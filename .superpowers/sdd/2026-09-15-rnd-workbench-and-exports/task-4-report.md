# Task 4 implementation report — 2026-09-22

Status: implemented and verified, ready for controller independent review. No deployment/push, branch switch, destructive reset, or additional implementation/review agents. Branch `codex/rnd-sample-foundation`, implementation base `0f7c789`. Existing unrelated edits and outputs were preserved.

## Scope and boundaries

- Added a shared read-only `ProcessExportView` and artifact-specific `ProcessExportCheckService`. Formal views use immutable revision snapshots and authorized promotion-source metadata; draft/trial views read saved versions with version fences, never promote, submit, create artifacts, archive, or mutate approval/audit state.
- Actual external ingredients are summed once, excluding intermediate inputs. The representative fixture is external **102 kg**, main flow **100 → 90 → 72 kg**, process yields **90% × 80% = 72%**, and separately entered finished output **74 kg / 74 袋**. Packed output is not inferred from yield. There are no price, cost, margin, tax, or legacy cost-source matching requirements in formal-process pricing.
- Main yield reuses the existing domain formula. Preview completeness validates the whole primary flow, not merely first/last weights. Genuine measured terminal zero can display 0%; missing actual values remain pending. Formal submission validation remains unchanged. `PRIMARY_INPUT` is supported; `NONE` is excluded from multiplication; historical `TOTAL_INPUT` remains stored but explicitly unsupported/pending and blocks new yield-dependent formal generation.
- Added nullable explicit `quantityUnit` to pricing/template model, persistence, requests and PC/mobile editors. Historical null is not inferred from a material name. Template units are copied explicitly; known system label type has explicit 张. `generatePricingFile` still creates editable `DRAFT_PACKAGING`; completeness blocks actual file creation in `confirmPricingPackaging`, not draft creation.
- Formal pricing receives locked experiment finished weight/count/unit explicitly, never derived `SampleVersion.referenceOutputKg`. Saved trial/draft previews do not borrow the experiment's finished quantity because no per-trial independent packed-output association exists; those fields remain pending.
- Existing READY artifact downloads and legacy non-process pricing remain available. A later failed artifact does not hide an earlier READY download. New formal generation remains server-checked even if a client bypasses disabled controls.

## Exact API contracts for Task 5/6

All routes use the existing authenticated server session (never a body-supplied principal), RND engineer owner / RND director authorization. Migration and clean-database role defaults grant only these RND roles the five GET routes.

| Method/path (prefix `/api/v1`) | Query | Result |
| --- | --- | --- |
| GET `/experiment-forms/{formId}/process-plan/revisions/{revisionId}/export-check` | `artifactType` | `ApiResponse<Check>` |
| GET `/experiment-forms/{formId}/process-plan/export-check` | `versionNo`, `artifactType` | `ApiResponse<Check>` |
| GET `/experiment-forms/{formId}/process-plan/export-preview` | `versionNo`, `artifactType` | attachment bytes |
| GET `/experiment-forms/{formId}/trials/{trialId}/export-check` | `versionNo`, `artifactType` | `ApiResponse<Check>` |
| GET `/experiment-forms/{formId}/trials/{trialId}/export-preview` | `versionNo`, `artifactType` | attachment bytes |

`artifactType`: `FORMULA_XLSX | SOP_DOCX | PRICING_XLSX`. `Check = {ready:boolean, issues:Issue[]}`; `Issue = {code,path,message,majorSequence:Integer|null,stepSequence:Integer|null}`. Paths include sequence locators, e.g. `majorProcesses[sequence=1].steps[sequence=2].instruction`. Preview is allowed when incomplete and clearly labels pending data; it is not formal eligibility. Invalid types use `PROCESS_ARTIFACT_TYPE_INVALID`; formal generation failure uses `PROCESS_EXPORT_NOT_READY`. Existing business error envelopes and session/ownership/version rejection conventions are preserved. Downloads supply correct XLSX/DOCX content type, content length, UTF-8 attachment filename.

Shared Java service entry points: `revisionView(formId,revisionId,principal)`, `draftView(formId,versionNo,principal)`, `trialView(formId,trialId,versionNo,principal)`, `check(view,type)`, `preview(view,type)`. Draft read uses a read-only repeatable-read transaction to avoid mixing normalized rows across a concurrent save. The immutable snapshot/source path is reused, not reconstructed from current mutable data. Pricing has a purpose-built internal locked-form source path that validates locked lifecycle and revision/form association without inventing a user principal.

Shared view contains product/source/snapshot, actual external total, batch main yield, ingredient rows with role and step locations, major input/output/yield rows, independent finished quantity plus source, packaging, completeness issues, and specification/compiler/date metadata. Task 5 can replace SOP/formula renderers using this view and the same endpoints. No new public view endpoint is introduced.

## Frontend contracts

