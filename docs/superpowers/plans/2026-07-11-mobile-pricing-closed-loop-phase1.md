# 手机端研发核价闭环第一阶段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让研发内勤、研发总监、研发人员、测试人员和财务能够在手机端接力完成“需求到核价文件接收”的最小闭环，并用自动化测试固定该流程。

**Architecture:** 保留现有 Vue 3/Vant、共享 API 客户端和 Spring Boot/JPA 结构。第一阶段增加财务接收状态与接口，修正手机待办的角色状态查询，解除生成核价对寄样反馈的隐含依赖，并补充跨角色 API 与浏览器测试；数据库单一数据源与权限统一在后续阶段实施。

**Tech Stack:** Java 17、Spring Boot 3、Spring Data JPA、Flyway、Apache POI、Vue 3、Vant、Pinia、TypeScript、Playwright。

## Global Constraints

- 主流程终点为财务确认接收核价文件，不包含采购询价、完整成本测算、生产工艺和生产 BOM。
- 寄样与客户反馈是可选支线，不得阻塞已锁定实验版本生成核价文件。
- 财务只能看到 `FINANCE_NOTIFIED` 和 `FINANCE_RECEIVED` 文件，不能看到尚未通知的 `GENERATED` 文件。
- 核价文件必须关联已锁定实验版本，历史文件不得覆盖。
- 手机端每个待办只提供一个明确主动作，接口失败必须显示错误和重试入口。
- 所有生产代码改动遵循测试先行；每个任务先观察对应测试失败，再写最小实现。

---

### Task 1: 固定跨角色闭环的后端契约

**Files:**
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes: 现有需求、审核、分配、接受、实验草稿、提交测试、测试通过、生成核价、通知财务接口。
- Produces: 一个不经过寄样即可生成核价的集成测试，以及财务确认接收接口的失败契约。

- [ ] **Step 1: 写不经过寄样的闭环失败测试**

增加测试 `lockedExperimentCanGeneratePricingWithoutShipmentAndReachFinanceInbox`：创建并锁定 A0 实验版本后，不创建 `shipment_record`，直接调用生成核价和通知财务，断言生成成功、状态为 `FINANCE_NOTIFIED`，并用 `status=FINANCE_NOTIFIED` 查询到该文件。

- [ ] **Step 2: 写财务确认接收的失败测试**

增加测试 `financeCanAcknowledgeNotifiedPricingFileIdempotently`，调用：

```http
POST /api/v1/pricing-files/{id}/receive
Content-Type: application/json

{"receivedBy":"钱财务"}
```

首次和重复调用均返回成功，文件状态为 `FINANCE_RECEIVED`，数据库最终只有一个财务接收结果。

- [ ] **Step 3: 运行测试并确认按预期失败**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest#lockedExperimentCanGeneratePricingWithoutShipmentAndReachFinanceInbox+financeCanAcknowledgeNotifiedPricingFileIdempotently test
```

Expected: 第二个测试因 `/receive` 接口不存在而失败；若第一个测试失败，记录实际阻断位置作为 Task 3 的修复依据。

- [ ] **Step 4: 保留失败测试进入下一任务**

不修改生产代码，确认测试名称、失败原因和目标行为一致。

---

### Task 2: 增加财务确认接收状态与接口

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__add_finance_received_status.sql`
- Create: `backend/src/main/java/com/lhr/rnd/api/ReceivePricingFileRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/PricingFileStatus.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingFileEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes: `PricingFileStatus.FINANCE_NOTIFIED`、`PricingFileRecord.withStatus`、会话角色 `FINANCE`。
- Produces: `PricingFileStatus.FINANCE_RECEIVED` 与 `POST /api/v1/pricing-files/{id}/receive`。

- [ ] **Step 1: 扩展状态与实体行为**

在 `PricingFileStatus` 增加 `FINANCE_RECEIVED`。在 `PricingFileEntity` 增加：

```java
public void markFinanceReceived() {
    this.status = "FINANCE_RECEIVED";
}
```

V10 迁移为 `pricing_file` 增加 `received_by varchar(100)` 和 `received_at timestamp`，实体同步增加字段及幂等接收方法。

- [ ] **Step 2: 增加请求类型和控制器接口**

请求类型：

```java
public record ReceivePricingFileRequest(
        @NotBlank(message = "接收人不能为空") String receivedBy
) {}
```

控制器调用 `workflowService.receivePricingFile(id, request.receivedBy())` 并返回更新后的 `PricingFileRecord`。

- [ ] **Step 3: 实现幂等业务规则**

`receivePricingFile` 规则：

- `GENERATED` 返回 `PRICING_FILE_NOT_NOTIFIED`。
- `FINANCE_NOTIFIED` 更新为 `FINANCE_RECEIVED`，保存接收人和接收时间，写审计日志。
- `FINANCE_RECEIVED` 直接返回当前记录，不重复写接收数据。

- [ ] **Step 4: 限定接口权限**

`POST /api/v1/pricing-files/{id}/receive` 仅允许 `FINANCE` 与管理员角色调用。

- [ ] **Step 5: 运行定向测试**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest,SchemaMigrationTest,RolePermissionServiceTest test
```

