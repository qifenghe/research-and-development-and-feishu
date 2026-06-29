package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.SampleVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PricingFileService {
    private static final String TEMPLATE_PATH = "/templates/pricing-material-list-template.xlsx";
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Pattern BAGS_PER_BOX = Pattern.compile("(\\d+)袋/箱");

    private static final int MATERIAL_START_ROW = 12;
    private static final int MATERIAL_END_ROW = 48;
    private static final int TOTAL_ROW = 49;
    private static final int REFERENCE_OUTPUT_ROW = 50;
    private static final int YIELD_RATE_ROW = 51;
    private static final int PACKAGE_COUNT_ROW = 52;
    private static final int PACKAGING_START_ROW = 57;

    public PricingFileResult generate(SampleVersion version, String pricingVersionNo) {
        return generate(version, pricingVersionNo, "LHYC");
    }

    public PricingFileResult generate(SampleVersion version, String pricingVersionNo, String customerName) {
        try (InputStream template = requireTemplate();
             var workbook = new XSSFWorkbook(template);
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            var customer = blankToDefault(customerName, "LHYC");
            var effectiveDate = version.effectiveDate() == null ? LocalDate.now() : version.effectiveDate();

            fillHeader(sheet, version, customer, effectiveDate);
            var materials = version.materials() == null ? List.<ExperimentMaterial>of() : version.materials();
            fillMaterials(sheet, materials);
            fillSummary(sheet, materials, version);
            fillPackagingQuantities(sheet, version);

            var sheetName = version.productName() + "-" + customer + "原料清单";
            workbook.setSheetName(0, truncateSheetName(sheetName));

            workbook.write(output);

            var pricingVersion = version.versionNo() + "-核价" + pricingVersionNo;
            var fileName = version.productName()
                    + "-" + customer
                    + "（核价）原料清单"
                    + version.versionNo()
                    + " "
                    + effectiveDate.format(FILE_DATE)
                    + ".xlsx";
            return new PricingFileResult(fileName, pricingVersion, output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate pricing workbook", e);
        }
    }

    private InputStream requireTemplate() {
        var stream = PricingFileService.class.getResourceAsStream(TEMPLATE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Pricing template not found: " + TEMPLATE_PATH);
        }
        return stream;
    }

    private void fillHeader(Sheet sheet, SampleVersion version, String customer, LocalDate effectiveDate) {
        cell(sheet, 2, 3, formatProductTitle(version.productName(), customer));
        cell(sheet, 4, 3, "产品负责人:" + blankToDefault(version.ownerName(), ""));
        cell(sheet, 4, 9, "规格：" + blankToDefault(version.specification(), ""));
        cell(sheet, 6, 3, blankToDefault(version.productType(), ""));
        cell(sheet, 6, 9, formatVersionLabel(version.versionNo()));
        cell(sheet, 7, 3, "LHRZP-03-YF-");
        cell(sheet, 7, 6, effectiveDate.format(HEADER_DATE));
        cell(sheet, 8, 3, blankToDefault(version.authorName(), ""));
        cell(sheet, 8, 6, "——");
    }

    private void fillMaterials(Sheet sheet, List<ExperimentMaterial> materials) {
        removeMergesInRange(sheet, MATERIAL_START_ROW, MATERIAL_END_ROW, 0, 5);
        removeMergesInRange(sheet, MATERIAL_START_ROW, MATERIAL_END_ROW, 9, 9);

        var styleRow = sheet.getRow(MATERIAL_START_ROW);
        for (int rowIndex = MATERIAL_START_ROW; rowIndex <= MATERIAL_END_ROW; rowIndex++) {
            clearMaterialRow(sheet, rowIndex);
        }

        for (int index = 0; index < materials.size() && index <= MATERIAL_END_ROW - MATERIAL_START_ROW; index++) {
            var material = materials.get(index);
            var rowIndex = MATERIAL_START_ROW + index;
            var row = row(sheet, rowIndex, styleRow);
            setSequence(row, 2, material.sequence());
            setText(row, 3, material.materialCode(), styleRow, 3);
            setText(row, 4, material.materialName(), styleRow, 4);
            setDecimal(row, 6, material.weightKg(), styleRow, 6);
            setDecimal(row, 7, material.utilizationRate(), styleRow, 7);
            setFormula(row, 8, "G" + (rowIndex + 1) + "/H" + (rowIndex + 1), styleRow, 8);
            setText(row, 9, material.remark(), styleRow, 9);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 4, 5));
        }

        applyStageMerges(sheet, materials);
    }

    private void applyStageMerges(Sheet sheet, List<ExperimentMaterial> materials) {
        if (materials.isEmpty()) {
            return;
        }
        int groupStart = 0;
        while (groupStart < materials.size()) {
            var stage = materials.get(groupStart).stage();
            int groupEnd = groupStart;
            while (groupEnd + 1 < materials.size()
                    && stage.equals(materials.get(groupEnd + 1).stage())) {
                groupEnd++;
            }
            int firstRow = MATERIAL_START_ROW + groupStart;
            int lastRow = MATERIAL_START_ROW + groupEnd;
            if (stage != null && !stage.isBlank()) {
                cell(sheet, firstRow, 0, stage);
            }
            if (lastRow > firstRow) {
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 0, 1));
            }
            groupStart = groupEnd + 1;
        }
    }

    private void fillSummary(Sheet sheet, List<ExperimentMaterial> materials, SampleVersion version) {
        int lastMaterialRow = materials.isEmpty()
                ? MATERIAL_START_ROW
                : Math.min(MATERIAL_START_ROW + materials.size() - 1, MATERIAL_END_ROW);
        int firstExcelRow = MATERIAL_START_ROW + 1;
        int lastExcelRow = lastMaterialRow + 1;

        numericCell(sheet, TOTAL_ROW, 2, materials.size() + 1);
        cell(sheet, TOTAL_ROW, 3, "总计");
        formula(sheet, TOTAL_ROW, 6, "SUM(G" + firstExcelRow + ":G" + lastExcelRow + ")");
        formula(sheet, TOTAL_ROW, 8, "SUM(I" + firstExcelRow + ":I" + lastExcelRow + ")");

        if (version.referenceOutputKg() != null) {
            numericCell(sheet, REFERENCE_OUTPUT_ROW, 6, version.referenceOutputKg().doubleValue());
        }
        if (!materials.isEmpty() && version.referenceOutputKg() != null) {
            formula(sheet, YIELD_RATE_ROW, 6, "G" + (REFERENCE_OUTPUT_ROW + 1) + "/I" + firstExcelRow);
        }
        if (version.referenceOutputKg() != null && version.unitWeightKg() != null) {
            var packageCount = version.referenceOutputKg()
                    .divide(version.unitWeightKg(), 0, RoundingMode.DOWN);
            numericCell(sheet, PACKAGE_COUNT_ROW, 6, packageCount.doubleValue());
        }
    }

    private void fillPackagingQuantities(Sheet sheet, SampleVersion version) {
        if (version.referenceOutputKg() == null || version.unitWeightKg() == null) {
            return;
        }
        var packageCount = version.referenceOutputKg()
                .divide(version.unitWeightKg(), 0, RoundingMode.DOWN)
                .intValue();
        var bagsPerBox = parseBagsPerBox(version.specification());
        var boxCount = bagsPerBox <= 0
                ? packageCount
                : BigDecimal.valueOf(packageCount)
                        .divide(BigDecimal.valueOf(bagsPerBox), 0, RoundingMode.CEILING)
                        .intValue();

        setPackagingQuantity(sheet, PACKAGING_START_ROW, packageCount);
        setPackagingQuantity(sheet, PACKAGING_START_ROW + 1, packageCount);
        setPackagingQuantity(sheet, PACKAGING_START_ROW + 2, boxCount);
        setPackagingQuantity(sheet, PACKAGING_START_ROW + 3, boxCount);
    }

    private void setPackagingQuantity(Sheet sheet, int rowIndex, int quantity) {
        var row = sheet.getRow(rowIndex);
        if (row == null) {
            return;
        }
        var cell = row.getCell(6);
        if (cell == null) {
            cell = row.createCell(6);
        }
        cell.setCellValue(quantity);
    }

    private int parseBagsPerBox(String specification) {
        if (specification == null || specification.isBlank()) {
            return 0;
        }
        var matcher = BAGS_PER_BOX.matcher(specification);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String formatProductTitle(String productName, String customer) {
        var name = blankToDefault(productName, "未命名产品");
        if (name.contains("（核价）")) {
            return name;
        }
        return name + "（核价）-" + customer;
    }

    private String formatVersionLabel(String versionNo) {
        if (versionNo == null || versionNo.isBlank()) {
            return "";
        }
        if (versionNo.length() == 2 && Character.isLetter(versionNo.charAt(0)) && Character.isDigit(versionNo.charAt(1))) {
            return versionNo.charAt(0) + "/" + versionNo.charAt(1);
        }
        return versionNo;
    }

    private String truncateSheetName(String sheetName) {
        return sheetName.length() <= 31 ? sheetName : sheetName.substring(0, 31);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void removeMergesInRange(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
            var range = sheet.getMergedRegion(index);
            if (range.getFirstRow() >= firstRow
                    && range.getLastRow() <= lastRow
                    && range.getFirstColumn() >= firstCol
                    && range.getLastColumn() <= lastCol) {
                sheet.removeMergedRegion(index);
            }
        }
    }

    private void clearMaterialRow(Sheet sheet, int rowIndex) {
        var row = row(sheet, rowIndex, null);
        for (int col = 0; col <= 9; col++) {
            var cell = row.getCell(col);
            if (cell != null) {
                row.removeCell(cell);
            }
        }
    }

    private Row row(Sheet sheet, int rowIndex, Row styleSource) {
        var row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        if (styleSource != null && styleSource.getHeight() > 0) {
            row.setHeight(styleSource.getHeight());
        }
        return row;
    }

    private void setSequence(Row row, int col, int value) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
    }

    private void setText(Row row, int col, String value, Row styleSource, int styleCol) {
        var cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        copyStyle(styleSource, styleCol, cell);
    }

    private void setDecimal(Row row, int col, BigDecimal value, Row styleSource, int styleCol) {
        var cell = row.createCell(col);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        copyStyle(styleSource, styleCol, cell);
    }

    private void setFormula(Row row, int col, String formula, Row styleSource, int styleCol) {
        var cell = row.createCell(col);
        cell.setCellFormula(formula);
        copyStyle(styleSource, styleCol, cell);
    }

    private void copyStyle(Row styleSource, int styleCol, Cell target) {
        if (styleSource == null) {
            return;
        }
        var sourceCell = styleSource.getCell(styleCol);
        if (sourceCell != null) {
            target.setCellStyle(sourceCell.getCellStyle());
        }
    }

    private void cell(Sheet sheet, int rowIndex, int cellIndex, String value) {
        row(sheet, rowIndex).createCell(cellIndex).setCellValue(value == null ? "" : value);
    }

    private void formula(Sheet sheet, int rowIndex, int cellIndex, String formula) {
        row(sheet, rowIndex).createCell(cellIndex).setCellFormula(formula);
    }

    private void numericCell(Sheet sheet, int rowIndex, int cellIndex, double value) {
        row(sheet, rowIndex).createCell(cellIndex).setCellValue(value);
    }

    private Row row(Sheet sheet, int rowIndex) {
        var row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }
}
