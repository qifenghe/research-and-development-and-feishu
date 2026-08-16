# R&D BOM Process Hierarchy Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backward-compatible major-process/minor-step/material/yield workflow to the existing experiment form, with PC editing and mobile read-only display.

**Architecture:** Persist the process plan as a normalized aggregate rooted at the experiment form. A dedicated transactional service owns nested save/load, calculations, legacy fallback, and legacy summary generation; Vue clients consume shared types through a new API and keep the old `processSteps` payload synchronized for existing reports.

**Tech Stack:** Java 17, Spring Boot 3.3, JPA, Flyway, PostgreSQL/H2, Vue 3, TypeScript, Ant Design Vue, Vant, Vitest-style Node contract tests, Playwright.

## Global Constraints

- Existing `experiment_process` records, endpoints, exports, and historical forms must remain readable.
- PC is the only editor in phase 1; mobile is read-only.
- Backend calculations are authoritative; frontend calculations are previews.
- Weights persist as `numeric(14,4)` kilograms and displayed rates use two decimals.
- A balance difference over `0.01 kg` requires an explanation before internal-test submission.
- Existing user changes outside this branch must not be overwritten.

---

### Task 1: Schema and Calculation Model

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__create_process_hierarchy.sql`
- Create: `backend/src/main/java/com/lhr/rnd/model/ProcessPlan.java`
- Create: `backend/src/main/java/com/lhr/rnd/domain/ProcessPlanCalculationService.java`
- Create: `backend/src/test/java/com/lhr/rnd/domain/ProcessPlanCalculationServiceTest.java`
- Modify: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`

**Interfaces:**
- Produces `ProcessPlan` with nested `MajorProcess`, `MinorStep`, `StepMaterial`, `ProcessInput`, `ProcessOutput`, and `ProcessYield` records.
- Produces `ProcessPlanCalculationService.calculate(MajorProcess)` and `calculateBatch(ProcessPlan)`.

- [ ] Write failing calculation tests for main yield, recovery, balance difference, zero basis, and batch yield.
- [ ] Add failing schema assertions for all six hierarchy tables and key numeric columns.
- [ ] Add migration with foreign keys, cascade deletion, sequence uniqueness, and indexes.
- [ ] Implement immutable nested records and calculation service using `BigDecimal`.
- [ ] Run `mvn -q -Dtest=ProcessPlanCalculationServiceTest,SchemaMigrationTest test` and require zero failures.

### Task 2: Transactional Persistence and API

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentProcessPlanEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentMajorProcessEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentMinorStepEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentStepMaterialEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentProcessInputEntity.java`
- Create: `backend/src/main/java/com/lhr/rnd/persistence/entity/ExperimentProcessOutputEntity.java`
- Create: matching repositories under `backend/src/main/java/com/lhr/rnd/persistence/repository/`
- Create: `backend/src/main/java/com/lhr/rnd/service/ProcessPlanService.java`
- Create: `backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java`
- Create: `backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`

**Interfaces:**
- `GET /api/v1/experiment-forms/{id}/process-plan` returns a persisted plan or a legacy-compatible plan.
- `PUT /api/v1/experiment-forms/{id}/process-plan` saves the full aggregate transactionally and returns calculated yields.
- Save request uses `versionNo`; mismatch returns `409 PROCESS_PLAN_VERSION_CONFLICT`.

- [ ] Write controller tests for empty plan, nested save/reload, calculation results, validation, version conflict, and legacy fallback.
- [ ] Implement entities and repositories with explicit string IDs and ordered child queries.
- [ ] Implement transactional replace-save, legacy fallback, and conversion to `ExperimentProcessStep` summaries.
- [ ] Implement controller validation and response errors using existing `ApiResponse` conventions.
- [ ] Grant R&D edit access and tester read access in `RolePermissionService`.
- [ ] Run `mvn -q -Dtest=ProcessPlanControllerTest,RolePermissionServiceTest test` and require zero failures.

### Task 3: Shared Contracts and PC Editor

**Files:**
- Create: `frontend/packages/shared/src/process-plan.ts`
- Modify: `frontend/packages/shared/src/index.ts`
- Modify: `frontend/packages/shared/src/api/task.ts`
- Create: `frontend/apps/pc/src/components/ProcessHierarchyEditor.vue`
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`
- Create: `frontend/tests/process-hierarchy-contract.test.mts`
- Modify: `frontend/e2e/pc-smoke.spec.ts`

**Interfaces:**
- `ProcessPlanDraft` mirrors backend field names and enum values.
- `api.task.getProcessPlan(formId)` and `api.task.saveProcessPlan(formId, plan)` wrap the new endpoints.
- `ProcessHierarchyEditor` accepts `v-model`, `readonly`, and emits legacy summary rows.

- [ ] Write failing contract tests for nested types, API routes, editor markers, and legacy summary conversion.
- [ ] Add shared types and pure yield/summary helpers.
- [ ] Build the three-column editor with reusable templates, major/minor drag sorting, drawers, material rows, output rows, and aggregate yield summary.
- [ ] Replace `ProcessTabsEditor` in PC experiment form behind a default-on local feature flag and preserve the old editor as fallback.
- [ ] Save the nested plan after the experiment draft has an ID, and use emitted summary rows in the existing draft payload.
- [ ] Run `pnpm typecheck`, contract tests, and PC build with zero failures.

### Task 4: Mobile Read-only and Submission Validation

**Files:**
- Create: `frontend/apps/mobile/src/components/ProcessHierarchyReadonly.vue`
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java`
- Create: `frontend/tests/mobile-process-hierarchy-contract.test.mts`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Mobile fetches `getProcessPlan(formId)` and renders collapsible major processes without mutation controls.
- Submission validation consumes the persisted plan and rejects missing valid steps; balance differences over `0.01` require `remark`.

- [ ] Write failing mobile contract and backend submission-validation tests.
- [ ] Implement compact mobile read-only cards for steps, key parameters, materials, and yields.
- [ ] Load the plan for existing forms and retain the legacy mobile editor only when no upgraded plan exists.
- [ ] Add backend submission rules without changing draft-save permissiveness.
- [ ] Run focused backend tests, mobile typecheck, and mobile build with zero failures.

### Task 5: Regression, Documentation, and Safe Integration

**Files:**
- Create: `docs/工艺分层与得率功能使用说明.md`
- Modify: `docs/研发样品管理系统_执行记录.md`

**Interfaces:**
- Documents feature flag, migration behavior, API, permissions, PC workflow, and mobile read-only behavior.

- [ ] Run full backend tests with `mvn test`.
- [ ] Run frontend contract checks, route checks, typecheck, PC build, and mobile build.
- [ ] Run focused Playwright smoke tests for the experiment form.
- [ ] Review the diff for secrets, generated artifacts, and unrelated user files.
- [ ] Commit the verified branch and integrate it into `codex/rnd-sample-foundation` without overwriting the user's dirty files.
