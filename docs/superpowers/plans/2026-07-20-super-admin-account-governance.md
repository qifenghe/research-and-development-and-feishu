# 超级管理员与账号治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将系统从飞书身份依赖中明确切换为网页账号治理，由超级管理员安全地创建账号、分配单一主角色并维护角色权限。

**Architecture:** 沿用现有 `SYSTEM_ADMIN` 角色编码，将其页面名称统一为“超级管理员”。后端以会话令牌中的角色和 `RolePermissionService` 作为最终授权依据，用户管理服务负责账号生命周期与最后一个超级管理员保护；PC 端仅展示超级管理员可访问的账号与权限入口。

**Tech Stack:** Java 17、Spring Boot 3、Spring MVC Test、H2/Flyway、Vue 3、TypeScript、Ant Design Vue、Vitest。

## Global Constraints

- 每个账号只允许一个主角色。
- `SYSTEM_ADMIN` 是超级管理员的持久化角色编码，前端文案统一显示为“超级管理员”。
- 研发总监通过已有权限集合拥有研发、需求审核、任务分配和核价审核能力。
- 飞书用户 ID 保持可空，仅供后续身份绑定；网页登录不依赖飞书。
- 仅超级管理员可访问用户与角色权限管理接口和页面。
- 禁止停用或降级最后一个启用中的超级管理员。
- 首版不提供账号删除。
- 所有改动遵循现有 `ApiResponse`、`SessionAuthenticationInterceptor`、`UserSettingsService` 模式。

---

## File Structure

- `backend/src/main/java/com/lhr/rnd/service/UserSettingsService.java`：账号角色变更约束、最后一个超级管理员保护、审计写入入口。
- `backend/src/main/java/com/lhr/rnd/api/UserSettingsController.java`：用户管理 HTTP 接口。
- `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`：限制配置接口与用户治理接口只允许 `SYSTEM_ADMIN`。
- `backend/src/main/java/com/lhr/rnd/api/SessionAuthenticationInterceptor.java`：复用会话主体并阻止普通角色访问账号治理路径。
- `backend/src/main/java/com/lhr/rnd/config/DefaultWebAccountBootstrap.java`：确保初始超级管理员存在。
- `backend/src/test/java/com/lhr/rnd/api/UserSettingsControllerTest.java`：账号生命周期及最后管理员保护测试。
- `backend/src/test/java/com/lhr/rnd/api/SessionAuthenticationInterceptorTest.java`：普通角色越权访问测试。
- `frontend/packages/shared/src/permissions/index.ts`：超级管理员页面文案与路由权限。
- `frontend/apps/pc/src/layouts/AdminLayout.vue`：仅为超级管理员展示系统设置菜单。
- `frontend/apps/pc/src/views/settings/UsersSettingsView.vue`：账号与权限页面、角色可选项及危险操作提示。
- `frontend/tests/super-admin-permissions.test.mts`：PC 端路由和角色文案测试。

### Task 1: 固化超级管理员角色与账号生命周期保护

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/UserSettingsService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/config/DefaultWebAccountBootstrap.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/UserSettingsControllerTest.java`

**Interfaces:**
- Consumes: `UserAccountRepository.findByRole(String role)` and `UserAccountEntity.updateAccount(...)`.
- Produces: `UserSettingsService.saveUser(...)`, `enableUser(...)`, `disableUser(...)` reject an operation that leaves no active `SYSTEM_ADMIN` account.

- [ ] **Step 1: Add failing lifecycle tests**

Add a test that creates two administrators, disables one successfully, then verifies disabling the final active administrator returns `400` and code `LAST_SYSTEM_ADMIN_REQUIRED`:

```java
mockMvc.perform(post("/api/v1/settings/users/{id}/disable", finalAdminId)
        .header("Authorization", "Bearer " + superAdminToken))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("LAST_SYSTEM_ADMIN_REQUIRED"));
```

Add a test that editing the final administrator role to `RND_DIRECTOR` also returns the same error.

- [ ] **Step 2: Run the focused test to verify failure**

Run:

```bash
mvn -q -Dtest=UserSettingsControllerTest test
```

Expected: FAIL because the final administrator can currently be disabled or reassigned.

- [ ] **Step 3: Add repository and service guard**

Add to `UserAccountRepository`:

```java
long countByRoleAndStatus(String role, String status);
```

In `UserSettingsService`, introduce constants:

```java
private static final String SUPER_ADMIN_ROLE = "SYSTEM_ADMIN";
private static final String ACTIVE_STATUS = "ACTIVE";
```

Before changing status or role, call a focused `assertNotRemovingLastSuperAdmin(existing, nextRole, nextStatus)` that throws:

```java
throw new BusinessException("LAST_SYSTEM_ADMIN_REQUIRED", "系统至少需要保留一个启用中的超级管理员");
```

Ensure `DefaultWebAccountBootstrap` creates or repairs the `admin` account with role `SYSTEM_ADMIN`, status `ACTIVE`, and a password hash only when no such account exists.

- [ ] **Step 4: Run the focused test to verify pass**

Run:

```bash
mvn -q -Dtest=UserSettingsControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the backend lifecycle protection**

