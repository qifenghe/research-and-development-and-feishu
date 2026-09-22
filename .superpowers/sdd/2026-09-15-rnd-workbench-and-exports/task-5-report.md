# Task 5 implementation report — 2026-09-22

Status: implemented and verified, ready for controller review. Work remained on branch `codex/rnd-sample-foundation` from base `cd1f6bb`; no branch switch, reset, push, deployment, runtime restart, UI bypass, or subagent was used. Existing unrelated dirty files were preserved.

## Delivered behavior

- Replaced the horizontal, code-heavy export with a vertical Chinese **研发 SOP** DOCX. It presents compact fixed-source/revision metadata, actual external input and yield summaries, major processes, numbered steps, actual materials, actual parameters, equipment, instructions, outputs, nearby documented control requirements, and a separate measurement/audit appendix.
- Material and output tables now write explicit OOXML `tblGrid` and DXA cell widths. Names receive wider columns, optional remark columns are omitted when empty, rows do not split, and table headers repeat. Major headings/summaries remain with the first step; each step and its control block are kept together where the page permits.
- Intermediate flow is readable without UUIDs: inputs identify the producer step number/name and outputs identify every recorded consumer step number/name. Unrecorded flow is not invented. Unknown display enums show only `待核对`; raw enum codes are not exposed in the operating body.
- Actual parameters and measurements remain trial observations, not approved production standards. Only recorded control requirements appear as requirements. The file explicitly says it records R&D facts and does not represent production approval or food-process validation.
- Replaced **标准配方** with a matched **研发配方** XLSX. It has separate actual-external-input and per-100-kg-external-input sections; denominator text explicitly excludes intermediate inputs and finished-product weight. Missing observations remain `待填写`, measured zero remains numeric zero, and no planned substitutions or finance columns were added.
- Formula aggregation reuses the existing external-material aggregation service, so repeated external identities are aggregated while `STEP_OUTPUT` inputs are not counted again. Displayed normalized values are deterministically allocated to total exactly 100.0000.
- Formula metadata uses merged A:B labels and C:F values. Numeric cells have right padding and step cells left padding. Actual and normalized tables use separate worksheets; each carries the same fixed-source metadata and its own correct repeating print-title row, source/page footer, one-page width, and unrestricted natural page height.
- Operator-facing preview, archive, validation, and download names consistently say `研发配方` / `研发SOP`. Preview and formal rendering both consume the fixed `ProcessExportView`; formal generation continues to use the immutable revision snapshot.

## TDD evidence

Focused tests were written or tightened before each implementation round:

- Initial RED: `ExportDisplayFormatTest` and `SopDocumentRendererTest` did not compile because the formatter/renderer did not exist.
- Integration RED: formula/SOP assertions failed against the former standard-formula and horizontal-production-SOP output.
- Visual-layout RED: DOCX XML lacked effective `tblGrid`/DXA widths; metadata and major summaries were oversized tables; optional empty remark columns remained; producer/consumer steps were absent.
- Review RED: unknown state `LAB_STATE_X` rendered its raw code, and the second major summary was not bound to its first step.
- Formula pagination RED round 1: the 30-material single-sheet fixture had only one header per section, used the actual table as a global print title, and had no cell indents. A fixed-row manual-break attempt passed structural tests but failed real LibreOffice print pagination because automatic page breaks split those chunks again.
- Formula pagination RED round 2: two independently named sheets, per-sheet metadata/footers, correct per-sheet print titles, and absence of manual row breaks all failed against the prior single-sheet implementation. The renderer was then simplified to the final two-sheet design.

Focused GREEN coverage includes compact/missing/zero formatting, known and unknown enum display, readable flow labels, omitted optional columns, real OOXML widths, major/step pagination binding, controls and trace appendix separation, external-only denominator, deterministic 100-kg allocation, 102-kg two-major formula, and 30-material section-correct continuation headers.

## Verification

Final serial verification after the last renderer change:

```text
cd backend
mvn -q test
Surefire XML aggregate: tests=359, failures=0, errors=0

cd frontend
node --experimental-strip-types --test tests/*.test.mts
tests=155, pass=155, fail=0, cancelled/skipped/todo=0

pnpm check:api-contracts
pnpm check:routes
pnpm typecheck
pnpm build
All exit 0; API contracts match, routes PC 30 / mobile 16, shared/PC/mobile typechecks pass,
and PC/mobile production builds pass. Existing Vite large-chunk advisory only.

git diff --check
No whitespace errors.
```

Existing environment warnings remain non-failures: Flyway notes H2 2.2.224 is newer than tested 2.2.220; the current JVM reports restricted native access and Mockito reflective final-field mutation warnings. No dependency or warning-policy change was made.

## Read-only artifact QA

Application POI code generated the artifacts; no office file was manually recreated. The controller used the bundled artifact runtime and bundled headless LibreOffice, with every generated page inspected rather than desktop LibreOffice.

- Final SOP fixture: `backend/target/task-5-export-qa/two-major-control-heavy-rnd-sop-v3.docx`. It covers two major processes, three steps, two controls, producer/consumer references, long names, optional-column omission, and two trace records. Controller inspected all 3 pages in `/private/tmp/rnd-export-qa-0922.8zDblM/sop-v3`: no overlap/cropping, correct widths, steps/controls together, major 2 with step 2.1, unknown shown only as `待核对`, and legible source/page footer.
- Matched 102-kg formula fixture: `backend/target/task-5-export-qa/two-major-102kg-rnd-formula-v2.xlsx`. Controller verified the one-page print, compact merged metadata, correct 102-kg actual total and 100-kg normalization, and no error tokens.
- Multi-page formula fixture: `backend/target/task-5-export-qa/long-30-material-rnd-formula-v4.xlsx`. It contains 30 long-name external materials across two major processes and totals 102 kg. Its `本次实际投料` and `每100kg折算` sheets each carry the correct repeat row and no manual row break. Controller imported/rendered both sheets without error tokens and inspected all 6 LibreOffice print pages in `/private/tmp/rnd-export-qa-0922.8zDblM/formula-v4`: every page repeats the correct sheet-specific header, source/footer/page number; actual 102 and normalized 100 are correct; long names and padded numeric/step columns have no overlap or clipping.

## Scoped files

New:

- `backend/src/main/java/com/lhr/rnd/service/ExportDisplayFormat.java`
- `backend/src/main/java/com/lhr/rnd/service/SopDocumentRenderer.java`
- `backend/src/test/java/com/lhr/rnd/service/ExportDisplayFormatTest.java`
- `backend/src/test/java/com/lhr/rnd/service/SopDocumentRendererTest.java`
- This report.

Modified:

- `backend/src/main/java/com/lhr/rnd/service/ProcessArtifactService.java`
- `backend/src/test/java/com/lhr/rnd/service/ProcessArtifactServiceTest.java`
- `frontend/apps/pc/src/components/process/ProcessExportPreview.vue`
- `frontend/apps/pc/src/components/process/RndOutputCenter.vue`
- `frontend/tests/process-workspace-contract.test.mts`

Not staged by this task: `DemandDetailView.vue`, task-3 review, overall plan changes, unrelated documents/scripts/output directories, package cache, and frontend test output.

## Remaining boundaries

- This is an R&D record/export, not a production approval workflow, material-library expansion, or validation of real food-process limits.
- Trial observations are not promoted into standards by rendering. No missing actual is replaced with a planned value.
- Runtime/browser acceptance remains Task 6; the existing blocked old IAB tab was not reused or bypassed, and preview services were not restarted.
