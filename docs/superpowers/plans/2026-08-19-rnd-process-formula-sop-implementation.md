# R&D Process, Formula, and SOP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the sampling form into a versioned major-process/minor-step workspace that tracks intermediate products and critical controls, calculates chained main-material yields, and generates formula, SOP, and pricing-linked outputs from one formal process revision.

**Architecture:** Keep `experiment_process_plan` as the editable working draft and persist every formal submission as an immutable JSON revision. Extend minor steps with typed outputs and critical-control records; derive major-process yield, final yield, and formula lines from that graph. Generate formula XLSX and SOP DOCX artifacts from a formal revision, while the existing pricing workflow links to the same revision after packaging confirmation.

**Tech Stack:** Java 17+, Spring Boot 3.3, JdbcTemplate, Flyway, Jackson, Apache POI/XWPF, H2 tests, Vue 3, TypeScript, Ant Design Vue, pnpm, Node test runner.

**Spec:** `docs/superpowers/specs/2026-08-19-rnd-process-formula-sop-design.md`

## Global Constraints

- Intermediate products exist only inside a process plan/revision and never create material-master, purchasing, inventory, or cost-master records.
- Each minor step has at most one yield-bearing primary input and one primary output.
- Major yield uses the first valid primary input and final valid primary output; finished yield is `100 × Π(majorYield / 100)`.
- Minor-step yields are diagnostic only and are never multiplied again into finished yield.
- Drafts remain editable; formal revisions are immutable and edits start a new draft with a required change reason.
- Critical unresolved key controls allow draft save but block formal submission.
- Formula, SOP, and pricing artifacts reference one immutable process revision.
- Existing simplified process data and pricing approval/finance handoff remain compatible.
- Do not add a new frontend or backend dependency.

---

## File Structure

### Backend domain and persistence

- `backend/src/main/resources/db/migration/V20__extend_process_revision_and_outputs.sql`: revision, step-output, control-point, artifact, and pricing-source schema.
- `backend/src/main/java/com/lhr/rnd/model/ProcessPlan.java`: request/response aggregate extended with step outputs and controls.
- `backend/src/main/java/com/lhr/rnd/model/ProcessRevision.java`: immutable revision summary/detail DTOs.
- `backend/src/main/java/com/lhr/rnd/model/ProcessArtifact.java`: generated formula/SOP metadata.
- `backend/src/main/java/com/lhr/rnd/domain/ProcessPlanCalculationService.java`: step, major, and chained yield calculations.
- `backend/src/main/java/com/lhr/rnd/domain/ProcessRecipeService.java`: external-input aggregation and intermediate-product exclusion.
- `backend/src/main/java/com/lhr/rnd/domain/ProcessSubmissionValidator.java`: formal-submission checks.
- `backend/src/main/java/com/lhr/rnd/service/ProcessPlanService.java`: draft save/load plus step-output/control persistence.
- `backend/src/main/java/com/lhr/rnd/service/ProcessRevisionService.java`: immutable submit/list/read/restore behavior.
- `backend/src/main/java/com/lhr/rnd/service/ProcessArtifactService.java`: formula XLSX and SOP DOCX generation.
- `backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java`: draft, validation, revision, and artifact endpoints.
- `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`: attach the selected process revision to pricing output.

### Frontend shared and PC

- `frontend/packages/shared/src/process-plan.ts`: graph, controls, revisions, recipes, validation, and yield helpers.
- `frontend/packages/shared/src/api/task.ts`: process draft/revision/artifact API methods.
- `frontend/packages/shared/src/api/report.ts`: artifact download methods.
- `frontend/apps/pc/src/components/process/ProcessPlanWorkspace.vue`: top-level draft/formal workflow.
- `frontend/apps/pc/src/components/process/MajorProcessBoard.vue`: first-level major-process composer.
- `frontend/apps/pc/src/components/process/MinorStepWorkspace.vue`: expanded step editor and material flow.
- `frontend/apps/pc/src/components/process/ControlPointEditor.vue`: controls, measurements, deviation resolution.
- `frontend/apps/pc/src/components/process/ProcessSubmitDialog.vue`: validation and formal confirmation.
- `frontend/apps/pc/src/components/process/RndOutputCenter.vue`: formula/SOP/pricing readiness and downloads.
- `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`: replace the embedded hierarchy card with the workspace.

### Mobile and tests

