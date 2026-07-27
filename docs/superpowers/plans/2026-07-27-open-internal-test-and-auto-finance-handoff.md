# 全员内部测试与自动财务移交 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让所有已启用角色均能完成内部测试，并让制样负责人或研发总监审核核价后自动将文件交给财务。

**Architecture:** 后端 API 权限表作为最终授权依据，默认角色权限补齐内部测试接口；工作流服务取消指定测试人限制，并在核价审核通过的同一事务中写入财务交接记录。PC 与手机端展示相同的权限入口和状态文案，去除人工通知财务。

**Tech Stack:** Java 17、Spring Boot 3、Spring Data JPA、Flyway、Vue 3、TypeScript、Ant Design Vue、Vant、Playwright。

## Global Constraints

- 所有内置角色均可提交内部测试结论，测试记录保存实际提交人。
- 自定义角色仍按角色权限配置决定能否测试。
- 制样负责人审核本人样品，研发总监审核全部样品。
- 审核通过自动生成财务交接，财务只读取已交接文件。
- 保留 `notify-finance` API 兼容旧客户端，但不得重复创建交接记录。

---

### Task 1: 开放内部测试权限并留存实际提交人

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Create: `backend/src/main/resources/db/migration/V19__grant_all_roles_internal_test.sql`
- Modify: `backend/src/test/java/com/lhr/rnd/service/RolePermissionServiceTest.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/v1/test-assignments/{id}/pass`、`POST /api/v1/test-assignments/{id}/fail-resample`。
- Produces: 任意已授权角色可提交，`TestRecord.testerName` 记录当前提交人。

- [ ] **Step 1: 写失败测试**

在 `RolePermissionServiceTest` 断言研发内勤、研发总监、研发人员、测试、财务、管理层均拥有两个测试提交接口权限；在 `SampleWorkflowControllerTest` 使用非预设测试人的会话提交测试，并断言响应中的测试人是会话用户。

```java
assertThat(service.hasPermission("FINANCE", "POST", "/api/v1/test-assignments/TEST-1/pass")).isTrue();
andExpect(jsonPath("$.data.testerName").value("赵内勤"));
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -q -Dtest=RolePermissionServiceTest,SampleWorkflowControllerTest test`

Expected: 因财务无测试权限或 `TEST_ASSIGNMENT_TESTER_MISMATCH` 失败。

- [ ] **Step 3: 实现最小改动**

为每个内置角色加入以下规则，`SYSTEM_ADMIN` 继续由管理员绕过授权：

```java
rule("POST", "/api/v1/test-assignments/*/pass", "提交内部测试通过", order),
rule("POST", "/api/v1/test-assignments/*/fail-resample", "提交内部测试复打样", order + 1),
rule("GET", "/api/v1/rnd-tasks/*/test-records", "查看内部测试记录", order + 2),
```

修改 `pendingTestAssignment`，仅验证任务存在且状态为 `PENDING_TEST`，删除提交人与预设测试人必须相同的检查。创建 V19 幂等迁移，为现有内置角色补齐权限。

- [ ] **Step 4: 运行通过测试**

Run: `mvn -q -Dtest=RolePermissionServiceTest,SampleWorkflowControllerTest test`

Expected: PASS，测试记录显示真实提交人。

- [ ] **Step 5: 提交任务**

Run: `git add backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java backend/src/main/resources/db/migration/V19__grant_all_roles_internal_test.sql backend/src/test/java/com/lhr/rnd/service/RolePermissionServiceTest.java backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java && git commit -m "feat: allow all roles to complete internal tests"`

### Task 2: 审核核价时自动交接财务

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/resources/db/migration/V19__grant_all_roles_internal_test.sql`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/v1/pricing-files/{id}/review` with `APPROVE` or `REJECT`.
- Produces: `APPROVE` 返回 `FINANCE_NOTIFIED` 并创建 `FinanceNotification`；`REJECT` 保持 `PRICING_REJECTED`。

- [ ] **Step 1: 写失败测试**

创建待审核核价文件，负责人会话提交 `APPROVE`，断言响应为 `FINANCE_NOTIFIED`，并用财务会话查询得到该文件；`REJECT` 后财务列表为空；非负责研发审核返回 `PRICING_FILE_REVIEW_FORBIDDEN`。

```java
andExpect(jsonPath("$.data.status").value("FINANCE_NOTIFIED"));
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -q -Dtest=SampleWorkflowControllerTest test`

Expected: 当前审核通过仍为 `PRICING_APPROVED`，财务列表没有文件。

- [ ] **Step 3: 实现最小改动**

在 `reviewPricingFile(...)` 的通过分支中，创建状态为 `FINANCE_NOTIFIED` 的核价记录和交接记录并持久化；驳回分支不创建交接。删除内勤的默认 `notify-finance` 权限；兼容接口发现已有交接时直接返回现有交接。

