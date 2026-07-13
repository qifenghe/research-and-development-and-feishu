# Task 3 Report: 核价优先的实验计算与表单

## Status

Complete. Task 3 implements shared and backend pricing calculations, reworks the mobile and PC experiment forms around pricing data, and enforces submission completeness in both UI and backend workflow boundaries.

## RED Evidence

1. Backend calculation test first failed to compile because `ExperimentCalculationService.pricingPreview(...)` did not exist.
2. Frontend calculation test first failed to load because `averageUnitWeightKg`, `referenceQuantity`, and `calculatePricingPreview` were not exported.
3. Backend submission tests were then added before the service change. All four failed because `POST /submit-test` returned `200` instead of the required validation error:
   - zero primary-material weight
   - no effective process
   - missing finished-output weight
   - missing finished-output quantity
4. After adding submission validation, application bootstrap exposed an incomplete `FeedbackDemoSeedService` experiment record. The stack trace identified that seed record as the source; it was updated with a valid process and output data only.

## GREEN Evidence

- Backend pricing preview for 10kg main material, 2kg ingredient, 8.5kg output, and 17 bags: total input 12kg, primary yield 85%, average unit weight 0.5kg, reference quantity 17.
- Process input 10kg, output 8kg, residual 1kg: loss 1kg and loss rate 10%.
- Server-side submit boundary now rejects incomplete drafts with dedicated error codes while draft saving remains permissive.
- The demo workflow still seeds, submits, passes testing, and creates the expected completed records.

## Implementation

- Added backend `PricingPreview`, average-unit-weight, and reference-quantity calculations.
- Added equivalent frontend shared calculation functions and tests.
- Mobile and PC forms now use the order: basic information, formula input, key process, finished output, pricing preview.
- Formula UI removes packaging entry, keeps exactly one main material, and adds auxiliary ingredient rows with 100% default utilization.
- Finished output quantity and unit are persisted; the unit defaults to `袋`.
- Pricing previews show material roles, ratios, total input, output weight and quantity, average unit weight, and primary-material yield.
- Client and server submission guards require positive main-material weight, a named process with positive input, positive finished-output weight, and positive finished-output quantity.

## Tests

```text
backend: mvn -Dtest=ExperimentCalculationServiceTest test
  14 tests, 0 failures, 0 errors

backend: mvn -Dtest=SampleWorkflowControllerTest test
  63 tests, 0 failures, 0 errors

frontend: node --test tests/experiment-calculations.test.mts
  7 tests, 0 failures

frontend: pnpm typecheck
  shared, mobile, and PC typechecks passed
```

## Commit

Implementation commit: `24766c0 feat: add pricing-first experiment form`

## Self Check

- Only Task 3 files were committed. `SampleWorkflowService` and `SampleWorkflowControllerTest` contained pre-existing workspace changes; only Task 3 hunks were staged.
- No unrelated workspace edits were reverted or cleaned.
- Both quick sampling and arranged sampling modes remain available.
- Draft saves remain incomplete-friendly; enforcement happens only when notifying/submitting for internal testing.

## Concern

No browser-driven visual regression or end-to-end run was performed in this task. Static frontend typechecking and focused backend/controller coverage passed; a later UI acceptance pass should confirm the tightened mobile layout on target devices.

## Review Follow-up (2026-07-12)

### Status

Complete. Addressed all three Important findings from `task-3-review.md` without reverting unrelated workspace changes.

### RED Evidence

1. The backend calculation test failed to compile because `PricingPreview.primaryMaterialYieldPercent()` did not exist.
2. The frontend quantity contract test failed because `normalizePositiveIntegerQuantity` was not exported.
3. After the calculation contract was implemented, the controller suite reached the persisted-form submission regression. It rejected a tampered packaging primary with the existing, more specific `PRIMARY_MATERIAL_INVALID` code; the new assertion was corrected from the generic category code.

### Fixes

- Backend pricing preview now exposes `primaryMaterialYieldPercent` using the same percent scale and six-decimal final rounding as the frontend (`8.5 / 10 => 85`). Calculation operands are retained at their incoming precision until each result is rounded.
- Formula validation now rejects all packaging, permits `RAW` only for the single primary material, and permits `AUXILIARY` for every non-primary material. It runs during draft saving and again before test submission; drafts with only valid auxiliary rows remain allowed.
- Added API/controller coverage for posted packaging and additional non-primary RAW rows, plus a persisted-record submission revalidation case.
- Added shared positive-integer quantity mapping. Mobile uses the digit keyboard and explicit validation; PC uses `min=1`, `precision=0`, and `step=1`, with both forms blocking invalid values before API requests.

