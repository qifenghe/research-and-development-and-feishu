# H5 实验单与核价审核改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将手机端改造成纯 H5 的 A「清爽办公」界面，并围绕主料、配料、关键工序和成品数量完成实验记录、核价文件审核、财务接收闭环。

**Architecture:** 保留 Vue 3/Vant、共享 API 客户端和 Spring Boot/JPA 结构。后端通过 Flyway 扩展实验单、任务负责人和核价审核字段，状态机禁止未审核核价文件通知财务；前端以 design tokens 和公共组件完成视觉收敛，飞书适配器保留但由 `FEISHU_ENABLED=false` 关闭。

**Tech Stack:** Java 17、Spring Boot 3、Spring Data JPA、Flyway、Apache POI、Vue 3、Vant、Pinia、TypeScript、Playwright。

## Global Constraints

- 当前交付形态为浏览器打开的纯 H5，系统账号密码是唯一可见登录方式。
- 飞书代码保留但默认关闭，关闭时不得产生外部请求或阻断业务。
- 实验单不录包材；一个主料、多个配料，关键工序至少一道。
- 成品数量单位默认“袋”，可选盒、份、个、盘。
- 产品负责人允许审核自己负责产品的核价文件。
- 核价审核通过前不得通知财务。
- 实验单锁定后不可覆盖；配方错误生成下一样品版本，核价内容错误生成下一核价版本。
- 每项生产代码修改先写失败测试，再写最小实现。

---

### Task 1: 纯 H5 运行模式与稳定登录

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/lhr/rnd/service/FeishuProperties.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/FeishuIntegrationService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/AuthControllerTest.java`
- Modify: `frontend/apps/mobile/src/router/index.ts`
- Modify: `frontend/apps/mobile/src/views/LoginView.vue`
- Modify: `frontend/apps/mobile/src/views/ProfileView.vue`
- Modify: `frontend/scripts/check-api-contracts.mjs`
- Modify: `scripts/print-lan-urls.mjs`
- Test: `frontend/e2e/mobile-smoke.spec.ts`

**Interfaces:**
- Consumes: `POST /api/v1/auth/login`、现有 `FeishuProperties`。
- Produces: `feishu.enabled: boolean`、纯账号密码登录页、稳定 `.local` 地址提示。

- [ ] **Step 1: 写关闭飞书模式的失败测试**

在 `AuthControllerTest` 增加 `webLoginWorksWhenFeishuIsDisabled`，使用 `rnd_assistant/123456` 登录并断言成功；同时断言关闭状态下调用飞书 OAuth 返回 `FEISHU_DISABLED`，而不是发起外部请求。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=AuthControllerTest#webLoginWorksWhenFeishuIsDisabled test
```

Expected: 因 `feishu.enabled` 和 `FEISHU_DISABLED` 尚不存在而失败。

- [ ] **Step 3: 增加功能开关**

在 `application.yml` 增加：

```yaml
app:
  feishu:
    enabled: ${FEISHU_ENABLED:false}
```

`FeishuProperties` 增加 `boolean enabled`。`FeishuIntegrationService` 的 OAuth、派发和外部发送入口在关闭时快速失败或返回零派发结果，不创建外部连接。

- [ ] **Step 4: 精简登录和路由**

登录页只保留账号、密码、主按钮和默认折叠的测试辅助。移除飞书文案；路由不再自动读取 `?code=` 跳转飞书回调，回调路由保留为不可见兼容入口。

网络异常文案必须包含当前 `location.host`，并区分：

```text
AUTH_CREDENTIAL_INVALID -> 账号或密码不正确
TypeError/fetch failed -> 无法连接服务，请确认电脑服务已启动且手机与电脑在同一网络
401 -> 登录已过期
```

- [ ] **Step 5: 更新固定访问地址输出**

`print-lan-urls.mjs` 优先输出：

```text
http://<LocalHostName>.local:5174/m/login
```

