# R&D workbench and export implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver independent trial schemes, planned/actual comparison and consistent readable exports without financial calculations.

**Architecture:** Persist each trial as a versioned JSON document associated with an experiment form. Keep the existing normalized formal process graph and immutable revision pipeline; trial promotion bridges into that pipeline transactionally with provenance and a displaced-draft backup. Render documents from a shared snapshot-derived view, not from live drafts.

**Tech Stack:** Java/Spring Boot/JDBC/Flyway/Jackson, Vue 3/TypeScript/Ant Design Vue, Apache POI, JUnit, Node test runner.

**Spec:** `docs/superpowers/specs/2026-09-15-rnd-workbench-and-exports-design.md` and `docs/superpowers/specs/2026-09-15-rnd-trial-workbench-design.md`.

## Global Constraints

- 核价文件不显示单价、金额、成本、税费、利润或自动报价。
- 计划投料和目标参数不得代替实际数据参与得率计算。
- 中间产物只记录状态和流向，不进入物料库，不作为外部加料重复计入配方。
- 推荐不等于正式提交，正式研发记录不等于批准生产标准。
- 不覆盖工作区已有未提交改动，不改旧正式快照。
- Work directly in the user's requested project checkout on `codex/rnd-sample-foundation`; no branch switching, resetting, staging unrelated files, pushing or deployment.
- Only one implementation agent at a time. Controller performs integration inspection and maintains this plan; separate task reviews follow implementations.
- Copy clears control confirmations and release decisions even when actual data is retained. Missing actuals are not zero measurements.
- Keep existing authorized roles and lifecycle locks; all write and read endpoints derive principal from server session.
- Scope excludes supplier batches, material-library rebuild, blind tasting, pilot scaling and production approval workflow.

## Task 1: Persist independent trial drafts and safe copies

Status: completed and independently reviewed through `ced2f26`; 16 focused tests and 325 backend tests passed.

**Files:** Create model `backend/src/main/java/com/lhr/rnd/model/TrialScheme.java`, domain `TrialSchemeCopyService.java`, service `TrialSchemeService.java`, API `TrialSchemeController.java`, migration `V25__create_trial_scheme.sql`, tests `TrialSchemeServiceTest.java` and `TrialSchemeCopyServiceTest.java`. Only add a targeted access/state helper to `ProcessPlanService` if required.

**Interfaces:** `/api/v1/experiment-forms/{formId}/trials` GET list, POST create; `/{trialId}` GET, PUT versioned save; `/{trialId}/copy` POST; `/{trialId}/archive` POST versioned archive/restore. Standard ApiResponse wrapper. Trial JSON has id, experimentFormId, versionNo, name, sourceTrialId, archived, purpose, variables, conclusion (`PENDING|ADJUST|REJECT|RECOMMEND`), recommendationReason, qualityScore (nullable 0–10), qualityNotes, difficulty (`EASY|MEDIUM|HARD` or null), plan (ProcessPlan), plannedData (JSON object), inheritedActuals, createdBy/At, updatedBy/At. PlannedData keys reference current step/material/major IDs, using `materialWeightsKg`, `stepParameters` (parameter1Value/parameter2Value), `majorYieldTargets`, `batchYieldTarget`, and `yieldBasisNote`. Server IDs/audit fields must not be accepted from save body.

- [ ] Write real Spring integration tests using unique task/form fixtures, following ProcessRevisionServiceTest seeding. Assert independent persistence, stale save conflict, malformed fields rejected, forbidden roles/unassigned engineers rejected, locked form rejected for create/save/copy/archive, missing form rejected for director.
  ```java
  assertThat(reloadedA.name()).isEqualTo("方案A");
  assertThat(reloadedB.versionNo()).isEqualTo(2);
  assertThatThrownBy(() -> saveWithVersion(1)).isInstanceOf(BusinessException.class);
  ```
- [ ] Run `mvn -q -Dtest=TrialSchemeServiceTest,TrialSchemeCopyServiceTest test` from backend, retain the expected missing-feature RED result.
- [ ] Implement JDBC JSON persistence with atomic `where version_no=?` updates, FK to form, explicit mutable-state validation matching existing formal-submit lifecycle. Copy uses new IDs for all graph nodes and references; rejects dangling/cross-scheme references. Remap plannedData keys. Default copy moves existing actual values into absent plan targets then clears actual weights/parameters/measurements; preserve operations, equipment and control standards. Copy-with-actuals marks inheritance and clears confirmations/resolved/retest-release decisions. Archive is reversible and versioned. Do not modify the active formal graph or auto-create a trial on GET.
- [ ] Run focused tests then full `mvn -q test`; self-review endpoints and audit spoofing; commit only Task 1 files and report exact request/response signatures for Task 2.

## Task 2: Transactional trial promotion and snapshot provenance

