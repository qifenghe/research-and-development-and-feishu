package com.lhr.rnd.service;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.assertj.core.api.SoftAssertions;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

final class PricingWorkbookAssertions {
    private static final String TEMPLATE_PATH = "/templates/pricing-material-list-template.xlsx";
    private static final String PACKAGING_MARKER = "包装物料";
    private static final String CONFIDENTIALITY_MARKER = "本资料为机密文件，未经江西龙汇食品有限公司研发部门的书面许可，不得以任何形式保有或部分保有以及泄露本资料任何内容。";
    private static final Pattern HEADER_DATE_FORMAT = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{2}");

    private static final int PRINT_AREA_START_ROW = 0;
    private static final int PRINT_AREA_START_COLUMN = 0;
    private static final int MATERIAL_START_ROW = 12;
    private static final int TITLE_ROW = 2;
    private static final int TITLE_COLUMN = 3;
    private static final int HEADER_DATE_ROW = 7;
    private static final int HEADER_DATE_COLUMN = 6;
    private static final int SEQUENCE_COLUMN = 2;
    private static final int MATERIAL_CODE_COLUMN = 3;
    private static final int WEIGHT_COLUMN = 6;
    private static final int UTILIZATION_RATE_COLUMN = 7;
    private static final int TOTAL_COLUMN = 6;
    private static final List<Integer> MATERIAL_STYLE_COLUMNS = List.of(0, 2, 3, 4, 6, 7, 8, 9);
    private static final List<Integer> SUMMARY_STYLE_COLUMNS = List.of(3, 6, 9);
    private static final List<Integer> PACKAGING_HEADER_STYLE_COLUMNS = List.of(0, 2, 3, 4, 5, 6, 7, 8, 10);
    private static final List<Integer> PACKAGING_ROW_STYLE_COLUMNS = List.of(0, 2, 3, 4, 6, 7, 8, 10);

    private PricingWorkbookAssertions() {
    }