```java
var financeReady = reviewed.withStatus(PricingFileStatus.FINANCE_NOTIFIED);
var notification = new FinanceNotification(nextFinanceId(), financeReady.id(), "财务", "核价审核通过后自动移交", FinanceNotificationStatus.SENT, reviewedAt);
```

- [ ] **Step 4: 运行通过测试**

Run: `mvn -q -Dtest=SampleWorkflowControllerTest test`

Expected: PASS，审核通过财务立即可见，驳回不可见。

- [ ] **Step 5: 提交任务**

Run: `git add backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java backend/src/main/resources/db/migration/V19__grant_all_roles_internal_test.sql backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java && git commit -m "feat: hand pricing files to finance after approval"`

### Task 3: 统一 PC/手机入口并去除人工通知

**Files:**
- Modify: `frontend/packages/shared/src/permissions/index.ts`
- Modify: `frontend/apps/mobile/src/views/TestConfirmView.vue`
- Modify: `frontend/apps/pc/src/views/rnd/PendingTestsView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingDetailView.vue`
- Modify: `frontend/apps/pc/src/views/shipment/PricingDetailView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingListView.vue`
- Modify: `frontend/apps/pc/src/views/shipment/ShipmentPricingModuleView.vue`
- Modify: `frontend/e2e/mobile-pricing-security-version.spec.ts`

**Interfaces:**
- Consumes: `canAccessRoute` 与测试、核价 API。
- Produces: 已登录任意内置角色可进入测试页；审核通过提示“已自动移交财务”；页面不含“通知财务”。

- [ ] **Step 1: 写失败的移动端测试**

使用 `tester` 登录后访问 `/m/tests/{id}`，断言 URL 不匹配 `/m/login`；核价审核通过后财务可见，页面不含“通知财务”。

```ts
await page.goto(`/m/tests/${testAssignmentId}`);
await expect(page).not.toHaveURL(/\/m\/login/);
await expect(page.getByText("通知财务")).toHaveCount(0);
```

- [ ] **Step 2: 运行失败测试**

Run: `pnpm --dir frontend exec playwright test e2e/mobile-pricing-security-version.spec.ts --project=mobile-chromium --workers=1`

Expected: 测试用户被路由规则拒绝，或“通知财务”仍存在。

- [ ] **Step 3: 实现最小改动**

扩展 PC/手机 `pending-tests`、`tasks/:id/test`、`tests/:id` 和 `PERFORM_INTERNAL_TEST` 到所有内置角色。`TestConfirmView.vue` 始终传 `auth.displayName`。删除 PC/手机核价详情的 `canNotifyFinance`、输入、按钮和调用；将列表“待通知财务”改为“已移交财务”，审核成功文案改为“审核通过，已自动移交财务”。

- [ ] **Step 4: 运行前端验证**

Run: `pnpm --dir frontend typecheck && pnpm --dir frontend exec playwright test e2e/mobile-pricing-security-version.spec.ts --project=mobile-chromium --workers=1`

Expected: PASS。

- [ ] **Step 5: 提交任务**

Run: `git add frontend/packages/shared/src/permissions/index.ts frontend/apps/mobile/src/views/TestConfirmView.vue frontend/apps/pc/src/views/rnd/PendingTestsView.vue frontend/apps/mobile/src/views/PricingDetailView.vue frontend/apps/pc/src/views/shipment/PricingDetailView.vue frontend/apps/mobile/src/views/PricingListView.vue frontend/apps/pc/src/views/shipment/ShipmentPricingModuleView.vue frontend/e2e/mobile-pricing-security-version.spec.ts && git commit -m "feat: open internal testing and simplify finance handoff"`

### Task 4: 全流程回归与交付

**Files:**
- Modify: `docs/superpowers/specs/2026-07-27-open-internal-test-and-auto-finance-handoff-design.md` only if behavior wording needs clarification.

- [ ] **Step 1: 后端全量回归**

Run: `mvn -q test`

Expected: PASS。

- [ ] **Step 2: 前端全量验证**

Run: `pnpm --dir frontend typecheck && pnpm --dir frontend run check:api-contracts && pnpm --dir frontend run check:routes`

Expected: PASS。

- [ ] **Step 3: 启动账号冒烟验证**

使用 `tester / 123456` 和 `finance / 123456` 验证测试页可进入，财务仅看见审核后自动交接的核价文件。

- [ ] **Step 4: 检查并推送**

Run: `git diff --check && git status --short`

只提交本计划文件，排除 `.superpowers/sdd/task-3-review.md`、`frontend/test-results/`、`outputs/` 和未关联脚本。之后执行 `git push origin codex/rnd-sample-foundation`。