Expected: 全部通过，财务确认接收测试由红转绿。

---

### Task 3: 修正手机端财务收件箱与核价主线

**Files:**
- Modify: `frontend/packages/shared/src/types/index.ts`
- Modify: `frontend/packages/shared/src/api/shipment.ts`
- Modify: `frontend/packages/shared/src/api/index.ts`
- Modify: `frontend/apps/mobile/src/views/TodoView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingListView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingDetailView.vue`
- Create: `frontend/tests/mobile-finance-inbox.test.mts`
- Test: `frontend/e2e/mobile-flow.spec.ts`

**Interfaces:**
- Consumes: `GET /pricing-files?status=FINANCE_NOTIFIED`、`GET /pricing-files?status=FINANCE_RECEIVED`、`POST /pricing-files/{id}/receive`。
- Produces: 财务“待接收/已接收/全部”列表、下载和确认接收动作。

- [ ] **Step 1: 写共享待办失败测试**

测试 `fetchMobileTodoBoard` 在 `FINANCE` 角色下必须以 `FINANCE_NOTIFIED` 查询核价文件，不请求 `GENERATED`；待办卡标题为“待接收核价文件”，动作是“查看/下载”。

- [ ] **Step 2: 运行共享测试确认失败**

Run:

```bash
cd frontend
node --test tests/mobile-finance-inbox.test.mts
```

Expected: 当前实现请求 `GENERATED`，测试失败。

- [ ] **Step 3: 修改共享类型和 API**

给 `PricingFileStatus` 联合类型增加 `FINANCE_RECEIVED`，给 shipment API 增加：

```ts
receivePricingFile: (id: string, receivedBy: string) =>
  client.post<PricingFileRecord>(`/pricing-files/${id}/receive`, { receivedBy })
```

`fetchMobileTodoBoard` 对财务查询 `FINANCE_NOTIFIED`，对内勤继续查询 `GENERATED`。

- [ ] **Step 4: 改造财务列表和详情**

财务进入列表时默认筛选 `FINANCE_NOTIFIED`；筛选项为待接收、已接收、全部。详情页保留下载，并在 `FINANCE_NOTIFIED` 时显示“确认接收”，成功后刷新详情并返回待办。

- [ ] **Step 5: 补充移动端浏览器测试**

在 `mobile-flow.spec.ts` 增加财务登录后看到“核价文件”入口、进入列表、打开详情并出现下载按钮的测试；有通知数据时验证确认接收按钮。

- [ ] **Step 6: 运行测试和类型检查**

Run:

```bash
cd frontend
node --test tests/mobile-finance-inbox.test.mts
pnpm typecheck
```

Expected: 测试通过，三个 workspace 类型检查通过。

---

