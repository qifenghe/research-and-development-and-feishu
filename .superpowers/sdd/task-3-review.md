# Task 3 Independent Review

Reviewed the brief, implementation report, and full `a03e908..4d13151` diff. No report-listed tests were re-run.

## Critical

None.

## Important

### I1. Backend and frontend publish different yield units, so the required formula contract is not consistent

`ExperimentCalculationService.pricingPreview` returns the primary-material yield as a fraction: `8.5 / 10 = 0.850000` ([`backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java:66`](../../backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java#L66), asserted at [`ExperimentCalculationServiceTest.java:42`](../../backend/src/test/java/com/lhr/rnd/domain/ExperimentCalculationServiceTest.java#L42)). The shared frontend calculation returns a percent for the identical input: `85` ([`frontend/packages/shared/src/experiment/calculations.ts:81`](../../frontend/packages/shared/src/experiment/calculations.ts#L81), asserted at [`experiment-calculations.test.mts:33`](../../frontend/tests/experiment-calculations.test.mts#L33)).

This contradicts the brief's `* 100` yield formula and the global requirement that frontend and backend formulas agree. Define one explicit contract (for example, `primaryMaterialYieldPercent` on both sides), or name both values as ratios and convert only in the presentation layer. Align rounding too: the backend rounds every operand before adding/dividing ([`ExperimentCalculationService.java:61`](../../backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java#L61)), while the frontend rounds only results. Mobile accepts arbitrary decimal weights ([`ExperimentFormView.vue:57`](../../frontend/apps/mobile/src/views/ExperimentFormView.vue#L57)), so fifth-decimal inputs can produce divergent previews.

### I2. The service boundary still accepts packaging and non-primary raw materials

The UI removes packaging choices, but `normalizeExperimentMaterials` preserves the caller-supplied category ([`SampleWorkflowService.java:2020`](../../backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java#L2020)). `validatePrimaryMaterial` rejects `PACKAGING` only when it is marked primary ([`SampleWorkflowService.java:2040`](../../backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java#L2040)); it permits a non-primary `PACKAGING` row and permits extra non-primary `RAW` rows. Those rows are then included in persisted formula ratios ([`SampleWorkflowService.java:2083`](../../backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java#L2083)).

Therefore any API client can record packaging or a formula that is not one main material plus auxiliary ingredients, bypassing the new forms. Enforce the category invariant in `saveExperimentDraft` and again at submission: reject `PACKAGING`; allow `RAW` only for the one marked primary; allow `AUXILIARY` for other rows, while still permitting an incomplete draft with no primary. Add controller tests for both bypass payloads.

### I3. Both forms permit fractional finished-product quantities, but the request model only accepts an integer

The endpoint request defines `finishedOutputQuantity` as `@Positive Integer` ([`SaveExperimentDraftRequest.java:19`](../../backend/src/main/java/com/lhr/rnd/api/SaveExperimentDraftRequest.java#L19)). The PC control explicitly accepts four decimal places ([`frontend/apps/pc/src/views/rnd/ExperimentFormView.vue:98`](../../frontend/apps/pc/src/views/rnd/ExperimentFormView.vue#L98)); the mobile numeric field has no integer constraint ([`frontend/apps/mobile/src/views/ExperimentFormView.vue:72`](../../frontend/apps/mobile/src/views/ExperimentFormView.vue#L72)). A value such as `17.5` is accepted in either form and then fails during JSON binding/validation instead of receiving form-level feedback.

Constrain quantity controls to whole positive units (or change the backend model only if fractional units are genuinely supported), and add a UI/API mapping test. The current focused tests cover only integer quantity `17`.

## Minor

None.

## Test Coverage Note

The added calculation tests validate the nominal example, but they encode the yield-unit mismatch rather than checking a shared contract. The controller tests cover the four required missing-field submission cases; they do not cover the material-category bypasses above or the frontend's fractional-quantity mapping.

---

## Re-review (2026-07-12)

Reviewed `task-3-brief.md`, `task-3-report.md`, this review history, and `review-a03e908..61c5f33.diff`. The material-category fix is effective: draft saving invokes `validatePrimaryMaterial`, submission invokes it again through `validateSubmittableExperiment`, and the invariant rejects every `PACKAGING` row, requires `RAW` for the sole primary row, and requires `AUXILIARY` for every non-primary row. The new controller coverage exercises posted packaging, an extra non-primary `RAW`, and a persisted-row revalidation.

## Critical

None.

## Important

### I1. The persisted/API yield contract remains a ratio, not a percent

The follow-up changed the new, otherwise unused `PricingPreview.primaryMaterialYieldPercent`, but the workflow that actually saves and returns an experiment form still calls `ExperimentCalculationService.finishedYield`, which returns `output / primaryInput` (`8 / 10 = 0.8`) at [`ExperimentCalculationService.java:31`](../../backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java#L31). That value is persisted and exposed as `finishedYieldRatio` by [`SampleWorkflowService.java:417`](../../backend/src/main/java/com/lhr/rnd/service/SampleWorkflowService.java#L417) and [`ExperimentForm.java:23`](../../backend/src/main/java/com/lhr/rnd/model/ExperimentForm.java#L23); the controller test explicitly asserts `0.8` at [`SampleWorkflowControllerTest.java:251`](../../backend/src/test/java/com/lhr/rnd/api/SampleWorkflowControllerTest.java#L251). The frontend pricing preview displays `80` as a percent.

This leaves the required frontend/backend percent contract split across the actual API/persistence path and the frontend preview. Rename and migrate the persisted/API field to an explicit percent contract, or keep an explicitly named ratio end-to-end and convert only in presentation; the task requirement calls for the former. Add an API round-trip assertion for `80`, not just the isolated `PricingPreview` unit test.

### I2. The backend silently truncates fractional JSON quantities instead of rejecting them

[`SaveExperimentDraftRequest.java:19`](../../backend/src/main/java/com/lhr/rnd/api/SaveExperimentDraftRequest.java#L19) uses `@Positive Integer`, but the application does not disable Jackson's default `DeserializationFeature.ACCEPT_FLOAT_AS_INT`. A fresh probe using the project's Jackson line confirmed that it is enabled and deserializes JSON `{"finishedOutputQuantity":17.5}` to `Integer(17)`. Bean validation then sees the positive integer `17`, so an API caller can bypass the UI's `normalizePositiveIntegerQuantity` guard and save a fractional quantity as a different integer.

Reject decimal numeric tokens at the request boundary (for example, by configuring integer coercion to fail or by deserializing/validating an exact numeric representation), and add a controller test that posts `17.5` and expects a 4xx response. Until then, the requirement that both frontend and backend allow only positive integers is not met.

## Minor

None.

## Verification

```text
backend: JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=ExperimentCalculationServiceTest test
  exit 0

backend: JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=SampleWorkflowControllerTest test
  exit 0

frontend: node --test tests/experiment-calculations.test.mts tests/experiment-output-quantity-contract.test.mts
  11 tests passed

frontend: pnpm typecheck
  shared, mobile, and PC typechecks passed
```

## Conclusion

Not Approved: the two Important contract defects above remain. The material-category constraint is approved, but the yield and backend quantity requirements still need correction and targeted controller/API regression coverage.

---

## Final Independent Re-review (2026-07-12)

Reviewed `task-3-brief.md`, `task-3-report.md`, the prior review history, and `review-2946d82..1057d26.diff` against the implementation at `1057d26`. No business code was changed.

## Critical

None.

## Important

### I1. V12 corrupts valid legacy yields above 100 percent

[`V12__convert_finished_yield_to_percent.sql`](../../backend/src/main/resources/db/migration/V12__convert_finished_yield_to_percent.sql#L1) multiplies only legacy `finished_yield_ratio` values in `[0, 1]` before renaming the column. The pre-V12 calculation permits any non-negative ratio and has no upper-bound validation: a 12 kg finished output from 10 kg primary input was persisted as ratio `1.2` ([`ExperimentCalculationService.java`](../../backend/src/main/java/com/lhr/rnd/domain/ExperimentCalculationService.java#L31) at `2946d82`). After V12, that row is renamed to `finished_yield_percent = 1.2`, which is rendered as `1.2%`; the correct percent value is `120`.

The current migration regression covers only `0.8 -> 80` ([`SchemaMigrationTest.java`](../../backend/src/test/java/com/lhr/rnd/persistence/SchemaMigrationTest.java#L173)), so it cannot detect this loss. Convert every valid legacy ratio when migrating (or, if V12 has already reached a persistent environment, add a corrective migration for the rows it left unchanged) and add a `1.2 -> 120` migration regression.

## Verified

- The API, domain model, entity mapping, persistence, and read-back contract use `finishedYieldPercent`; server calculation yields percent-scale values, and the focused controller test verifies `80` in both API response and `finished_yield_percent` storage.
- `finishedOutputQuantity: 17.5` is received as `BigDecimal` and rejected through `toBigIntegerExact()`; the focused controller test expects `FINISHED_OUTPUT_QUANTITY_INVALID`. Positive integer `17` succeeds.
- Export uses `formatPercent` directly for `finishedYieldPercent`, rather than the ratio formatter that multiplies by 100.
- The existing material-category invariant remains active for posted packaging, extra non-primary raw material, and persistence-tampered data.

## Verification

```text
backend: JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -Dtest=ExperimentCalculationServiceTest,SampleWorkflowControllerTest,SchemaMigrationTest test
  exit 0

frontend: node --test tests/experiment-calculations.test.mts tests/experiment-output-quantity-contract.test.mts
  11 passed, 0 failed
```

## Conclusion

Not Approved: no Critical findings, but I1 is an Important historical-data migration defect. The API/quantity/export/category paths are otherwise approved by this review.

---

## Migration Boundary Resolution (2026-07-12)

The remaining I1 finding is resolved. The regression was first changed to use a valid legacy ratio above one (`1.2`) and failed with `1.200000` instead of `120`. V12 now converts every non-negative, non-null legacy ratio before renaming the column, so yields above 100 percent are preserved correctly.

Focused verification passed:

```text
ExperimentCalculationServiceTest + SampleWorkflowControllerTest + SchemaMigrationTest
exit 0
```

Conclusion: **Approved**. Task 3 has no remaining Critical or Important findings.