Status: completed and independently reviewed in `1769ccb`; 15 focused tests and 340 backend tests passed. Deeper populated-graph/late-failure rollback regression coverage is carried into Task 6.

**Files:** Create `TrialPromotionService.java`, migration `V26__bind_trial_revision_provenance.sql`, tests `TrialPromotionServiceTest.java`; modify trial controller and targeted revision model/service code. Store immutable trial JSON, displaced draft JSON, request key and resulting revision ID in a promotion record, FK to form/trial/revision. Existing revision JSON remains unchanged.

**Interfaces:** POST `/{trialId}/submission-preview` with trialVersionNo and expectedProcessVersionNo returns checks plus differingDraft flag and preview token/hash. POST `/{trialId}/submit` takes versions, preview token, confirmed, changeReason, idempotencyKey; returns ProcessRevision. GET revision source metadata is authorized via existing formal read path, not draft read.

Trial controls also need explicit `/{trialId}/control-points/{pointId}/confirm` (valid current passing measurements) and `/confirm-deviation` (director only, documented disposition and passing retest) actions, with trialVersionNo. Derive confirmer and time on server. Reject confirmation of unchanged inherited measurements; save invalidates prior confirmation if location, control definition or measurements change. A confirmed trial's trusted evidence must survive promotion remapping without becoming client-forgeable. This closes the critical-point gate rather than removing it.

- [ ] Write integration tests for unconfirmed/stale/unauthorized rejection, existing-draft preservation, invalid control rollback, retry returning same revision, mismatched request-key reuse rejection, and immutable source snapshot after later edits.
  ```java
  assertThat(retry.id()).isEqualTo(first.id());
  assertThat(beforeFailure).usingRecursiveComparison().isEqualTo(planService.find(formId));
  ```
- [ ] Run `mvn -q -Dtest=TrialPromotionServiceTest test` to observe RED.
- [ ] Implement promotion transaction locking form, active graph and trial in a consistent order. Compare preview against current inputs, remap graph IDs, preserve prior draft, transfer validated trial into existing formal graph and use existing submission validator and role checks. Do not trust inherited control confirmations. Preserve plannedData and evaluation in immutable promotion snapshot. Expose explicit source name/version on revision read without granting trial access to testers/finance.
- [ ] Run focused plus full backend tests, self-review rollbacks and existing sent-test binding; commit scoped changes and record provenance API for exports.

## Task 3: Trial workbench and planned/actual comparison UI

Status: implemented and independently reviewed through `0f7c789` on2026-09-22. Task6 latest fullfrontend155/build passed; CUA save/switch/reload and confirmation checked. Final review carries newly observed missing-summary and long-name layout issues for one scoped fix wave.

**Files:** Create `frontend/apps/pc/src/components/trials/TrialWorkbench.vue`, `TrialComparison.vue`, `trialDraft.ts`, `trialComparison.ts`, service `trialApi.ts`, tests `frontend/tests/trial-workbench.test.mts`, `trial-comparison.test.mts`; modify ExperimentFormView and expose a controlled persistence adapter in ProcessPlanWorkspace as needed, preserving existing dirty edits.

**Interfaces:** Consume Task 1 APIs and Task 2 promotion. TrialWorkbench owns trial graph separately from legacy/formal editor; persistence adapter saves trial endpoint, never active formal endpoint. Cache identity contains user/form/trial ID; async generation fence prevents late response pollution. Use current editor components for large/small process drag/drop and expansion.

- [ ] Add failing behavioral tests for cached draft identity, stale callback fences, +0.2kg difference (10.2−10), null vs zero, text parameter changes, repeated process matching and mismatched yield basis.
  ```ts
  assert.equal(numericDifference(10,10.2),0.2);
  assert.equal(numericDifference(null,10.2),null);
  ```
- [ ] Run `node --experimental-strip-types --test tests/trial-*.test.mts` and retain RED.
- [ ] Implement compact scheme bar, independent save/copy/archive restore, purpose/variable/conclusion summary and collapsed evaluation. Add planned/actual section keyed to selected major/step/material. Comparison supports baseline, differences, quality and difficulty; no price fields. Confirmation dialog shows promotion preview, conflicts and displaced-draft preservation. Only explicit promotion changes active formal graph. Unsaved switch offers save/discard/cancel; stale conflicts never auto-overwrite.
- [ ] Run focused tests, `pnpm typecheck`, `pnpm build`, full frontend tests. Browser-check A/B save/reload and differences with real backend; commit only relevant hunks/files and report remaining UI issues.

## Task 4: Snapshot export checks and pricing basis

Status: completed and independently reviewed through `cd1f6bb`. Task6 latest fullbackend360/frontend155 and API152 passed. Final pricing workbooks visually checked across all12printed pages. Preview dialogs verified by CUA; actual browser download-event receipt remains unconfirmed despite successful API file verification.