    static void assertFormalLayout(Workbook workbook, int materialCount) {
        try (var template = openTemplate()) {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(materialCount).as("material count").isPositive();

                var sheet = workbook.getSheetAt(0);
                var templateSheet = template.getSheetAt(0);
                var summaryRowIndex = MATERIAL_START_ROW + materialCount;
                var referenceOutputRowIndex = summaryRowIndex + 1;
                var yieldRowIndex = summaryRowIndex + 2;
                var packageCountRowIndex = summaryRowIndex + 3;

                assertTitleAndDate(softly, sheet, templateSheet);
                assertMaterialRows(softly, sheet, templateSheet, materialCount);
                assertSummaryAndProductionRows(
                        softly,
                        sheet,
                        summaryRowIndex,
                        referenceOutputRowIndex,
                        yieldRowIndex,
                        packageCountRowIndex
                );

                var packagingHeaderRowIndex = findPackagingHeaderRow(sheet);
                var packagingLastRowIndex = findPackagingLastRow(sheet, packagingHeaderRowIndex);
                var templatePackagingHeaderRowIndex = findPackagingHeaderRow(templateSheet);
                var templatePackagingLastRowIndex = findPackagingLastRow(templateSheet, templatePackagingHeaderRowIndex);
                softly.assertThat(packagingHeaderRowIndex)
                        .as("packaging header row")
                        .isGreaterThan(packageCountRowIndex);
                softly.assertThat(packagingLastRowIndex)
                        .as("packaging last row")
                        .isGreaterThanOrEqualTo(packagingHeaderRowIndex);
                var printAreaLastContentRowIndex = findPrintAreaLastContentRow(
                        softly,
                        sheet,
                        packagingLastRowIndex
                );
                assertDynamicSectionStyles(
                        softly,
                        sheet,
                        templateSheet,
                        summaryRowIndex,
                        templateSummaryRowIndex(templateSheet),
                        4,
                        SUMMARY_STYLE_COLUMNS,
                        "summary section"
                );
                assertPackagingStylesAndQuantities(
                        softly,
                        sheet,
                        templateSheet,
                        packagingHeaderRowIndex,
                        packagingLastRowIndex,
                        templatePackagingHeaderRowIndex,
                        templatePackagingLastRowIndex
                );
                if (packagingHeaderRowIndex >= 0) {
                    softly.assertThat(longestBlankRun(sheet, summaryRowIndex + 1, packagingHeaderRowIndex - 1))
                            .as("blank rows between summary and packaging")
                            .isLessThanOrEqualTo(4);
                }

                assertPrintLayout(softly, workbook, sheet, printAreaLastContentRowIndex);
            });
        } catch (IOException e) {
            throw new AssertionError("Unable to read pricing workbook template baseline", e);
        }
    }

    private static Workbook openTemplate() throws IOException {
        var stream = PricingFileService.class.getResourceAsStream(TEMPLATE_PATH);
        if (stream == null) {
            throw new IOException("Pricing template not found: " + TEMPLATE_PATH);
        }
        return WorkbookFactory.create(stream);
    }

    private static void assertTitleAndDate(SoftAssertions softly, Sheet sheet, Sheet templateSheet) {
        assertStyleMatchesTemplate(
                softly,
                sheet.getRow(TITLE_ROW).getCell(TITLE_COLUMN),
                templateSheet.getRow(TITLE_ROW).getCell(TITLE_COLUMN),
                "title cell"
        );
        softly.assertThat(textOf(sheet.getRow(HEADER_DATE_ROW).getCell(HEADER_DATE_COLUMN)))
                .as("header date")
                .matches(HEADER_DATE_FORMAT);
    }

    private static void assertMaterialRows(
            SoftAssertions softly,
            Sheet sheet,
            Sheet templateSheet,
            int materialCount
    ) {
        for (int index = 0; index < materialCount; index++) {
            var rowIndex = MATERIAL_START_ROW + index;
            var excelRow = rowIndex + 1;
            var row = sheet.getRow(rowIndex);
            var templateRow = templateSheet.getRow(rowIndex);

            softly.assertThat(row).as("material row %s", excelRow).isNotNull();
            if (row == null) {
                continue;
            }

            var sequence = row.getCell(SEQUENCE_COLUMN);
            softly.assertThat(sequence).as("material sequence at row %s", excelRow).isNotNull();
            if (sequence != null) {
                softly.assertThat(sequence.getNumericCellValue())
                        .as("material sequence at row %s", excelRow)
                        .isEqualTo(index + 1);
            }

            for (var columnIndex : MATERIAL_STYLE_COLUMNS) {
                if (columnIndex == 0 && index > 0) {
                    continue;
                }
                assertStyleMatchesTemplate(
                        softly,
                        row.getCell(columnIndex),
                        templateRow.getCell(columnIndex),
                        "material style at %s%s".formatted(columnName(columnIndex), excelRow)
                );
            }

            assertNumberFormat(
                    softly,
                    row.getCell(WEIGHT_COLUMN),
                    "0.000",
                    "material weight format at row %s".formatted(excelRow)
            );
            assertNumberFormat(
                    softly,
                    row.getCell(UTILIZATION_RATE_COLUMN),
                    "0.00%",
                    "utilization rate format at row %s".formatted(excelRow)
            );
        }
    }

    private static void assertSummaryAndProductionRows(
            SoftAssertions softly,
            Sheet sheet,
            int summaryRowIndex,
            int referenceOutputRowIndex,
            int yieldRowIndex,
            int packageCountRowIndex
    ) {
        var summaryRow = sheet.getRow(summaryRowIndex);
        softly.assertThat(summaryRow).as("summary row %s", summaryRowIndex + 1).isNotNull();
        if (summaryRow != null) {
            softly.assertThat(textOf(summaryRow.getCell(MATERIAL_CODE_COLUMN)))
                    .as("summary label")
                    .isEqualTo("总计");
            softly.assertThat(formulaOf(summaryRow.getCell(TOTAL_COLUMN)))
                    .as("summary total formula")
                    .isEqualTo("SUM(G%s:G%s)".formatted(MATERIAL_START_ROW + 1, summaryRowIndex));
        }

        assertLabeledNumericRow(softly, sheet, referenceOutputRowIndex, "研发部参考肥肠出成(kg）", null, "0.000");
        assertLabeledNumericRow(
                softly,
                sheet,
                yieldRowIndex,
                "肥肠得率（%）",
                "G%s/I%s".formatted(referenceOutputRowIndex + 1, MATERIAL_START_ROW + 1),
                "0.00%"
        );
        assertLabeledNumericRow(softly, sheet, packageCountRowIndex, "研发部参考包数", null, "0");
    }

    private static void assertDynamicSectionStyles(
            SoftAssertions softly,
            Sheet sheet,
            Sheet templateSheet,
            int actualStartRowIndex,
            int templateStartRowIndex,
            int rowCount,
            List<Integer> columnIndexes,
            String description
    ) {
        if (templateStartRowIndex < 0) {
            softly.fail("%s template baseline was not found", description);
            return;
        }
        for (int rowOffset = 0; rowOffset < rowCount; rowOffset++) {
            var actualRow = sheet.getRow(actualStartRowIndex + rowOffset);
            var templateRow = templateSheet.getRow(templateStartRowIndex + rowOffset);
            for (var columnIndex : columnIndexes) {
                assertStyleMatchesTemplate(
                        softly,
                        actualRow == null ? null : actualRow.getCell(columnIndex),
                        templateRow == null ? null : templateRow.getCell(columnIndex),
                        "%s style at %s%s".formatted(
                                description,
                                columnName(columnIndex),
                                actualStartRowIndex + rowOffset + 1
                        )
                );
            }
        }
    }

    private static void assertPackagingStylesAndQuantities(
            SoftAssertions softly,
            Sheet sheet,
            Sheet templateSheet,
            int packagingHeaderRowIndex,
            int packagingLastRowIndex,
            int templatePackagingHeaderRowIndex,
            int templatePackagingLastRowIndex
    ) {
        if (packagingHeaderRowIndex < 0 || templatePackagingHeaderRowIndex < 0) {
            return;
        }
        assertDynamicSectionStyles(
                softly,
                sheet,
                templateSheet,
                packagingHeaderRowIndex,
                templatePackagingHeaderRowIndex,
                1,
                PACKAGING_HEADER_STYLE_COLUMNS,
                "packaging header"
        );

        var actualRows = packagingLastRowIndex - packagingHeaderRowIndex;
        var templateRows = templatePackagingLastRowIndex - templatePackagingHeaderRowIndex;
        softly.assertThat(actualRows).as("packaging item count").isEqualTo(templateRows);
        var rowsToCompare = Math.min(actualRows, templateRows);
        for (int rowOffset = 1; rowOffset <= rowsToCompare; rowOffset++) {
            var actualRow = sheet.getRow(packagingHeaderRowIndex + rowOffset);
            var templateRow = templateSheet.getRow(templatePackagingHeaderRowIndex + rowOffset);
            for (var columnIndex : PACKAGING_ROW_STYLE_COLUMNS) {
                assertStyleMatchesTemplate(
                        softly,
                        actualRow == null ? null : actualRow.getCell(columnIndex),
                        templateRow == null ? null : templateRow.getCell(columnIndex),
                        "packaging style at %s%s".formatted(
                                columnName(columnIndex),
                                packagingHeaderRowIndex + rowOffset + 1
                        )
                );
            }
            assertNumberFormat(
                    softly,
                    actualRow == null ? null : actualRow.getCell(TOTAL_COLUMN),
                    "0",
                    "packaging quantity format at row %s".formatted(packagingHeaderRowIndex + rowOffset + 1)
            );
        }
    }

    private static void assertLabeledNumericRow(
            SoftAssertions softly,
            Sheet sheet,
            int rowIndex,
            String label,
            String formula,
            String numberFormat
    ) {
        var row = sheet.getRow(rowIndex);
        softly.assertThat(row).as("%s row", label).isNotNull();
        if (row == null) {
            return;
        }

        softly.assertThat(textOf(row.getCell(MATERIAL_CODE_COLUMN)))
                .as("%s label", label)
                .isEqualTo(label);
        if (formula != null) {
            softly.assertThat(formulaOf(row.getCell(TOTAL_COLUMN)))
                    .as("%s formula", label)
                    .isEqualTo(formula);
        }
        assertNumberFormat(softly, row.getCell(TOTAL_COLUMN), numberFormat, label + " format");
    }

    private static void assertPrintLayout(
            SoftAssertions softly,
            Workbook workbook,
            Sheet sheet,
            int printAreaLastContentRowIndex
    ) {
        softly.assertThat(sheet.getPrintSetup().getPaperSize())
                .as("print paper size")
                .isEqualTo(PrintSetup.A4_PAPERSIZE);
        softly.assertThat(sheet.getPrintSetup().getLandscape())
                .as("print orientation")
                .isFalse();
        softly.assertThat(sheet.getPrintSetup().getFitWidth())
                .as("print fit width")
                .isEqualTo((short) 1);
        softly.assertThat(sheet.getAutobreaks())
                .as("print automatic page breaks")
                .isTrue();
        softly.assertThat(sheet.getFitToPage())
                .as("print fit to page")
                .isTrue();
        softly.assertThat(sheet.getHorizontallyCenter())
                .as("print horizontally centered")
                .isTrue();
        softly.assertThat(sheet.isDisplayGridlines())
                .as("display gridlines")
                .isFalse();

        var printArea = workbook.getPrintArea(workbook.getSheetIndex(sheet));
        softly.assertThat(printArea).as("print area").isNotBlank();
        if (printArea == null || printArea.isBlank() || printAreaLastContentRowIndex < 0) {
            return;
        }

        var area = new AreaReference(printArea, SpreadsheetVersion.EXCEL2007);
        softly.assertThat(area.getFirstCell().getCol())
                .as("print area start column")
                .isEqualTo(PRINT_AREA_START_COLUMN);
        softly.assertThat(area.getFirstCell().getRow())
                .as("print area start row")
                .isEqualTo(PRINT_AREA_START_ROW);
        softly.assertThat(area.getLastCell().getRow())
                .as("print area end row")
                .isEqualTo(printAreaLastContentRowIndex);
    }

    private static void assertStyleMatchesTemplate(
            SoftAssertions softly,
            Cell actual,
            Cell expected,
            String description
    ) {
        softly.assertThat(actual).as(description + " cell").isNotNull();
        softly.assertThat(expected).as(description + " template cell").isNotNull();
        if (actual == null || expected == null) {
            return;
        }
        softly.assertThat(styleContract(actual.getCellStyle()))
                .as(description)
                .isEqualTo(styleContract(expected.getCellStyle()));
    }

    private static String styleContract(CellStyle style) {
        return "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s"
                .formatted(
                        canonicalNumberFormat(style.getDataFormatString()),
                        style.getFontIndexAsInt(),
                        style.getFillPattern(),
                        style.getFillForegroundColor(),
                        style.getFillBackgroundColor(),
                        style.getAlignment(),
                        style.getVerticalAlignment(),
                        style.getWrapText(),
                        style.getShrinkToFit(),
                        style.getRotation(),
                        style.getIndention(),
                        style.getLocked(),
                        style.getHidden(),
                        style.getBorderTop(),
                        style.getBorderRight(),
                        style.getBorderBottom(),
                        style.getBorderLeft(),
                        style.getTopBorderColor(),
                        style.getRightBorderColor(),
                        style.getBottomBorderColor(),
                        style.getLeftBorderColor()
                );
    }

    private static void assertNumberFormat(SoftAssertions softly, Cell cell, String expected, String description) {
        softly.assertThat(cell).as(description + " cell").isNotNull();
        if (cell != null) {
            softly.assertThat(canonicalNumberFormat(cell.getCellStyle().getDataFormatString()))
                    .as(description)
                    .isEqualTo(expected);
        }
    }

    private static int findPackagingHeaderRow(Sheet sheet) {
        var formatter = new DataFormatter();
        for (int rowIndex = MATERIAL_START_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (PACKAGING_MARKER.equals(formatter.formatCellValue(cell).trim())) {
                    return rowIndex;
                }
            }
        }
        return -1;
    }

    private static int templateSummaryRowIndex(Sheet templateSheet) {
        for (int rowIndex = MATERIAL_START_ROW; rowIndex <= templateSheet.getLastRowNum(); rowIndex++) {
            var row = templateSheet.getRow(rowIndex);
            if (row != null && "总计".equals(textOf(row.getCell(MATERIAL_CODE_COLUMN)))) {
                return rowIndex;
            }
        }
        return -1;
    }

    private static int findPackagingLastRow(Sheet sheet, int packagingHeaderRowIndex) {
        if (packagingHeaderRowIndex < 0) {
            return -1;
        }
        var lastRowIndex = packagingHeaderRowIndex;
        for (int rowIndex = packagingHeaderRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var sequence = sheet.getRow(rowIndex) == null ? null : sheet.getRow(rowIndex).getCell(SEQUENCE_COLUMN);
            if (sequence == null || sequence.getCellType() != CellType.NUMERIC) {
                return lastRowIndex;
            }
            lastRowIndex = rowIndex;
        }
        return lastRowIndex;
    }

    private static int findPrintAreaLastContentRow(
            SoftAssertions softly,
            Sheet sheet,
            int packagingLastRowIndex
    ) {
        var confidentialityRowIndex = findRowContaining(sheet, CONFIDENTIALITY_MARKER);
        softly.assertThat(confidentialityRowIndex)
                .as("confidentiality statement row")
                .isGreaterThan(packagingLastRowIndex);
        if (confidentialityRowIndex < 0) {
            return packagingLastRowIndex;
        }

        softly.assertThat(lastContentRowIndex(sheet))
                .as("last content row")
                .isEqualTo(confidentialityRowIndex);
        return confidentialityRowIndex;
    }

    private static int findRowContaining(Sheet sheet, String expectedText) {
        var formatter = new DataFormatter();
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (expectedText.equals(formatter.formatCellValue(cell).trim())) {
                    return rowIndex;
                }
            }
        }
        return -1;
    }

    private static int lastContentRowIndex(Sheet sheet) {
        for (int rowIndex = sheet.getLastRowNum(); rowIndex >= 0; rowIndex--) {
            if (!isBlank(sheet.getRow(rowIndex))) {
                return rowIndex;
            }
        }
        return -1;
    }

    private static int longestBlankRun(Sheet sheet, int firstRow, int lastRow) {
        var longestRun = 0;
        var currentRun = 0;
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            if (isBlank(sheet.getRow(rowIndex))) {
                currentRun++;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        return longestRun;
    }

    private static boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (!textOf(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String formulaOf(Cell cell) {
        return cell != null && cell.getCellType() == CellType.FORMULA ? cell.getCellFormula() : null;
    }

    private static String textOf(Cell cell) {
        return cell == null ? "" : new DataFormatter().formatCellValue(cell).trim();
    }

    private static String canonicalNumberFormat(String format) {
        return format.replace("_ ", "").trim();
    }

    private static String columnName(int columnIndex) {
        return String.valueOf((char) ('A' + columnIndex));
    }
}