并保留当前 IPv4 作为备用地址。

- [ ] **Step 6: 验证**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=AuthControllerTest test
cd ../frontend
pnpm typecheck
pnpm exec playwright test e2e/mobile-smoke.spec.ts --project=mobile-chromium --workers=1
```

Expected: H5 登录成功，页面无飞书入口，测试通过。

---

### Task 2: 实验单核价数据与产品负责人模型

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__extend_experiment_output_and_product_owner.sql`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SaveExperimentDraftRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/ExperimentForm.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/RndTask.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentFormEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/RndTaskEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Modify: `frontend/packages/shared/src/types/index.ts`
- Modify: `frontend/packages/shared/src/api/task.ts`

**Interfaces:**
- Produces: `finishedOutputQuantity: Integer`、`finishedOutputUnit: String`、`productOwnerName: String`。
- Consumes: Task 3 的计算和 Task 4 的审核权限。

- [ ] **Step 1: 写字段持久化失败测试**

扩展控制器测试：分配任务时传入 `productOwnerName="张研发"`；保存实验草稿时传入：

```json
{
  "finishedOutputWeightKg": 8.5,
  "finishedOutputQuantity": 17,
  "finishedOutputUnit": "袋"
}
```

重新读取任务详情，断言三个字段保持不变。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest#savesFinishedQuantityAndProductOwner test
```

Expected: 请求类型或数据库字段不存在导致失败。

- [ ] **Step 3: 增加 V11 迁移**

```sql
alter table rnd_task add column product_owner_name varchar(100);
alter table experiment_form add column finished_output_quantity integer;
alter table experiment_form add column finished_output_unit varchar(20) default '袋' not null;
update rnd_task set product_owner_name = assignee_name where product_owner_name is null;
```

- [ ] **Step 4: 扩展模型和 API**

分配接口增加可选 `productOwnerName`，缺省时使用 `assigneeName`。实验草稿增加：

```java
@Positive Integer finishedOutputQuantity,
@Pattern(regexp = "袋|盒|份|个|盘") String finishedOutputUnit
```

空单位规范化为“袋”。同步更新实体、映射和共享 TypeScript 类型。

- [ ] **Step 5: 验证迁移和持久化**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SchemaMigrationTest,SampleWorkflowControllerTest test
```

Expected: 迁移和控制器测试通过。

---

### Task 3: 核价优先的实验计算与表单

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/domain/ExperimentCalculationServiceTest.java`
- Modify: `frontend/packages/shared/src/experiment/calculations.ts`
- Modify: `frontend/tests/experiment-calculations.test.mts`
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Modify: `frontend/apps/mobile/src/components/ProcessStepEditor.vue`
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`

**Interfaces:**
- Produces: `averageUnitWeightKg`、`referenceQuantity`、核价预览模型。
- Consumes: Task 2 的成品字段。

- [ ] **Step 1: 写计算失败测试**

增加测试：主料 10kg、配料 2kg、成品 8.5kg、17 袋，断言：

```text
总投入 = 12kg
主料得率 = 85%
平均每袋重量 = 0.5kg
参考袋数 = 17
```

工序投入 10kg、余料 1kg、产出 8kg，断言损耗 1kg、损耗率 10%。