- `frontend/apps/mobile/src/components/ProcessHierarchyReadonly.vue`: revision-aware read-only process/control/output view.
- Backend tests beside the corresponding domain/service/controller classes.
- `frontend/tests/process-workspace-contract.test.mts`: source-level interaction contract.
- Existing `frontend/tests/process-hierarchy-contract.test.mts` and mobile contract tests remain green.

---

### Task 1: Schema for revisions, intermediate outputs, controls, and artifacts

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__extend_process_revision_and_outputs.sql`
- Modify: `backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java`

**Interfaces:**
- Produces tables `experiment_step_output`, `experiment_control_point`, `experiment_control_measurement`, `experiment_process_revision`, `experiment_process_artifact`.
- Produces nullable `pricing_file.process_revision_id` for Task 7.

- [ ] **Step 1: Write failing schema assertions**

Add assertions:

```java
assertTableExists("experiment_step_output");
assertTableExists("experiment_control_point");
assertTableExists("experiment_control_measurement");
assertTableExists("experiment_process_revision");
assertTableExists("experiment_process_artifact");
assertColumnExists("pricing_file", "process_revision_id");
assertNumericColumn("experiment_step_output", "weight_kg", 14, 4);
assertNumericColumn("experiment_control_measurement", "measured_value", 14, 4);
```

- [ ] **Step 2: Run migration test and verify failure**

Run: `cd backend && mvn -q -Dtest=SchemaMigrationTest test`

Expected: FAIL because the V20 tables do not exist.

- [ ] **Step 3: Add migration**

Create tables with cascade foreign keys and sequence uniqueness. The key columns are:

```sql
create table experiment_step_output (
  id varchar(64) primary key,
  minor_step_id varchar(64) not null,
  sequence integer not null,
  output_type varchar(30) not null,
  output_name varchar(200) not null,
  material_state varchar(30) not null default 'SEMI_SOLID',
  weight_kg numeric(14,4),
  primary_output boolean not null default false,
  continue_flow boolean not null default true,
  remark varchar(1000),
  constraint uk_step_output_sequence unique (minor_step_id, sequence),
  constraint fk_step_output_step foreign key (minor_step_id)
    references experiment_minor_step(id) on delete cascade
);

create table experiment_process_revision (
  id varchar(64) primary key,
  process_plan_id varchar(64) not null,
  experiment_form_id varchar(64) not null,
  revision_no integer not null,
  source_revision_id varchar(64),
  change_reason varchar(1000),
  submitted_by varchar(100),
  submitted_at timestamp not null,
  snapshot_json text not null,
  snapshot_hash varchar(64) not null,
  constraint uk_process_revision_no unique (experiment_form_id, revision_no),
  constraint fk_process_revision_plan foreign key (process_plan_id)
    references experiment_process_plan(id)
);
```

Add control standard/measurement tables, artifact metadata with `storage_key`, and a nullable pricing revision foreign key.

- [ ] **Step 4: Run migration test**

Run: `cd backend && mvn -q -Dtest=SchemaMigrationTest test`

Expected: PASS with Flyway applying 20 migrations.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__extend_process_revision_and_outputs.sql backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java
git commit -m "feat: add process revision and control schema"
```

### Task 2: Shared process graph and chained yield calculations

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/model/ProcessPlan.java`
- Modify: `backend/src/main/java/com/lhr/rnd/domain/ProcessPlanCalculationService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/domain/ProcessPlanCalculationServiceTest.java`
- Modify: `frontend/packages/shared/src/process-plan.ts`
- Modify: `frontend/tests/process-hierarchy-contract.test.mts`

**Interfaces:**
- Produces `MinorStep.outputs(): List<StepOutput>` and `MinorStep.controlPoints(): List<ControlPoint>`.
- Produces Java `calculateStep`, `calculate`, and `calculateBatch` and TypeScript equivalents with the same formulas.

- [ ] **Step 1: Add failing backend calculation tests**

Cover a two-step major and three-major chain:

```java
assertThat(service.calculateStep(step("鲜牛腩", ten(), "修割牛腩", nine())))
    .isEqualByComparingTo("90.000000");
assertThat(service.calculate(majorWithFlow("10.0000", "8.0000")).mainYieldPercent())
    .isEqualByComparingTo("80.000000");
assertThat(service.calculateBatch(planWithMajorYields("90", "80", "70")))
    .isEqualByComparingTo("50.400000");
