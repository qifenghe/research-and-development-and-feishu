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

## Review-Fix Verification

Updated only the Task 1 test contract files:

- `backend/src/test/java/com/lhr/rnd/service/PricingFileServiceTest.java`
- `backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java`

The assertions now derive summary, reference-output, yield, package-count, and packaging positions from `materialCount`. The former fixed row assertions for rows 49, 50, 51, 52, 57, and 60 were removed. All three material-count scenarios invoke the same soft-assertion helper, so the 25-material scenario continues through every structural, style, and print assertion.

`PricingWorkbookAssertions` opens the versioned classpath template at
`/templates/pricing-material-list-template.xlsx` as its auditable style baseline. It compares the complete logical-cell style contract (font, fill, alignment, protection, and all border sides/colors) for material rows and relocated summary/packaging sections. It also checks weight `0.000`, utilization and yield `0.00%`, packaging quantity `0`, header date `yyyy.MM.dd`, A4 portrait printing, `fitWidth=1`, and that the explicit print area reaches the final numbered packaging row.

Command run from `backend`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest test
```

Result: `BUILD FAILURE` as intended for the RED phase. The suite compiled and ran 3 tests; all 3 failed and none errored. The two-material, official-template, and 25-material scenarios reported 43, 34, and 250 soft assertion failures respectively.

The failures are all current production-format defects rather than test construction failures:

- Header and logical material cells are recreated with default styles, losing the template's fonts, fills, alignment, and borders; material weight and utilization formats become `General` instead of `0.000` and `0.00%`.
- The dynamic summary, reference-output, yield, and package-count rows are absent at the material-count-derived positions. In particular, the 25-material assertion reaches the expected `SUM(G13:G37)` and related follow-on rows rather than stopping at an earlier hard assertion.
- The fixed packaging block leaves 34 blank rows for two materials and 11 for 25 materials before the `包装物料` marker; the dynamic summary and packaging styles are therefore absent at their required positions.
- The generated workbook has no explicit print area, so it cannot prove inclusion of the final packaging row.

The current template-derived A4 paper size, portrait orientation, `fitWidth=1`, header date text, and the static packaging quantity format already meet their respective checks; they do not contribute to the RED failures.

## Important Finding Completion

Completed the remaining Task 1 `Important` review finding in
`PricingWorkbookAssertions` only; no production Java code, template, or export
logic was changed.

The print contract now reads the page settings through the POI `Sheet` API:
`getAutobreaks()`, `getFitToPage()`, `getHorizontallyCenter()`, and
`isDisplayGridlines()`. It requires automatic page breaks, fit-to-page, and
horizontal centering to be enabled, and gridlines to be hidden.

The template includes the confidentiality statement immediately after the
packaging structure. The print range is therefore required to start at `A1` and
end exactly at that confidentiality row, which is also asserted to be the final
content row. This deliberately rejects both a cropped packaging/footer and an
overextended print area; the former permissive `>=` end-row assertion was
removed.

Command run from `backend` after adding these assertions:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest test
```

Result: `BUILD FAILURE` remains the expected RED result: 3 tests ran, 3 failed,
and 0 errored. The two-material, official-template, and 25-material scenarios
reported 44, 35, and 251 soft assertion failures. In addition to the existing
layout failures, every generated workbook still has display gridlines enabled
and lacks an explicit print area, so neither the required `A1` start nor the
strict final-content-row end can yet be satisfied. The production implementation
was not modified.

## Print Width Assertion Follow-up

Updated only `backend/src/test/java/com/lhr/rnd/service/PricingWorkbookAssertions.java`.
The print-area contract now requires the area to start at `A1`, end at the
template formal form's final column (including the complete packaging table;
`K` in the current template), and end at the confidentiality statement row.
The final-column expectation is derived from the template form rather than
from the generated workbook.

Command run from `backend`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest test
```

Result: `BUILD FAILURE` remains the expected RED result: 3 tests ran, 3
failed, and 0 errored. The generated workbook still has no explicit print
area, so the new end-column assertion is correctly guarded by the existing
missing-print-area failure; production code was not changed.