- `processExportApi.ts`: `ExportType`, `ExportIssue`, `ExportCheck`, `ExportScope {formId, trialId?, versionNo}`; `exportApi.check(scope,type)`, `.preview(scope,type)`, `.revisionCheck(formId,revisionId,type)`, using existing API client.
- `ProcessExportPreview.vue`: props `formId?`, `trialId?`, `versionNo`, `sourceLabel`, `disabled?`; checks/downloads all three types and emits issue location. Request-generation fencing invalidates late responses when form/trial/version/source/disabled state changes or component unmounts. Dirty/busy editors cannot start saved-source preview; downloading never saves or submits.
- `TrialWorkbench` mounts preview for the current saved scheme/version. `ProcessPlanWorkspace` mounts saved formal-draft preview; major/step issues open the relevant major where practical. `RndOutputCenter` loads per-type revision checks, disables formal generation on failure, and keeps historical READY downloads visible even if checks fail.
- The base summary beneath trial work is explicitly labeled `正式工艺 / 实验单基础数据预览（非当前试验方案）`; missing finished weight is `待填写`, distinct from numeric zero.
- `PricingPackagingItem.quantityUnit?: string | null` is edited explicitly on both PC/mobile pricing pages; old clients/rows remain compatible.

## Workbook and read-only artifact QA

Formal pricing title is **产品核价基础数据表**; preview visibly includes **研发预览 · 非正式归档**. Three short distinct-schema sheets cover actual ingredients, main flow, independent output/packaging. Headers identify product, specification, source/revision, compiler/date and actual batch. No 100 kg normalized values are mixed into pricing; normalization stays in the separate formula output. Structural totals-row blanks remain blank, not 待填写. Missing actual rows have concrete pending reasons and major/step locators. Numeric integer cells render without trailing decimal points.

POI remains the application renderer. Spreadsheet skill was used for read-only QA; controller used the bundled artifact runtime and bundled headless LibreOffice, not desktop Excel/LibreOffice. Final layout uses readable CJK font, expanded name columns, CJK-aware wrapped row heights, compact metadata, table-header-only repeat, A4 landscape one-page width, explicit print areas, and product/source/sheet/page footers. A long-name overlap identified by initial print QA was reproduced and fixed, then rechecked.

Generated test-only fixtures (not committed):

- `backend/target/task-4-export-qa/formal-basis-102kg-72percent-74bags.xlsx`
- `backend/target/task-4-export-qa/trial-incomplete-preview.xlsx`
- `backend/target/task-4-export-qa/long-chinese-37-ingredient-basis.xlsx` (37 ingredients, long Chinese ingredient and packaging names; external 110.75 kg, main yield 72%, independent output 74 retained).

Controller final read-only QA: `/private/tmp/rnd-export-qa-0922.8zDblM/formal-v2` and `/private/tmp/rnd-export-qa-0922.8zDblM/print-v2`. All three final formal sheets imported/rendered without error tokens. All **12 printed pages** inspected: formal 3 + incomplete preview 3 + long fixture 6; prior overlap gone, Chinese legible, long names readable, repeated headers/footer/page numbers present, distinct 102/72%/74 and preview missing-item/step labels visible. No blocking layout issue. Artifact importer displayed stray 25/95 for structural empty shared strings; underlying XLSX XML and LibreOffice both confirm those cells are empty, so this is a read-only importer anomaly, not an application value defect. No source-data workaround was added.

## TDD and verification evidence

Focused tests were written/run RED before implementation, then GREEN:

| Test / command family (from `backend`, unless noted) | Observed RED | GREEN coverage |
| --- | --- | --- |
| `mvn -q -Dtest=ProcessExportCheckServiceTest test` | Missing shared view/check classes and explicit unit constructor | 102 vs 72%, no double-count, broken/missing flow, zero vs missing, legacy basis, packaging pending |
| `mvn -q -Dtest=PricingBasisTest test` | Missing `renderBasis` | Numeric formal basis, no monetary/internal columns, incomplete preview, representative XLSX fixtures |
| `mvn -q -Dtest=ProcessArtifactServiceTest test` | Missing draft/trial view/preview contracts | Read-only preview, owner/version/source fences, no artifact writes |
| `mvn -q -Dtest=PricingPackagingServiceTest test` | Explicit template-unit constructor unavailable | Unit copied explicitly, names do not infer unit |
| `node --experimental-strip-types --test tests/process-export-mounted.test.mts` (frontend) | Missing new API/component | Two mounted tests: late-response/source fencing and dirty refusal; incomplete checks preserve older READY downloads |
| Metadata regression | Saved draft metadata null | Product/spec/source/compiler/date supplied; RED log `/private/tmp/task4-metadata-red.log` |
| Print-layout regression (`PricingBasisTest`) | Repeated header first row 0, expected 5 | Table-only repeats, footer and sufficient wrapped row height; logs `/private/tmp/task4-layout-red.log`, `/private/tmp/task4-layout-green.log` |
| Missing-external SOP preview (`ProcessArtifactServiceTest`) | Missing actual input rendered `0.0000kg` | Pending actual total/yield rather than fabricated zero; RED log `/private/tmp/task4-sop-preview-red.log` |