```

Also assert that auxiliary materials do not change the denominator and `yieldBasis=NONE` is skipped.

- [ ] **Step 2: Run calculation tests and verify failure**

Run: `cd backend && mvn -q -Dtest=ProcessPlanCalculationServiceTest test`

Expected: FAIL because step outputs and chained multiplication are unavailable.

- [ ] **Step 3: Extend the aggregate and calculation service**

Add records:

```java
public record StepOutput(String id, int sequence, String outputType,
        String outputName, String materialState, BigDecimal weightKg,
        boolean primaryOutput, boolean continueFlow, String remark) {}

public record ControlPoint(String id, int sequence, String controlType,
        String importance, String itemName, BigDecimal targetValue,
        BigDecimal lowerLimit, BigDecimal upperLimit, String unit,
        String method, String frequency, String deviationAction,
        boolean resolved, String confirmedBy, List<ControlMeasurement> measurements) {}
```

Implement batch multiplication:

```java
BigDecimal ratio = BigDecimal.ONE;
for (var major : plan.majorProcesses()) {
    if ("NONE".equals(major.yieldBasis())) continue;
    var value = calculate(major).mainYieldPercent();
    if (value != null) ratio = ratio.multiply(value.divide(BigDecimal.valueOf(100)));
}
return ratio.multiply(BigDecimal.valueOf(100)).setScale(RATE_SCALE, HALF_UP);
```

- [ ] **Step 4: Mirror types and helpers in TypeScript**

Add `StepOutputDraft`, `ControlPointDraft`, and:

```ts
export function calculateBatchYield(plan: ProcessPlanDraft): number | null {
  const rates = plan.majorProcesses
    .filter((major) => major.yieldBasis !== "NONE")
    .map(calculateMajorProcessYield)
    .map((result) => result.mainYieldPercent)
    .filter((value): value is number => value != null);
  return rates.length ? rates.reduce((value, rate) => value * rate / 100, 100) : null;
}
```

- [ ] **Step 5: Run focused backend and frontend tests**

Run:

```bash
cd backend && mvn -q -Dtest=ProcessPlanCalculationServiceTest test
cd ../frontend && node --test --experimental-strip-types tests/process-hierarchy-contract.test.mts
pnpm typecheck
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/model/ProcessPlan.java backend/src/main/java/com/lhr/rnd/domain/ProcessPlanCalculationService.java backend/src/test/java/com/lhr/rnd/domain/ProcessPlanCalculationServiceTest.java frontend/packages/shared/src/process-plan.ts frontend/tests/process-hierarchy-contract.test.mts
git commit -m "feat: calculate chained main-material yields"
```

### Task 3: Recipe aggregation and formal-submission validation

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/domain/ProcessRecipeService.java`
- Create: `backend/src/main/java/com/lhr/rnd/domain/ProcessSubmissionValidator.java`
- Create: `backend/src/main/java/com/lhr/rnd/model/ProcessSubmissionCheck.java`
- Create: `backend/src/test/java/com/lhr/rnd/domain/ProcessRecipeServiceTest.java`
- Create: `backend/src/test/java/com/lhr/rnd/domain/ProcessSubmissionValidatorTest.java`
- Modify: `frontend/packages/shared/src/process-plan.ts`

**Interfaces:**
- Produces `List<RecipeLine> aggregate(ProcessPlan plan)` grouped by material ID/code/name.
- Produces `ProcessSubmissionCheck validate(ProcessPlan plan)` with `ready`, `errors`, and `warnings`.

- [ ] **Step 1: Write failing recipe tests**

```java
var lines = service.aggregate(planWithRepeatedExternalMaterialAndIntermediateFlow());
assertThat(lines).extracting(RecipeLine::materialName, RecipeLine::weightKg)
    .containsExactly(tuple("鲜牛腩", bd("10.0000")), tuple("食盐", bd("0.1800")));
assertThat(lines).extracting(RecipeLine::materialName)
    .doesNotContain("修割牛腩", "焯水牛腩");
```

- [ ] **Step 2: Write failing validation tests**

Assert submission is blocked for a broken primary-flow reference, missing major yield input/output, or unresolved critical control; assert an out-of-balance warning does not block when a reason exists.

- [ ] **Step 3: Run tests and verify failure**

Run: `cd backend && mvn -q -Dtest=ProcessRecipeServiceTest,ProcessSubmissionValidatorTest test`

Expected: FAIL because the services do not exist.

- [ ] **Step 4: Implement aggregation**