### Verification

```text
backend: mvn -Dtest=ExperimentCalculationServiceTest test
  15 tests, 0 failures, 0 errors

backend: mvn -Dtest=SampleWorkflowControllerTest test
  66 tests, 0 failures, 0 errors

frontend: node --test tests/experiment-calculations.test.mts tests/experiment-output-quantity-contract.test.mts
  11 tests, 0 failures

frontend: pnpm typecheck
  shared, mobile, and PC typechecks passed
```

### Remaining Concern

No browser-level UI run was performed. The focused page-contract tests and full typechecks protect the quantity controls, but mobile interaction on a physical target remains a useful acceptance check.

## Second Re-review Follow-up (2026-07-12)

### Status

Complete. Addressed both remaining Important findings from the second Task 3 re-review.

### RED Evidence

1. Backend calculation assertions for `10 / 12.5` expected `80.000000` but received the persisted ratio `0.800000`.
2. The API regression test posting `finishedOutputQuantity: 17.5` received `200` because Jackson coerced it to `17` before validation.
3. The renamed persistence contract initially failed schema validation because `finished_yield_percent` did not exist.

### Fixes

- Renamed the form/API contract to `finishedYieldPercent` and made the backend calculation return `0-100` values. The service now persists the percent value, and report export formats it directly instead of multiplying it again.
- Added Flyway V12 to convert legacy ratio values in `[0, 1]` to percent values and rename `finished_yield_ratio` to `finished_yield_percent`. The migration regression verifies `0.8` becomes `80`.
- Changed request and service-boundary output quantity handling to `BigDecimal`. The service accepts only positive, exact `Integer` values using `toBigIntegerExact()` and `intValueExact()`; fractions, zero, negatives, and overflow return `FINISHED_OUTPUT_QUANTITY_INVALID` without truncation.
- Updated shared frontend API/model types to use `finishedYieldPercent`; frontend calculations already publish percent values.

### Verification

```text
backend: JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=ExperimentCalculationServiceTest,SampleWorkflowControllerTest,SchemaMigrationTest test
  ExperimentCalculationServiceTest: 15 tests, 0 failures, 0 errors
  SampleWorkflowControllerTest: 70 tests, 0 failures, 0 errors
  SchemaMigrationTest: 4 tests, 0 failures, 0 errors

frontend: node --test tests/experiment-calculations.test.mts tests/experiment-output-quantity-contract.test.mts
  11 tests, 0 failures

frontend: pnpm typecheck
  shared, mobile, and PC typechecks passed
```

## Pricing Excel Final Review Follow-up (2026-07-13)

### Scope

Addressed all findings in `pricing-excel-final-review.md` without reverting unrelated workspace changes.

### Fixes

- Replaced the product-specific summary labels with `研发部参考出成(kg）` and `原料得率（%）` in both the pricing template and `PricingFileService`.
- Updated the template confidentiality statement to `江西龙汇肉制品有限责任公司`, matching the title block and generated workbooks.
- Moved the title marker to the reference order: `产品名-LHYC（核价）`, retaining the existing centered title style and leaving generated file names/API contracts unchanged.
- Added a permanent 60-material regression past the template's original 37-row capacity. It verifies material 60's value, formula, row height, cloned style, merge, summary formulas, print area, and section placement. Added an explicit non-肥肠 visible-text regression for `清炖牛腩`.

### Delivery And Verification

- Regenerated `outputs/pricing-format-review/500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx`, PDF, and PNG from the formal service fixture.
- XLSX `unzip -t` passed. LibreOffice rendered the refreshed output as one A4 portrait PDF (`595.304 x 841.89 pt`), and the rendered page was checked for contiguous title, material, summary, and packaging sections.
- Focused backend suite: `PricingFileServiceTest` 8 + `ReportExportControllerTest` 2 + `SampleWorkflowControllerTest` 78 = 88 tests, 0 failures, 0 errors.
- Full backend suite: 158 tests, 0 failures, 0 errors, 0 skipped.