**Files:** Create `ProcessExportView.java`, `ProcessExportCheckService.java`, tests `ProcessExportCheckServiceTest.java`; extend PackagingItem/unit migration and request/frontend contracts; modify PricingFileService and pricing tests, ProcessArtifactService preview/check API.

**Interfaces:** Shared read-only export view from fixed ProcessRevision plus authorized promotion source metadata. Check result has ready and issues with code/path/message; artifact type-specific checks. Preview renders without formal archive/status writes and labels incomplete fields. Existing archived downloads unchanged.

- [ ] Test external sum 102kg vs final main72kg and process90%×80%=72%, no duplicate intermediate input, broken flow rejected, missing packaging unit unresolved, no price requirement, preview has no DB artifact creation.
  ```java
  assertThat(view.externalInputKg()).isEqualByComparingTo("102");
  assertThat(view.mainYieldPercent()).isEqualByComparingTo("72");
  ```
- [ ] Run focused tests to see RED.
- [ ] Add compatible nullable packaging quantity unit; never infer from name. Render product/source/version/base batch, actual external ingredients by role, process main inputs/outputs/yields, independently confirmed finished quantity, packaging quantity/unit/spec/conversion. Remove internal source strings and fabricated utilization/duplicate picking fields from formal-process mode. Preserve documented legacy mode, separate actual basis from 100kg normalization. Do not derive packed total from main yield. No monetary fields. Configure readable widths, print area, repeat headers and one-page width.
- [ ] Run pricing/domain/API tests and contract/typechecks, generate representative XLSX and inspect all visible sheets and print layout via spreadsheet skill; commit scoped changes and record artifact outputs.

## Task 5: Readable SOP renderer and matched formula

Status: completed and independently reviewed through `12161a9`. Backend359/frontend155 tests and typecheck/build/contracts passed atfa85c04; finalfix29focusedpassed. LatestSOP4pages and formula2sheets/6printpages visually inspected. Actual and100kg formula amounts use separately named sheets with independent repeatingheaders.

**Files:** Create `SopDocumentRenderer.java`, `ExportDisplayFormat.java`, tests `SopDocumentRendererTest.java`; modify ProcessArtifactService and associated tests.

**Interfaces:** Renderer consumes Task 4 fixed export view and preview/formal mode, returns DOCX bytes. Formula export consumes same snapshot/provenance and separates actual quantities from 100kg external-input normalization.

- [ ] Add tests extracting POI document content for Chinese roles/states/results, source scheme/version, nearby controls, no duplicate output or empty appendix, missing required instructions/control method/frequency/deviation rejection in formal mode. Inspect OOXML for repeat headers, page numbering and row split strategy.
- [ ] Run focused tests to see RED.
- [ ] Render each major vertically: summary, each step materials, operation/equipment/parameters, output, important controls. Limit small tables to readable field/value pairs or ingredient rows; never recreate seven/twelve equal columns. Separate actual audit appendix, omit absent optional fields, retain required missing markers in preview. Chinese display names with unknown-value warning; no raw IDs in operator body. Defaults: explicit Chinese-capable font, readable body, consistent black headings, subtle borders and header fill; numbers formatted without losing meaningful small values. Add product/version and page fields for multi-page traceability. Do not claim production approval.
- [ ] Run tests and generate long-Chinese-name, multiple-additive, multiple-process, control-heavy samples; use document skill render and inspect every page, iterate layout without changing source business records; commit scoped changes.

## Task 6: End-to-end and role acceptance

Status: Task6 completed and independently reviewed through `508dc59`. Final bounded fix wave committed `39770f5`, backend364/frontend159/contracts/routes/typecheck/build passed; API152 baseline retained. Sole scoped rereview closed7/8 findings; SOP incomplete-preview missing-name/unit markers remain P2 (formal generation gate fixed). Browser file receipt remains unverified. Both are explicit acceptance limitations, not an all-complete claim.

**Files:** Extend `scripts/verify-rnd-roles.mjs` only if necessary; create `scripts/verify-rnd-trials.mjs`, report `docs/研发工作台与导出验收-2026-09-15.md`.

- [ ] Add executable API acceptance assertions for A/B isolation, plan/actual difference, promotion+preview, matching export sources, no cost fields, lifecycle locks, and test/finance forbidden access to drafts. Use isolated test records and ephemeral sessions; never log credentials or tokens.
- [ ] Run full backend and frontend suites, API contract checker, typecheck/build; use local preview environment only.
- [ ] Browser-inspect real trial editing/comparison/promotion dialogs and file downloads, record exact results and limitations. Re-render representative final XLSX/DOCX if relevant code changed.
- [ ] Independent final review across this plan's diff, resolve Important/Critical issues, preserve unrelated dirty files. Write user-readable acceptance report and hand off current preview URL without claiming food process validation or production readiness.