Use only step materials with `sourceType=EXTERNAL`; group using `formulaMaterialId`, then material code, then normalized name. Sum weights without rounding intermediate operands and calculate ratio after total aggregation.

- [ ] **Step 5: Implement submission validation**

Return stable codes such as:

```java
error("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链", major.sequence(), step.sequence());
error("CRITICAL_CONTROL_UNRESOLVED", "极重要关键控制点未完成", major.sequence(), step.sequence());
warning("MATERIAL_BALANCE_EXCEEDED", "物料平衡差超过允许范围", major.sequence(), null);
```

- [ ] **Step 6: Add TypeScript recipe/validation preview helpers**

Mirror aggregation for immediate UI preview; backend remains authoritative on submission.

- [ ] **Step 7: Run tests and typecheck**

Run:

```bash
cd backend && mvn -q -Dtest=ProcessRecipeServiceTest,ProcessSubmissionValidatorTest test
cd ../frontend && pnpm typecheck
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/domain backend/src/main/java/com/lhr/rnd/model/ProcessSubmissionCheck.java backend/src/test/java/com/lhr/rnd/domain frontend/packages/shared/src/process-plan.ts
git commit -m "feat: aggregate process formula and validate submissions"
```

### Task 4: Persist step outputs, controls, and measurements in drafts

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/ProcessPlanService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/service/ProcessPlanServiceTest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java`
- Create: `backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java`

**Interfaces:**
- Extends existing `GET/PUT /api/v1/experiment-forms/{formId}/process-plan` without breaking old payloads.
- Produces `GET /submission-check` for immediate server-side validation.

- [ ] **Step 1: Add failing round-trip service test**

Save a draft containing one intermediate output, one critical point, and three measurements; reload it and assert all values and source links survive.

- [ ] **Step 2: Run service test and verify failure**

Run: `cd backend && mvn -q -Dtest=ProcessPlanServiceTest test`

Expected: FAIL because V20 child data is not persisted.

- [ ] **Step 3: Extend save/load transactions**

Add focused private methods:

```java
private void saveStepOutputs(String stepId, List<ProcessPlan.StepOutput> outputs)
private void saveControlPoints(String stepId, List<ProcessPlan.ControlPoint> controls)
private List<ProcessPlan.StepOutput> loadStepOutputs(String stepId)
private List<ProcessPlan.ControlPoint> loadControlPoints(String stepId)
```

Delete through existing cascade when replacing major processes, then insert all child records in sequence order.

- [ ] **Step 4: Add controller contract test**

Use MockMvc to verify old draft JSON with no `outputs` or `controlPoints` is accepted and returned as empty arrays; verify `/submission-check` returns stable error codes.

- [ ] **Step 5: Run service/controller tests**

Run: `cd backend && mvn -q -Dtest=ProcessPlanServiceTest,ProcessPlanControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/service/ProcessPlanService.java backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java backend/src/test/java/com/lhr/rnd/service/ProcessPlanServiceTest.java backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java
git commit -m "feat: persist intermediate outputs and process controls"
```

### Task 5: Immutable formal revisions and new-draft workflow

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/model/ProcessRevision.java`
- Create: `backend/src/main/java/com/lhr/rnd/service/ProcessRevisionService.java`
- Create: `backend/src/test/java/com/lhr/rnd/service/ProcessRevisionServiceTest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java`
- Modify: `frontend/packages/shared/src/process-plan.ts`
- Modify: `frontend/packages/shared/src/api/task.ts`

**Interfaces:**
- Produces `POST /process-plan/submit`, `GET /process-plan/revisions`, `GET /process-plan/revisions/{revisionId}`, and `POST /process-plan/revisions/{revisionId}/new-draft`.
- Produces shared `ProcessRevisionSummary` and `SubmitProcessPlanRequest`.

- [ ] **Step 1: Write failing revision service tests**

Assert:

```java
var revision = service.submit(formId, new SubmitCommand(versionNo, true, "首次正式提交", "研发工程师"));
assertThat(revision.revisionNo()).isEqualTo(1);
assertThat(service.find(revision.id()).snapshot()).isEqualTo(savedPlan);
assertThatThrownBy(() -> service.submit(formId, new SubmitCommand(versionNo, false, null, "研发工程师")))
    .hasMessageContaining("确认");
```