Additional API integration checks cover real-session export contracts/auth/version handling, editable packaging draft with missing units, confirmation-unit persistence, explicit locked independent 8.5 kg/17-count source, and archived/new-generation distinction. Old formal-cost/layout assertions were replaced with the new approved no-money basis expectations; legacy-template tests remain. Minimal SOP software fixtures gained explicit instruction/control data instead of weakening standards.

Final serial verification (all exit 0):

```text
cd backend
mvn -q test > /private/tmp/task4-backend-verified.log 2>&1
Surefire XML aggregate: tests=348, failures=0, errors=0

cd frontend
node --experimental-strip-types --test tests/*.test.mts > /private/tmp/task4-frontend-tests.log 2>&1
tests 154; pass 154; fail 0; cancelled/skipped/todo 0

pnpm check:api-contracts && pnpm check:routes && pnpm typecheck && pnpm build > /private/tmp/task4-frontend-build.log 2>&1
API contracts passed; routes PC 30 / mobile 16; shared + PC + mobile typechecks passed;
PC and mobile production builds passed (existing Vite chunk-size advisory only).

git diff --check
No whitespace errors.
```

An earlier overlapping full Maven run was not accepted as verification: it reported 335 tests / 26 failures / 10 errors from stale formal-layout/monetary assertions, incomplete packaging/SOP fixtures, and transient concurrent compilation/context loading. A later integration run had one obsolete formal-filename expectation. Those expectations/fixtures were corrected to the approved contract; the subsequent **serial** full-suite result above has no remaining failures. Focused/integration intermediate logs are `/private/tmp/task4-focus.log` and `/private/tmp/task4-integration.log`; final serial output is authoritative.

Preview runtime rebuilt/restarted with the existing runner (escalation only for required process visibility):

```text
node scripts/run-rnd-preview.mjs stop
node scripts/run-rnd-preview.mjs build > /private/tmp/task4-preview-build.log 2>&1
node scripts/run-rnd-preview.mjs start
node scripts/run-rnd-preview.mjs status
backend 8082 health 200; PC 5183 /admin 200; mobile 5184 /m 200
```

Health was rechecked with the approved process-visible runner on **2026-09-22 17:31:43 Asia/Shanghai (09:31:43 UTC)**: all three endpoints again returned 200, runner exit 0. Controller's sandbox-only curl separately returned connection failure; it is not used as proof of browser availability. Feishu remains disabled in preview. Ports 8080/5173 were not touched. Controller browser acceptance remains **not completed**: binding the prior IAB error tab encountered Browser URL policy blocking on `data:errorpage`; no bypass attempted and no UI pass claimed. HTTP health, mounted UI tests, real-session API tests and spreadsheet render/print evidence are separate from browser acceptance.

## Scoped changed files

New:

- `backend/src/main/java/com/lhr/rnd/api/ProcessExportController.java`
- `backend/src/main/java/com/lhr/rnd/model/ProcessExportView.java`
- `backend/src/main/java/com/lhr/rnd/service/ProcessExportCheckService.java`
- `backend/src/main/resources/db/migration/V27__export_packaging_quantity_unit.sql`
- `backend/src/test/java/com/lhr/rnd/service/ProcessExportCheckServiceTest.java`
- `backend/src/test/java/com/lhr/rnd/service/PricingBasisTest.java`
- `frontend/apps/pc/src/services/processExportApi.ts`
- `frontend/apps/pc/src/components/process/ProcessExportPreview.vue`
- `frontend/tests/process-export-mounted.test.mts`
- This report.

Modified:

- Domain: `ProcessPlanCalculationService.java`, `ProcessSubmissionValidator.java`.
- Model/entity: `PackagingTemplateItem.java`, `PricingPackagingItem.java`, `PackagingTemplateItemEntity.java`, `PricingPackagingItemEntity.java`.
- Services: `PricingFileService.java`, `PricingPackagingService.java`, `ProcessArtifactService.java`, `RolePermissionService.java`, `SampleWorkflowService.java`, `TrialPromotionService.java`.
- Backend tests: `SampleWorkflowControllerTest.java`, `SessionAuthenticationInterceptorTest.java`, `PricingFileServiceTest.java`, `PricingPackagingServiceTest.java`, `ProcessArtifactServiceTest.java`.
- PC: `ProcessPlanWorkspace.vue`, `RndOutputCenter.vue`, `TrialWorkbench.vue`, `ExperimentFormView.vue`, `views/shipment/PricingDetailView.vue`.
- Mobile: `views/PricingDetailView.vue`; shared `types/index.ts`.

Not staged/changed by this task: pre-existing `DemandDetailView.vue`, task-3 review, overall plan edits, unrelated report/scripts/output directories and package cache.

## Remaining boundaries / handoff

- Task 5 owns replacement SOP/formula layout and final naming; this task provides the shared view, checks and saved-source preview path, without expanding that redesign.
- Trial-specific independent packed quantity remains deliberately unavailable/pending until a separately approved model exists.
- TOTAL_INPUT migration/interpretation remains intentionally unsupported, not silently converted.
- Fixtures are software-only; no real food-process parameter validation or production acceptance is asserted.
- Controller independent code review and browser acceptance are separate follow-up gates; no reviewer agent was spawned under the one-implementation-agent instruction.
