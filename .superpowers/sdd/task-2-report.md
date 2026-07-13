# Task 2: Formal Pricing Workbook Implementation Report

## Delivered

- Rebuilt `pricing-material-list-template.xlsx` as a de-identified formal pricing template. The reference workbook was reviewed for the formal form structure; the existing `A:K` compatibility layout was retained because the export contract and Task 1 assertions require material data to begin at Excel row 13.
- Cleared the previous product title, product attributes, people, dates, material/package names, internal codes, weights, quantities, formulas, and workbook sheet name from the template while retaining headers, blank sample rows, column widths, row heights, merged regions, styles, and confidentiality statement.
- Added dynamic layout calculation through package-visible `LayoutRows layoutRows(int materialCount)`. It reserves at least one material row, relocates the complete summary/packaging/footer structure, and rebuilds material/stage merges.
- Added complete template-row copying for cell types, styles, formulas, comments, row height, and migrated merged regions. The implementation never removes template style cells.
- Applied formal numeric formats through the template: weights `0.000`, utilization/yield `0.00%`, and package quantities `0`. Generated sheets set A4 portrait, fit-to-page, width 1, horizontal centering, hidden gridlines, and a dynamic `A1:K` print area ending at the confidentiality statement.
- Corrected the Task 1 print-area assertion to compare POI column indexes as `short`, matching `AreaReference`'s API.

## Template De-identification Check

The generated template was inspected with Apache POI and its `xl/sharedStrings.xml` was scanned for former product/business values. The scan found no matches for old product name fragments, material-code prefixes, material names, weights, dates, or named personnel, including:

```text
500g|香卤|大肠|YRP|FGT|TJJ|FXL|FYT|FBT|YSC|99999|100.000|2026.04.28|赵新武|黄丽金
```

The retained content is limited to form labels, blank styled sample rows, generic document controls, and the confidentiality statement.

## Verification

RED observed before implementation:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest test
```

Result: expected failure, 3 tests failed because the former fixed-layout exporter lost template styles, left the packaging block fixed, and had no explicit print area.

GREEN verification:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest test
```

Result: PASS, 3 tests, 0 failures, 0 errors.

Regression verification:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -q -Dtest=PricingFileServiceTest,ReportExportControllerTest,SampleWorkflowControllerTest test
```

Result: PASS. `PricingFileServiceTest` 3/3, `ReportExportControllerTest` 2/2, and `SampleWorkflowControllerTest` 78/78; all had 0 failures and 0 errors.