Also verify unresolved critical points block submission and a new draft from revision 1 requires a change reason before revision 2 submit.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -q -Dtest=ProcessRevisionServiceTest test`

Expected: FAIL because the revision service does not exist.

- [ ] **Step 3: Implement immutable snapshots**

Serialize normalized `ProcessPlan` with the configured Jackson `ObjectMapper`, hash UTF-8 JSON with SHA-256, insert one revision row, and never update revision snapshots.

Use optimistic draft `versionNo`; reject stale submissions with `PROCESS_PLAN_VERSION_CONFLICT`.

- [ ] **Step 4: Add endpoints and frontend API**

Add exact shared methods:

```ts
submitProcessPlan(formId, request: SubmitProcessPlanRequest): Promise<ProcessRevision>
getProcessRevisions(formId): Promise<ProcessRevisionSummary[]>
getProcessRevision(formId, revisionId): Promise<ProcessRevision>
createDraftFromRevision(formId, revisionId, changeReason): Promise<ProcessPlanDraft>
```

- [ ] **Step 5: Run backend tests and frontend typecheck**

Run:

```bash
cd backend && mvn -q -Dtest=ProcessRevisionServiceTest,ProcessPlanControllerTest test
cd ../frontend && pnpm typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/model/ProcessRevision.java backend/src/main/java/com/lhr/rnd/service/ProcessRevisionService.java backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java backend/src/test/java/com/lhr/rnd/service/ProcessRevisionServiceTest.java backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java frontend/packages/shared/src/process-plan.ts frontend/packages/shared/src/api/task.ts
git commit -m "feat: add formal process revisions"
```

### Task 6: Generate standard formula XLSX and SOP DOCX

**Files:**
- Create: `backend/src/main/java/com/lhr/rnd/model/ProcessArtifact.java`
- Create: `backend/src/main/java/com/lhr/rnd/service/ProcessArtifactService.java`
- Create: `backend/src/test/java/com/lhr/rnd/service/ProcessArtifactServiceTest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java`
- Modify: `backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java`
- Modify: `frontend/packages/shared/src/api/task.ts`
- Modify: `frontend/packages/shared/src/api/report.ts`

**Interfaces:**
- Produces formula `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Produces SOP `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
- Produces list/generate/download endpoints under `/process-plan/revisions/{revisionId}/artifacts`.

- [ ] **Step 1: Write failing artifact tests**

For formula XLSX, open bytes with `XSSFWorkbook` and assert material rows exclude intermediate products and include actual/100kg weights. For SOP DOCX, open with `XWPFDocument` and assert paragraphs/tables contain major processes, steps, intermediate flow, limits, and deviation actions.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -q -Dtest=ProcessArtifactServiceTest test`

Expected: FAIL because artifact generation is unavailable.

- [ ] **Step 3: Implement formula workbook**

Use `ProcessRecipeService.aggregate(snapshot)` and create columns:

```text
序号 | 物料编码 | 物料名称 | 角色 | 打样重量(kg) | 配方占比(%) | 100kg折算(kg) | 加入步骤
```

Store generated bytes through the existing local archive/storage abstraction and persist artifact metadata.

- [ ] **Step 4: Implement SOP document**

Generate title/version metadata, one section per major process, one row per minor step, a material-flow table, yield summary, key-control table, and version-change section. Standards are included; experiment measurement records remain in the revision trace appendix rather than production instruction columns.

- [ ] **Step 5: Add API methods and download behavior**

Reject artifact generation from a draft ID. Repeated generation for the same revision/type creates the next document version and preserves earlier metadata.

- [ ] **Step 6: Run artifact/controller tests**

Run: `cd backend && mvn -q -Dtest=ProcessArtifactServiceTest,ProcessPlanControllerTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/model/ProcessArtifact.java backend/src/main/java/com/lhr/rnd/service/ProcessArtifactService.java backend/src/main/java/com/lhr/rnd/api/ProcessPlanController.java backend/src/test/java/com/lhr/rnd/service/ProcessArtifactServiceTest.java backend/src/test/java/com/lhr/rnd/api/ProcessPlanControllerTest.java frontend/packages/shared/src/api/task.ts frontend/packages/shared/src/api/report.ts
git commit -m "feat: generate formula and SOP artifacts"
```

### Task 7: Link the pricing workflow to a process revision

**Files:**
- Modify: `backend/src/main/java/com/lhr/rnd/service/PricingFileService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`
- Modify: `backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java:1791`
- Modify: `backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java`