```bash
git add backend/src/main/java/com/lhr/rnd/service/UserSettingsService.java backend/src/main/java/com/lhr/rnd/config/DefaultWebAccountBootstrap.java backend/src/main/java/com/lhr/rnd/persistence/repository/UserAccountRepository.java backend/src/test/java/com/lhr/rnd/api/UserSettingsControllerTest.java
git commit -m "feat: protect super admin account lifecycle"
```

### Task 2: 强制用户和角色配置接口只允许超级管理员

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SessionAuthenticationInterceptor.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/SessionAuthenticationInterceptorTest.java`

**Interfaces:**
- Consumes: `SessionTokenService.resolve(...)` and `SessionPrincipal.role()` in the interceptor.
- Produces: only the `SYSTEM_ADMIN` role may call `/api/v1/settings/**`; director retains the narrower `GET /api/v1/settings/users` endpoint only if task assignment still requires it, through a separate assignee lookup endpoint.

- [ ] **Step 1: Add failing authorization tests**

Add a director-token test that requests the following paths and expects `403` with `SESSION_ROLE_FORBIDDEN`:

```java
get("/api/v1/settings/users")
put("/api/v1/settings/users/USR-any")
get("/api/v1/settings/role-permissions/RND_ENGINEER")
```

Add a `SYSTEM_ADMIN` token test that requests `GET /api/v1/settings/users` and expects `200`.

- [ ] **Step 2: Run the authorization test to verify failure**

Run:

```bash
mvn -q -Dtest=SessionAuthenticationInterceptorTest test
```

Expected: FAIL because a director is currently permitted to load the full user-settings endpoint.

- [ ] **Step 3: Implement path-specific authorization**

In `RolePermissionService.hasPermission`, add a first-class predicate:

```java
private boolean isSettingsManagement(String uri) {
    return uri != null && uri.startsWith("/api/v1/settings/");
}
```

Resolve it before database permission lookup:

```java
if (isSettingsManagement(uri)) {
    return hasAnyRole(role, "SYSTEM_ADMIN");
}
```

If the task-assignment screen needs a people list, add a dedicated `GET /api/v1/rnd-assignees` endpoint returning only active `RND_ENGINEER` and `RND_DIRECTOR` users. Give this endpoint to `RND_DIRECTOR`, rather than exposing account administration.

- [ ] **Step 4: Run authorization tests to verify pass**

Run:

```bash
mvn -q -Dtest=SessionAuthenticationInterceptorTest,UserSettingsControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit API authorization enforcement**

```bash
git add backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java backend/src/main/java/com/lhr/rnd/api/SessionAuthenticationInterceptor.java backend/src/main/java/com/lhr/rnd/api backend/src/test/java/com/lhr/rnd/api/SessionAuthenticationInterceptorTest.java
git commit -m "fix: restrict account settings to super admins"
```

### Task 3: 完成 PC 端超级管理员账号管理体验

**Files:**
- Modify: `frontend/packages/shared/src/permissions/index.ts`
- Modify: `frontend/apps/pc/src/layouts/AdminLayout.vue`
- Modify: `frontend/apps/pc/src/views/settings/UsersSettingsView.vue`
- Modify: `frontend/apps/pc/src/views/settings/RolePermissionsSettingsView.vue`
- Test: `frontend/tests/super-admin-permissions.test.mts`

**Interfaces:**
- Consumes: `canAccessRoute(role, path, "pc")`, `canPerformAction(role, "MANAGE_SETTINGS")`, and `api.settings` account endpoints.
- Produces: a PC-only “账号与权限” menu for `SYSTEM_ADMIN`; other roles receive no system-settings entry and are redirected to `/403` for a direct settings URL.

- [ ] **Step 1: Write failing frontend permission tests**

Create `frontend/tests/super-admin-permissions.test.mts`:

```ts
import { canAccessRoute, roleLabel } from "@rnd/shared";

it("allows only a system admin to access settings", () => {
  expect(canAccessRoute("SYSTEM_ADMIN", "/admin/settings/users", "pc")).toBe(true);
  expect(canAccessRoute("RND_DIRECTOR", "/admin/settings/users", "pc")).toBe(false);
});

it("labels SYSTEM_ADMIN as super administrator", () => {
  expect(roleLabel("SYSTEM_ADMIN")).toBe("超级管理员");
});
```

- [ ] **Step 2: Run the test to verify failure**

Run:

```bash
pnpm --dir frontend exec vitest run tests/super-admin-permissions.test.mts
```

Expected: FAIL because the current display label is “系统管理员”.

- [ ] **Step 3: Implement menu and account-management changes**

Update `roleLabel` to return “超级管理员” for `SYSTEM_ADMIN`. In `AdminLayout.vue`, use `canPerformAction(auth.role, "MANAGE_SETTINGS")` to render the complete settings group only for `SYSTEM_ADMIN`.

In `UsersSettingsView.vue`:

```ts
const roles = [
  { value: "RND_ASSISTANT", label: "研发内勤" },
  { value: "RND_DIRECTOR", label: "研发总监" },
  { value: "RND_ENGINEER", label: "研发人员" },
  { value: "TESTER", label: "测试人员" },
  { value: "FINANCE", label: "财务" },
  { value: "SYSTEM_ADMIN", label: "超级管理员" },
];
```

Rename the page to “账号与权限”, remove the prominent Feishu-ID column, and put the optional future binding value into the edit modal’s collapsed “后期飞书绑定” section. Disable the role selector and disable button for the last active super administrator using server-provided error handling as the authoritative fallback.

- [ ] **Step 4: Run frontend checks to verify pass**

Run:

```bash
pnpm --dir frontend exec vitest run tests/super-admin-permissions.test.mts
pnpm --dir frontend typecheck
pnpm --dir frontend --filter @rnd/pc build
```

Expected: all commands exit with code `0`.

- [ ] **Step 5: Commit the PC management experience**

```bash
git add frontend/packages/shared/src/permissions/index.ts frontend/apps/pc/src/layouts/AdminLayout.vue frontend/apps/pc/src/views/settings/UsersSettingsView.vue frontend/apps/pc/src/views/settings/RolePermissionsSettingsView.vue frontend/tests/super-admin-permissions.test.mts
git commit -m "feat: add super admin account management"
```

### Task 4: 端到端回归与账号治理验收

**Files:**
- Modify: `frontend/e2e/pc-smoke.spec.ts`
- Modify: `docs/mobile-uat-checklist.md`
- Test: `backend/src/test/java/com/lhr/rnd/api/AuthControllerTest.java`

**Interfaces:**
- Consumes: default admin account `admin`, web login endpoint `/api/v1/auth/login`, and protected user-settings endpoints.
- Produces: reproducible regression coverage for administrator login, account creation, role assignment, and ordinary-role rejection.

- [ ] **Step 1: Add a failing login and authority regression test**

Add backend assertions that a disabled user cannot log in and that a `SYSTEM_ADMIN` account can log in:

```java
mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.user.role").value("SYSTEM_ADMIN"));
```

- [ ] **Step 2: Run the focused backend tests**

Run:

```bash
mvn -q -Dtest=AuthControllerTest,UserSettingsControllerTest,SessionAuthenticationInterceptorTest test
```

Expected: PASS after Tasks 1 and 2; use any failure to correct the implementation before proceeding.

- [ ] **Step 3: Add a PC browser smoke case**

In `frontend/e2e/pc-smoke.spec.ts`, log in as `admin / 123456`, visit `/admin/settings/users`, create a test research assistant account, confirm it appears with role “研发内勤”, then log out and confirm that account can log in. Use a timestamp in the username to avoid duplicate test data.

- [ ] **Step 4: Run the complete verification set**

Run:

```bash
mvn -q test
pnpm --dir frontend typecheck
pnpm --dir frontend --filter @rnd/pc build
pnpm --dir frontend exec playwright test e2e/pc-smoke.spec.ts --project=chromium --workers=1
```

Expected: all commands exit with code `0`.

- [ ] **Step 5: Commit verification coverage and handoff notes**

```bash
git add backend/src/test/java/com/lhr/rnd/api/AuthControllerTest.java frontend/e2e/pc-smoke.spec.ts docs/mobile-uat-checklist.md
git commit -m "test: cover super admin account governance"
```

## Plan Self-Review

- Spec coverage: Tasks 1-2 implement account lifecycle, role isolation and final-administrator protection. Task 3 implements the PC presentation and role wording. Task 4 covers web login and regression verification.
- No placeholders: the plan contains no `TBD`, no deferred implementation instructions, and every task includes target files, commands, expected results and a commit boundary.
- Type consistency: `SYSTEM_ADMIN` is the sole super-administrator role code in all tasks; front-end route checks continue to use `canAccessRoute` and `canPerformAction`.