- [ ] **Step 2: 运行前后端计算测试确认失败**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ExperimentCalculationServiceTest test
cd ../frontend
node --test tests/experiment-calculations.test.mts
```

Expected: 成品数量和平均单袋重量函数不存在。

- [ ] **Step 3: 实现纯计算函数**

后端和共享前端都实现相同规则：

```text
得率 = 成品总重量 / 主料投入重量 * 100
平均单袋重量 = 成品总重量 / 成品数量
工序损耗 = 投入 - 余料 - 产出
工序损耗率 = 工序损耗 / 投入 * 100
```

除数小于等于 0 时返回 0；工序余料加产出大于投入时校验失败。

- [ ] **Step 4: 重排实验单**

移动端顺序固定为：基础信息、配方投入、关键工序、成品产出、核价数据预览。包材选项从实验单移除；主料必须且只能有一个；利用率默认 100%；成品单位默认袋。

核价预览展示：主料、配料、比例、总投入、成品重量、成品数量、平均单袋重量、得率。

- [ ] **Step 5: 增加提交校验**

保存草稿允许字段不完整；通知测试前必须满足：主料重量大于 0、至少一道有效工序、成品重量大于 0、成品数量大于 0。

- [ ] **Step 6: 验证**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ExperimentCalculationServiceTest test
cd ../frontend
node --test tests/experiment-calculations.test.mts
pnpm typecheck
```

Expected: 计算、类型检查通过。

---

### Task 4: 核价审核状态机、版本与权限

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__add_pricing_review.sql`
- Create: `backend/src/main/java/com/lhr/rnd/api/ReviewPricingFileRequest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/PricingFileStatus.java`
- Modify: `backend/src/main/java/com/lhr/rnd/model/PricingFileRecord.java`
- Modify: `backend/src/main/java/com/lhr/rnd/persistence/entity/PricingFileEntity.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SampleWorkflowController.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Modify: `backend/src/test/java/com/lhr/rnd/service/RolePermissionServiceTest.java`
- Modify: `frontend/packages/shared/src/types/index.ts`
- Modify: `frontend/packages/shared/src/api/shipment.ts`

**Interfaces:**
- Produces: `POST /pricing-files/{id}/review`。
- Statuses: `PENDING_PRICING_REVIEW | PRICING_APPROVED | PRICING_REJECTED | FINANCE_NOTIFIED | FINANCE_RECEIVED`。

- [ ] **Step 1: 写审核状态机失败测试**

覆盖：生成后为待审核；待审核不能通知财务；产品负责人可自审；非负责人研发无权审核；退回必须填写原因；审核通过后可通知财务；重复审核保持幂等。

请求：

```json
{
  "decision": "APPROVE",
  "reviewerName": "张研发",
  "comment": "配方与出成数据确认无误"
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest#pricingRequiresOwnerOrDirectorReview test
```

Expected: 审核接口不存在或生成状态仍为 `GENERATED`。

- [ ] **Step 3: 增加 V12 和状态**

迁移新增 `reviewed_by`、`reviewed_at`、`review_comment`、`rejection_reason`，并将历史 `GENERATED` 更新为 `PENDING_PRICING_REVIEW`。

- [ ] **Step 4: 实现审核规则**

研发总监始终可审核；其他研发角色仅在姓名等于任务 `productOwnerName` 时可审核。允许产品负责人自审。`REJECT` 要求非空原因；审核通过后才能调用 `notifyFinance`。

- [ ] **Step 5: 实现版本规则**

同一样品版本被退回后再次生成核价时，版本递增为 `核价V2/V3`，旧文件和审核记录只读保留。配方错误由现有复打样接口生成下一样品版本，不修改锁定实验单。