**Interfaces:**
- Consumes a formal `processRevisionId` and its recipe/yield snapshot.
- Produces pricing records with `process_revision_id`; existing packaging review and finance statuses do not change.

- [ ] **Step 1: Add failing pricing linkage test**

Generate pricing for a sample version with a selected formal process revision; assert the workbook uses revision formula/yield data and the persisted pricing record references that revision.

- [ ] **Step 2: Run pricing tests and verify failure**

Run: `cd backend && mvn -q -Dtest=PricingFileServiceTest test`

Expected: FAIL because pricing generation has no revision source.

- [ ] **Step 3: Add an overload that accepts revision data**

Keep current signatures for compatibility and add:

```java
PricingFileResult generate(SampleVersion version, String pricingVersionNo,
        String customerName, ProcessRevision revision, List<PricingPackagingItem> packaging)
```

Use revision recipe and finished yield when present; otherwise preserve the existing legacy calculation path.

- [ ] **Step 4: Persist the revision link at the workflow call site**

Select the latest approved/formal revision for the experiment form used by the sample version. If none exists, keep the current behavior and mark the pricing detail as legacy-sourced.

- [ ] **Step 5: Run pricing and workflow tests**

Run: `cd backend && mvn -q -Dtest=PricingFileServiceTest,SampleWorkflowControllerTest test`

