package com.lhr.rnd.service;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.assertj.core.api.SoftAssertions;

final class PricingWorkbookAssertions {
    private static final int MATERIAL_START_ROW = 12;
    private static final int TITLE_ROW = 2;
    private static final int TITLE_COLUMN = 3;
    private static final int SEQUENCE_COLUMN = 2;
    private static final int MATERIAL_CODE_COLUMN = 3;
    private static final int UTILIZATION_RATE_COLUMN = 7;
    private static final int TOTAL_COLUMN = 6;

    private PricingWorkbookAssertions() {
    }

    static void assertFormalLayout(Workbook workbook, int materialCount) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(materialCount).as("material count").isPositive();

            var sheet = workbook.getSheetAt(0);
            var summaryRowIndex = MATERIAL_START_ROW + materialCount;
            assertTitleStyle(softly, sheet);
            assertMaterialRows(softly, sheet, materialCount);
            assertSummaryRow(softly, sheet, summaryRowIndex);

            var packagingHeaderRowIndex = findPackagingHeaderRow(sheet);
            softly.assertThat(packagingHeaderRowIndex)
                    .as("packaging header row")
                    .isGreaterThan(summaryRowIndex);
            if (packagingHeaderRowIndex >= 0) {
                softly.assertThat(longestBlankRun(sheet, summaryRowIndex + 1, packagingHeaderRowIndex - 1))
                        .as("blank rows between summary and packaging")
                        .isLessThanOrEqualTo(4);
            }

            assertPrintLayout(softly, workbook, sheet, packagingHeaderRowIndex);
        });
    }

    private static void assertTitleStyle(SoftAssertions softly, Sheet sheet) {
        var title = sheet.getRow(TITLE_ROW).getCell(TITLE_COLUMN);
        softly.assertThat(title.getCellStyle().getIndex())
                .as("title cell style")
                .isGreaterThan((short) 0);
    }

    private static void assertMaterialRows(SoftAssertions softly, Sheet sheet, int materialCount) {
        for (int index = 0; index < materialCount; index++) {
            var excelRow = MATERIAL_START_ROW + index + 1;
            var row = sheet.getRow(MATERIAL_START_ROW + index);

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

            var materialCode = row.getCell(MATERIAL_CODE_COLUMN);
            softly.assertThat(materialCode).as("material code at row %s", excelRow).isNotNull();
            if (materialCode != null) {
                softly.assertThat(hasVisibleBorder(materialCode))
                        .as("material border at row %s", excelRow)
                        .isTrue();
            }

            var utilizationRate = row.getCell(UTILIZATION_RATE_COLUMN);
            softly.assertThat(utilizationRate).as("utilization rate at row %s", excelRow).isNotNull();
            if (utilizationRate != null) {
                softly.assertThat(utilizationRate.getCellStyle().getDataFormatString())
                        .as("utilization rate format at row %s", excelRow)
                        .isEqualTo("0.00%");
            }
        }
    }

    private static void assertSummaryRow(SoftAssertions softly, Sheet sheet, int summaryRowIndex) {
        var summaryRow = sheet.getRow(summaryRowIndex);
        var lastMaterialExcelRow = summaryRowIndex;

        softly.assertThat(summaryRow).as("summary row %s", summaryRowIndex + 1).isNotNull();
        if (summaryRow == null) {
            return;
        }

        softly.assertThat(textOf(summaryRow.getCell(MATERIAL_CODE_COLUMN)))
                .as("summary label")
                .isEqualTo("总计");
        softly.assertThat(formulaOf(summaryRow.getCell(TOTAL_COLUMN)))
                .as("summary total formula")
                .isEqualTo("SUM(G13:G%s)".formatted(lastMaterialExcelRow));
    }

    private static void assertPrintLayout(
            SoftAssertions softly,
            Workbook workbook,
            Sheet sheet,
            int packagingHeaderRowIndex
    ) {
        softly.assertThat(sheet.getPrintSetup().getFitWidth())
                .as("print fit width")
                .isEqualTo((short) 1);

        var printArea = workbook.getPrintArea(workbook.getSheetIndex(sheet));
        softly.assertThat(printArea).as("print area").isNotBlank();
        if (printArea == null || printArea.isBlank() || packagingHeaderRowIndex < 0) {
            return;
        }

        var area = new AreaReference(printArea, SpreadsheetVersion.EXCEL2007);
        softly.assertThat(area.getLastCell().getRow())
                .as("print area end row")
                .isGreaterThanOrEqualTo(packagingHeaderRowIndex);
    }

    private static int findPackagingHeaderRow(Sheet sheet) {
        var formatter = new DataFormatter();
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (formatter.formatCellValue(cell).trim().startsWith("包装物料")) {
                    return rowIndex;
                }
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

    private static boolean hasVisibleBorder(Cell cell) {
        var style = cell.getCellStyle();
        return style.getBorderTop() != BorderStyle.NONE
                || style.getBorderRight() != BorderStyle.NONE
                || style.getBorderBottom() != BorderStyle.NONE
                || style.getBorderLeft() != BorderStyle.NONE;
    }

    private static String formulaOf(Cell cell) {
        return cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                ? cell.getCellFormula()
                : null;
    }

    private static String textOf(Cell cell) {
        if (cell == null) {
            return "";
        }
        return new DataFormatter().formatCellValue(cell).trim();
    }
}