### Task 4: 让测试通过后直接进入待生成核价

**Files:**
- Modify: `frontend/packages/shared/src/api/index.ts`
- Modify: `frontend/apps/mobile/src/views/TodoView.vue`
- Modify: `frontend/apps/mobile/src/views/TaskCustomerFeedbackView.vue`
- Modify: `frontend/apps/pc/src/views/shipment/PricingListView.vue`
- Modify: `frontend/e2e/mobile-flow.spec.ts`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes: 测试通过后的锁定实验版本、`POST /sample-versions/{id}/pricing-files`。
- Produces: 研发内勤无需寄样即可看到“待生成核价”并生成文件。

- [ ] **Step 1: 写移动端待核价来源失败测试**

为共享待办构建逻辑增加测试：当任务状态为 `COMPLETED` 且版本存在锁定实验单、没有核价文件时，研发内勤待办应出现对应版本的“生成核价”动作，而不是依赖 `FEEDBACK_PASSED` 寄样记录。

- [ ] **Step 2: 为后端提供可核价版本查询契约**

增加只读接口：

```http
GET /api/v1/sample-versions/pricing-ready
```

返回已锁定实验版本且尚无 `GENERATED`、`FINANCE_NOTIFIED` 或 `FINANCE_RECEIVED` 核价文件的版本摘要。先在控制器测试中写失败用例。

- [ ] **Step 3: 实现最小后端查询**

查询必须以实验单 `LOCKED` 为准，不依赖寄样状态；同一版本已有任一核价文件时不重复列入待生成列表。

- [ ] **Step 4: 更新共享 API 与内勤待办**

`fetchMobileTodoBoard` 增加 `listPricingReadyVersions` 依赖，研发内勤用该结果生成“待生成核价”卡片。寄样反馈组仍独立存在。

- [ ] **Step 5: 更新 PC 核价列表入口**

PC 核价页面增加“待生成核价”区域，数据同样来自 `pricing-ready` 接口。

- [ ] **Step 6: 运行定向和全量检查**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest test
cd ../frontend
pnpm typecheck
```

Expected: 后端定向测试和前端类型检查通过。

---

### Task 5: 建立手机端五角色接力验收

**Files:**
- Modify: `frontend/e2e/mobile-flow.spec.ts`
- Modify: `frontend/playwright.config.ts`
- Create: `scripts/run-mobile-closed-loop.mjs`
- Modify: `docs/mobile-uat-checklist.md`

**Interfaces:**
- Consumes: 五个网页账号和 Task 1-4 的完整接口链。
- Produces: 一条可重复执行的手机端闭环测试与人工验收清单。

- [ ] **Step 1: 编写串行 Playwright 场景**

单测试按以下顺序切换账号并复用同一个新建样品编号：

```text
rnd_assistant 创建需求
rnd_director 审核并分配
rnd_engineer 接受、填写配方/工序/成品出成并提交测试
tester 测试通过
rnd_assistant 生成核价并通知 finance
finance 查看、下载并确认接收
```

每一步都断言页面状态和后端产生的数据，不使用预置演示记录。

- [ ] **Step 2: 增加运行脚本**

脚本依次检查 8080、5174 服务是否可用，运行 `mobile-chromium` 项目的闭环测试，并将失败步骤输出到终端。

- [ ] **Step 3: 执行完整移动端测试**

Run:

```bash
node scripts/run-mobile-closed-loop.mjs
```

Expected: 五角色主流程通过，下载响应为 `.xlsx` 且文件大小大于 0。

- [ ] **Step 4: 更新人工验收清单**

清单记录测试账号、每个角色的入口、预期状态和异常重试方法，供研发试用现场逐项勾选。

- [ ] **Step 5: 全量回归**

Run:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test
cd ../frontend
pnpm typecheck
pnpm build
node scripts/check-api-contracts.mjs
node scripts/check-routes.mjs
```

Expected: 后端零失败，PC/手机构建和类型检查通过，API 与路由检查无缺失。