Expected: PASS with all existing pricing approval assertions unchanged.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/service/PricingFileService.java backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java
git commit -m "feat: source pricing from formal process revision"
```

### Task 8: Build the PC major-process-first workspace

**Files:**
- Create: `frontend/apps/pc/src/components/process/ProcessPlanWorkspace.vue`
- Create: `frontend/apps/pc/src/components/process/MajorProcessBoard.vue`
- Create: `frontend/apps/pc/src/components/process/MinorStepWorkspace.vue`
- Create: `frontend/apps/pc/src/components/process/ControlPointEditor.vue`
- Create: `frontend/apps/pc/src/components/process/ProcessSubmitDialog.vue`
- Create: `frontend/apps/pc/src/components/process/RndOutputCenter.vue`
- Modify: `frontend/apps/pc/src/views/rnd/ExperimentFormView.vue`
- Create: `frontend/tests/process-workspace-contract.test.mts`

**Interfaces:**
- Consumes Task 2 shared helpers and Task 5/6 API methods.
- Emits normalized `ProcessPlanDraft`; the experiment form remains responsible for task-level navigation.

- [ ] **Step 1: Write failing source-contract tests**

Assert the experiment form renders `ProcessPlanWorkspace`; assert the workspace includes major board, minor-step workspace, draft save, formal submit, revision history, KCP editor, and output center. Assert the finished-yield label uses `calculateBatchYield`.

- [ ] **Step 2: Run contract test and verify failure**

Run: `cd frontend && node --test --experimental-strip-types tests/process-workspace-contract.test.mts`

Expected: FAIL because the workspace components do not exist.

- [ ] **Step 3: Implement the major-process-first shell**

The initial state displays `MajorProcessBoard`. Selecting a major switches the center area to `MinorStepWorkspace` with a visible breadcrumb/back action. Keep template drag/copy/reorder behavior from `ProcessHierarchyEditor.vue`, moving reusable logic rather than duplicating it.

- [ ] **Step 4: Implement minor-step editing and intermediate flow**

Render four sections: basic parameters, step materials, primary/intermediate output, and live summary. External materials can select formula/material IDs; intermediate inputs select previous step outputs and show “不进入物料库”.

- [ ] **Step 5: Implement critical controls**

`ControlPointEditor` supports control type, importance, target/limits, method, frequency, multiple measurements, deviation action, resolution, and confirmer. Show blocking red state only for unresolved critical items.

- [ ] **Step 6: Implement draft and submit interactions**

Manual/auto-save calls existing PUT. `ProcessSubmitDialog` first loads `/submission-check`, renders errors/warnings/formula/major yields/final yield, requires an explicit checkbox, and sends change reason for revision 2+.

- [ ] **Step 7: Implement output center**

List formula and SOP artifact readiness/download actions immediately after revision submit. Show pricing as waiting until the existing packaging workflow creates it; never provide a second pricing-generation path here.

- [ ] **Step 8: Run frontend checks**

Run:

```bash
cd frontend
node --test --experimental-strip-types tests/process-workspace-contract.test.mts tests/process-hierarchy-contract.test.mts
pnpm typecheck
pnpm build:pc
```

Expected: PASS and Vite production build succeeds.

- [ ] **Step 9: Commit**

```bash
git add frontend/apps/pc/src/components/process frontend/apps/pc/src/views/rnd/ExperimentFormView.vue frontend/tests/process-workspace-contract.test.mts
git commit -m "feat: add major-process-first R&D workspace"
```

### Task 9: Revision-aware mobile read-only view and permissions

**Files:**
- Modify: `frontend/apps/mobile/src/components/ProcessHierarchyReadonly.vue`
- Modify: `frontend/apps/mobile/src/views/ExperimentFormView.vue`
- Modify: `frontend/tests/mobile-process-hierarchy-contract.test.mts`
- Modify: `backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java`
- Modify: `backend/src/test/java/com/lhr/rnd/service/RolePermissionServiceTest.java`

**Interfaces:**
- Mobile reads drafts for authorized R&D roles and formal revisions for tester/read-only roles.
- Adds read/write/generate/download permission patterns for the new endpoints.

- [ ] **Step 1: Add failing permission and mobile contract tests**

Assert R&D engineer can save/submit/generate, tester can read formal revision/SOP but cannot mutate, finance can read linked pricing source summary but cannot read draft controls.

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```bash
cd backend && mvn -q -Dtest=RolePermissionServiceTest test
cd ../frontend && node --test --experimental-strip-types tests/mobile-process-hierarchy-contract.test.mts
```

Expected: FAIL for missing revision/artifact rules and mobile fields.

- [ ] **Step 3: Add endpoint permission patterns**

Add specific GET/POST patterns for submission checks, submit, revision history, draft restore, artifact generation, and download. Do not grant wildcard access to all `/process-plan/**` routes.

- [ ] **Step 4: Extend mobile read-only rendering**

Show revision status, intermediate flow, critical controls, major yields, finished yield, and formula/SOP download actions. Do not add drag, edit, measurement, or submission controls to mobile in this iteration.

- [ ] **Step 5: Run tests, typecheck, and mobile build**

Run:

```bash
cd backend && mvn -q -Dtest=RolePermissionServiceTest test
cd ../frontend
node --test --experimental-strip-types tests/mobile-process-hierarchy-contract.test.mts
pnpm typecheck
pnpm build:mobile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lhr/rnd/service/RolePermissionService.java backend/src/test/java/com/lhr/rnd/service/RolePermissionServiceTest.java frontend/apps/mobile/src/components/ProcessHierarchyReadonly.vue frontend/apps/mobile/src/views/ExperimentFormView.vue frontend/tests/mobile-process-hierarchy-contract.test.mts
git commit -m "feat: expose formal process outputs by role"
```

### Task 10: Compatibility, full verification, and user documentation

**Files:**
- Modify: `docs/工艺分层与得率功能使用说明.md`
- Modify tests only if full-suite failures reveal a real compatibility expectation; do not weaken assertions.

**Interfaces:**
- Confirms the full system works with V1–V20 migrations, old process payloads, existing pricing tests, and both frontend applications.

- [ ] **Step 1: Document the final workflow**

Update the guide with: major-first entry, step materials, intermediate products, key controls, draft/formal revisions, formula/SOP timing, chained yield formula, and pricing linkage.

- [ ] **Step 2: Run all backend tests**

Run: `cd backend && mvn -q test`

Expected: PASS with 20 migrations applied in schema tests.

- [ ] **Step 3: Run all frontend tests and static checks**

Run:

```bash
cd frontend
node --test --experimental-strip-types tests/*.mts
pnpm typecheck
pnpm check:api-contracts
pnpm check:routes
pnpm build
```

Expected: all tests and both production builds PASS.

- [ ] **Step 4: Inspect changes and preserve unrelated user work**

Run:

```bash
git diff --check
git status --short
git log --oneline --decorate -12
```

Confirm `.superpowers/sdd/task-3-review.md`, `frontend/apps/pc/src/views/demand/DemandDetailView.vue`, report scripts, output folders, and any other pre-existing dirty paths remain uncommitted and unchanged by this feature.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/工艺分层与得率功能使用说明.md
git commit -m "docs: explain process revisions and R&D outputs"
```

- [ ] **Step 6: Final handoff**

Report the implemented commit range, exact test commands and outcomes, database migration number, where R&D users enter the new workspace, and that formula/SOP generation requires a formal revision while pricing remains packaging-gated.
