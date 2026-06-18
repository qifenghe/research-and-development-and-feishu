package com.lhr.rnd.service;

import com.lhr.rnd.model.SampleVersion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PricingFileService {
    public PricingFileResult generate(SampleVersion version, String pricingVersionNo) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("核价原料清单");
            fillHeader(sheet, version);
            fillMaterials(sheet, version);
            fillPackageReference(sheet, version);
            workbook.write(output);

            var pricingVersion = version.versionNo() + "-核价" + pricingVersionNo;
            var fileName = version.productName() + "-核价原料清单-" + version.versionNo() + "-" + pricingVersionNo + ".xlsx";
            return new PricingFileResult(fileName, pricingVersion, output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate pricing workbook", e);
        }
    }

    private void fillHeader(Sheet sheet, SampleVersion version) {
        cell(sheet, 0, 0, "研发核价原料清单");
        cell(sheet, 2, 2, "产品名称");
        cell(sheet, 2, 3, version.productName());
        cell(sheet, 3, 2, "产品类型");
        cell(sheet, 3, 3, version.productType());
        cell(sheet, 4, 2, "规格");
        cell(sheet, 4, 3, version.specification());
        cell(sheet, 5, 8, "负责人");
        cell(sheet, 5, 9, version.ownerName());
        cell(sheet, 6, 8, "版本号");
        cell(sheet, 6, 9, version.versionNo());
        cell(sheet, 7, 8, "编写人");
        cell(sheet, 7, 9, version.authorName());
        if (version.effectiveDate() != null) {
            cell(sheet, 8, 8, "实施日期");
            cell(sheet, 8, 9, version.effectiveDate().toString());
        }
    }

    private void fillMaterials(Sheet sheet, SampleVersion version) {
        cell(sheet, 11, 0, "阶段");
        cell(sheet, 11, 1, "序号");
        cell(sheet, 11, 2, "物料编码");
        cell(sheet, 11, 3, "物料名称");
        cell(sheet, 11, 7, "重量kg");
        cell(sheet, 11, 8, "领料重量kg");
        cell(sheet, 11, 9, "备注");

        var rowIndex = 12;
        for (var material : version.materials()) {
            var pickingWeight = material.weightKg().divide(material.utilizationRate(), 14, RoundingMode.HALF_UP);
            cell(sheet, rowIndex, 0, material.stage());
            numericCell(sheet, rowIndex, 1, material.sequence());
            cell(sheet, rowIndex, 2, material.materialCode());
            cell(sheet, rowIndex, 3, material.materialName());
            numericCell(sheet, rowIndex, 7, material.weightKg().doubleValue());
            numericCell(sheet, rowIndex, 8, pickingWeight.doubleValue());
            cell(sheet, rowIndex, 9, material.remark());
            rowIndex++;
        }
    }

    private void fillPackageReference(Sheet sheet, SampleVersion version) {
        if (version.referenceOutputKg() == null || version.unitWeightKg() == null) {
            return;
        }
        var packageCount = version.referenceOutputKg().divide(version.unitWeightKg(), 0, RoundingMode.DOWN);
        cell(sheet, 39, 5, "参考包数");
        numericCell(sheet, 39, 6, packageCount.doubleValue());
    }

    private void cell(Sheet sheet, int rowIndex, int cellIndex, String value) {
        row(sheet, rowIndex).createCell(cellIndex).setCellValue(value == null ? "" : value);
    }

    private void numericCell(Sheet sheet, int rowIndex, int cellIndex, double value) {
        row(sheet, rowIndex).createCell(cellIndex).setCellValue(value);
    }

    private Row row(Sheet sheet, int rowIndex) {
        var row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }
}
