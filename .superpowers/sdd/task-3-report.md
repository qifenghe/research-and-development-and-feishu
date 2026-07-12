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
