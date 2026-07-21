# Role And Form Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement editable single-role governance, configurable CRUD permissions, five-form field administration, and enforced testing/pricing handoff rules.

**Architecture:** Add a role-definition persistence layer above existing role-permission rows. Keep `UserAccount.role` as a single role code. Replace hard-coded front-end role matrices with permission capabilities returned by the authenticated session while preserving backend business checks for product ownership and finance visibility.

**Tech Stack:** Java 17, Spring Boot 3, JPA/Flyway/H2/PostgreSQL-compatible SQL, Vue 3, Ant Design Vue, TypeScript, Vitest/node:test, Maven.

## Global Constraints

- One account has exactly one active role code.
- `SYSTEM_ADMIN` is protected; at least one enabled super administrator must remain.
- Finance receives only reviewed and notified pricing files.
- Product-owner pricing review uses assigned task ownership, not an additional role.
- Preserve existing test account data and existing API endpoints where possible.

---

### Task 1: Role Definition And Assignment Safety

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__create_role_definition.sql`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/RoleDefinitionEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/repository/RoleDefinitionRepository.java`
- Create: `backend/src/main/java/com/lhr/rnd/service/RoleDefinitionService.java`
- Create: `backend/src/main/java/com/lhr/rnd/api/RoleDefinitionController.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/UserSettingsService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SessionAuthenticationInterceptor.java`
- Test: `backend/src/test/java/com/lhr/rnd/api/RoleDefinitionControllerTest.java`

**Interfaces:**
- Produces `GET/POST/PUT /api/v1/settings/roles`, `POST /api/v1/settings/roles/{code}/enable`, and `POST /api/v1/settings/roles/{code}/disable`.
- `UserSettingsService.saveUser` validates that `role` exists and is active.

- [ ] Write failing tests for creating an enabled role, rejecting duplicate role codes, and rejecting disable when enabled users reference a role.
- [ ] Add `role_definition` table with `role_code`, `role_name`, `description`, `status`, `system_builtin`, timestamps and unique role code.
- [ ] Implement service validation and bootstrap the existing six roles plus `SYSTEM_ADMIN`.
- [ ] Add protected controller endpoints and audit records for role changes.
- [ ] Run `mvn -q -Dtest=RoleDefinitionControllerTest test` and expect zero failures.

### Task 2: Editable Capability Matrix

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/RolePermissionController.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/SaveRolePermissionsRequest.java`
- Modify: `frontend/packages/shared/src/types/index.ts`
- Modify: `frontend/packages/shared/src/api/settings.ts`
- Modify: `frontend/apps/pc/src/views/settings/RolePermissionsSettingsView.vue`
- Test: `backend/src/test/java/com/lhr/rnd/api/RolePermissionControllerTest.java`
- Test: `frontend/tests/role-permission-matrix.test.mts`

**Interfaces:**
- Produces capability rows with `moduleCode`, `actionCode`, `dataScope`, `enabled` and API method/path mapping.
- Existing `PUT /api/v1/settings/role-permissions/{roleCode}` replaces a role capability set atomically.

- [ ] Write tests proving a role with `TEST_RECORD:READ` can access testing records and a role without it receives HTTP 403.
- [ ] Convert the settings UI from free-text role-code lookup to role selection, grouped modules, CRUD/submit/review/export/download switches, and a data-scope selector.
- [ ] Keep API-rule persistence as enforcement source and map each capability to exact endpoint patterns.
- [ ] Add a create-role action that opens a role drawer, then redirects to its permission matrix.
- [ ] Run backend focused tests and `node --test tests/role-permission-matrix.test.mts`.

### Task 3: Five-Form Field Management

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/FormFieldSettingsService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/FormFieldSettingsController.java`
- Modify: `frontend/apps/pc/src/views/settings/FormFieldsSettingsView.vue`
- Modify: `frontend/packages/shared/src/types/index.ts`
- Test: `backend/src/test/java/com/lhr/rnd/api/FormFieldSettingsControllerTest.java`
- Test: `frontend/tests/form-field-settings.test.mts`

**Interfaces:**
- `GET /api/v1/settings/form-fields` always returns `SAMPLE_REQUEST`, `EXPERIMENT_FORM`, `TEST_RECORD`, `SHIPMENT_FEEDBACK`, `PRICING_FILE`, even before first manual save.
- `PUT /api/v1/settings/form-fields/{formCode}` accepts editable fields and validates protected system field codes.

- [ ] Write tests that empty configuration returns all five default forms and each form has nonzero fields.
- [ ] Add `initializeDefaultFormFields()` use in read path or application bootstrap so the configuration UI never displays an empty table.
- [ ] Implement editable drawer rows: field name, control type, required, enabled, default, placeholder, dictionary, sort order and remark; provide add/delete/reorder controls.
- [ ] Mark system-required fields as protected from deletion and display their protection reason.
- [ ] Run focused backend and frontend tests.

### Task 4: Test Visibility And Pricing Handoff Enforcement

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Modify: `frontend/apps/pc/src/views/rnd/PendingTestsView.vue`
- Modify: `frontend/apps/pc/src/views/shipment/PricingDetailView.vue`
- Modify: `frontend/apps/mobile/src/views/TestConfirmView.vue`
- Modify: `frontend/apps/mobile/src/views/PricingDetailView.vue`
- Test: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`
- Test: `frontend/tests/pricing-finance-visibility.test.mts`

**Interfaces:**
- RND_DIRECTOR receives test-list/detail/report read permissions.
- Pricing review endpoint permits RND_DIRECTOR or the assigned product owner only.
- Finance list/detail/download returns only `PRICING_APPROVED` files after finance notification.

- [ ] Write failing API tests for director viewing a test record, non-owner engineer review rejection, product owner review success, and finance invisibility before notification.
- [ ] Add director testing list/report routes and make test details readable without test decision buttons.
- [ ] Tighten pricing action display so review is shown only to director or current product owner; hide it from unrelated engineers.
- [ ] Verify finance list/download/archives use the same `isFinanceVisible` predicate.
- [ ] Run `mvn -q -Dtest=SampleWorkflowControllerTest test` and the pricing visibility frontend test.

### Task 5: Regression And Manual Acceptance

**Files:**
- Modify: `docs/mobile-uat-checklist.md`
- Create: `docs/role-permission-uat-checklist.md`
- Test: `frontend/e2e/mobile-closed-loop.spec.ts`

- [ ] Add a super-admin UAT sequence: create role, assign CRUD, assign it to an account, verify screen and API denial/allow.
- [ ] Add a director testing-read check and pricing owner/finance handoff checks to mobile closed-loop test.
- [ ] Run `mvn -q test`, `pnpm --dir frontend typecheck`, `pnpm --dir frontend build`, and `node scripts/run-mobile-closed-loop.mjs`.
- [ ] Manually verify PC settings pages and the five default forms with the `admin` account.
