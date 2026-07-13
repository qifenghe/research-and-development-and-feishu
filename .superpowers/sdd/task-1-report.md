# Task 1: Formal Pricing Workbook Layout Contract

## Scope

- Added RED-phase tests for pricing workbooks with 2 and 25 materials.
- Added `PricingWorkbookAssertions.assertFormalLayout(Workbook, int)` for later tasks to reuse.
- Did not change production Java code, the Excel template, or exported workbook generation logic.

## Files

- `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`
- `backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java`

## Contract Covered

- Material rows begin at the template's first material row and remain consecutive.
- The summary row follows the final material row and sums the dynamic material range.
- Material borders and utilization-rate format (`0.00%`) are retained.
- The title preserves a non-default template style.
- The longest blank run between summary and packaging is at most four rows.
- Packaging remains within an explicit print area, with print width fitted to one page.
- A 25-material workbook must contain `SUM(G13:G37)` on its dynamic summary row.

## RED Verification

Command run from `backend`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -Dtest=PricingFileServiceTest test
```

Result: `BUILD FAILURE` as intended. The suite compiled and ran 3 tests; 2 failed and 0 errored.

## Failure Evidence

For the 2-material workbook, the reusable layout assertion reported these contract failures in the existing implementation:

- Title cell style index is `0` instead of inheriting the template style.
- Material rows 13 and 14 have no visible border.
- Utilization-rate cells in rows 13 and 14 use `General`, not `0.00%`.
- The expected dynamic summary row has no `总计` label or `SUM(G13:G14)` formula.
- There are 34 consecutive blank rows between the expected summary and the packaging header.
- The workbook has no explicit print area.

For the 25-material workbook, the expected dynamic summary formula `SUM(G13:G37)` is absent because the existing implementation keeps the summary at its fixed row.

These are expected RED failures caused by the current fixed-layout production implementation, not test setup failures. No production implementation was added in this task.

## Reference Inputs Reviewed

- Formal reference: `165g锋味黑椒烤烤肠（肉含量88%）（核价）-LHYC 原材料清单 2026.04.28(1).xlsx`
- Current abnormal export: `500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx`