- [ ] **Step 6: 验证**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -Dtest=SampleWorkflowControllerTest,RolePermissionServiceTest,SchemaMigrationTest test
```

Expected: 审核、权限、迁移测试通过。

---

### Task 5: A「清爽办公」公共视觉与业务页面

**Files:**
- Modify: `frontend/apps/mobile/src/styles/tokens.css`
- Modify: `frontend/apps/mobile/src/styles/global.css`
- Modify: `frontend/apps/mobile/src/styles/components.css`
- Modify: `frontend/apps/mobile/src/styles/vant-overrides.css`
- Modify: `frontend/apps/mobile/src/components/PageHeader.vue`
- Modify: `frontend/apps/mobile/src/components/HeroCard.vue`
- Modify: `frontend/apps/mobile/src/components/TaskCard.vue`
- Modify: `frontend/apps/mobile/src/components/RoleEntryGrid.vue`
- Modify: `frontend/apps/mobile/src/components/FixedActionBar.vue`
- Modify: `frontend/apps/mobile/src/views/TodoView.vue`
- Modify: `frontend/apps/mobile/src/views/RequestNewView.vue`
- Modify: `frontend/apps/mobile/src/views/RequestReviewView.vue`
- Modify: `frontend/apps/mobile/src/views/TaskAssignView.vue`
- Modify: `frontend/apps/mobile/src/views/TestConfirmView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingListView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingDetailView.vue`
- Test: `frontend/e2e/mobile-flow.spec.ts`

**Interfaces:**
- Consumes: Tasks 2-4 的数据和审核 API。
- Produces: 375px 无溢出的统一 H5 界面。

- [ ] **Step 1: 写页面契约失败测试**

浏览器测试断言：登录首屏能看到账号、密码和登录；待办页有三个状态数字、一个优先任务；每个详情页最多一个 `type=primary` 固定主按钮；核价待审核详情出现审核动作，未审核不出现通知财务。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend
pnpm exec playwright test e2e/mobile-flow.spec.ts --project=mobile-chromium --workers=1
```

Expected: 旧工作台结构和核价审核按钮断言失败。

- [ ] **Step 3: 收敛 design tokens**

使用：主色 `#246BFE`、背景 `#F5F7FA`、正文 `#172033`、辅助文字 `#667085`、卡片与按钮圆角 `8px`、页面边距 `16px`。移除普通卡片重阴影和大圆角。

- [ ] **Step 4: 重做工作台与公共组件**

工作台顺序为用户/角色、三个状态数字、一个优先任务、紧凑待办、角色快捷入口。任务图标使用“审、样、测、价”，动效 150-200ms。

- [ ] **Step 5: 接入核价审核界面**

总监和产品负责人看到“待核价审核”；审核详情显示核价预览、通过、退回原因输入。通过后内勤才看到“通知财务”，财务只看到已通知文件。

- [ ] **Step 6: 375px 和常规手机验证**

在 Playwright Pixel 7 和 375×812 viewport 下检查无横向滚动、固定按钮不遮挡内容、表单错误保留输入。

---

### Task 6: 五角色闭环与全量回归

**Files:**
- Modify: `frontend/e2e/mobile-closed-loop.spec.ts`
- Modify: `scripts/run-mobile-closed-loop.mjs`
- Modify: `docs/mobile-uat-checklist.md`

**Interfaces:**
- Consumes: Tasks 1-5 的完整流程。
- Produces: 可重复执行的 H5 六阶段接力验收。

- [ ] **Step 1: 更新闭环测试**

串行执行：内勤录需求 → 总监审核并指定研发/产品负责人 → 研发填写主料、配料、关键工序和成品 17 袋 → 测试通过锁版 → 内勤生成核价 → 产品负责人审核 → 内勤通知财务 → 财务下载并接收。

- [ ] **Step 2: 验证审核阻断**

在负责人审核前，用内勤调用通知财务，断言返回业务错误；审核后同一操作成功。

- [ ] **Step 3: 运行一键闭环**

```bash
node scripts/run-mobile-closed-loop.mjs
```

Expected: 新建记录完成全流程，下载文件扩展名为 `.xlsx` 且非空。

- [ ] **Step 4: 全量回归**

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test
cd ../frontend
node --test tests/*.test.mts
pnpm typecheck
pnpm build
pnpm check:api-contracts
pnpm check:routes
pnpm exec playwright test --workers=1
```

Expected: 后端零失败；前端单元测试、类型检查、PC/手机构建、契约、路由和浏览器测试全部通过。

- [ ] **Step 5: 更新人工验收清单**

删除飞书作为当前验收项，增加固定 `.local` 入口、产品负责人自审、核价审核退回、实验单核价预览和正式移除测试账号的说明。
