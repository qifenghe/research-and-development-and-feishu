package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PricingBasisTest {
    @Test void excludedUnobservedPrimaryWeightsRenderPendingWhileObservedZeroStaysNumeric() throws Exception {
        for (var weight : new String[]{null, "0", "5"}) {
            var bytes = new PricingFileService().renderBasis(ProcessExportCheckServiceTest.view(ProcessExportCheckServiceTest.nonePlan(weight)), true);
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var row = book.getSheetAt(0).getRow(11);
                for (var col : List.of(5, 7)) {
                    if (weight == null) assertThat(new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(row.getCell(col))).isEmpty();
                    else assertThat(row.getCell(col).getNumericCellValue()).isZero(); // the external observation is explicitly zero; legacy major weights are separate.
                }
            }
        }
    }

    @Test void cleanBasisUsesFixedActualsAndIndependentPackingAndNoLegacyPriceMapping() throws Exception {
        var view = ProcessExportCheckServiceTest.view(ProcessExportCheckServiceTest.plan("72", "90", "PRIMARY_INPUT"))
                .withMetadata(new ProcessExportView.Metadata("1kg/袋", "软件测试研发", "2026-09-22"));
        var bytes = new PricingFileService().renderBasis(view, false);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var data = new org.apache.poi.ss.usermodel.DataFormatter();
            var text = new StringBuilder();
            for (var sheet : workbook) {
                for (var row : sheet) for (var cell : row) text.append(data.formatCellValue(cell,workbook.getCreationHelper().createFormulaEvaluator())).append('|');
                assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short)1);
                assertThat(sheet.getRepeatingRows()).isNotNull();
                assertThat(workbook.getPrintArea(workbook.getSheetIndex(sheet))).isNotBlank();
            }
            assertThat(text.toString()).contains("原材料清单", "102", "72", "74", "方案A V3", "个", "1kg", "编写人", "日期", "规格")
                    .doesNotContain("source=", "revisionId=", "成本", "单价", "毛利", "税", "100kg折算", "100kg归一化", "宁德时代", "162.6");
            assertThat(workbook.getSheetAt(0).getRow(11).getCell(5).getNumericCellValue()).isEqualTo(100);
            assertThat(workbook.getSheetAt(0).getRow(11).getCell(6).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
        }
        var path = Path.of("target/task-4-export-qa"); Files.createDirectories(path);
        Files.write(path.resolve("formal-basis-102kg-72percent-74bags.xlsx"), bytes);
    }
    @Test void previewLabelsMissingWithoutArtifactsAndFormalRenderBlocksMissingUnit() throws Exception {
        var view = new ProcessExportCheckService().view("软件测试", "方案 B V2", ProcessExportCheckServiceTest.plan(null, "90", "PRIMARY_INPUT"), null, List.of());
        assertThatThrownBy(() -> new PricingFileService().renderBasis(view, false)).hasMessageContaining("重量");
        var bytes = new PricingFileService().renderBasis(view, true);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var data = new org.apache.poi.ss.usermodel.DataFormatter(); var text = new StringBuilder();
            for (var sheet : book) for (var row : sheet) for (var cell : row) text.append(data.formatCellValue(cell));
            assertThat(text.toString()).contains("预览", "待补充", "包装物料", "方案 B V2");
        }
        var path = Path.of("target/task-4-export-qa"); Files.createDirectories(path);
        Files.write(path.resolve("trial-incomplete-preview.xlsx"), bytes);
    }

    @Test void longChineseNamesAndManyRowsHaveExplicitPaginationAndRepeatedHeaders() throws Exception {
        var original = ProcessExportCheckServiceTest.plan("72", "90", "PRIMARY_INPUT");
        var major = original.majorProcesses().get(1); var step = major.steps().get(0);
        var materials = new java.util.ArrayList<>(step.materials());
        for (int i = 0; i < 35; i++) materials.add(new ProcessPlan.StepMaterial("QA" + i, i + 3, "AUXILIARY", "QA-" + i,
                "软件测试专用长中文复合调味辅助物料第" + i + "项（不可用于真实生产）", "SOLID", new java.math.BigDecimal("0.25"), "QA-" + i, null, "EXTERNAL", null));
        var changed = new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(), step.stepType(), null, null, null, null, null, null, step.equipment(), step.instruction(), materials, step.outputs(), step.controlPoints());
        var changedMajor = new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(), null, major.yieldBasis(), null, List.of(changed), List.of(), List.of(), null);
        var view = ProcessExportCheckServiceTest.view(new ProcessPlan("P", "F", 1, "SUBMITTED", List.of(original.majorProcesses().get(0), changedMajor), null, false))
                .withMetadata(new ProcessExportView.Metadata("1kg/袋，10袋/箱", "软件测试研发", "2026-09-22"));
        var bytes = new PricingFileService().renderBasis(view, false);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isGreaterThan(40);
            assertThat(book.getSheetAt(0).getRepeatingRows().getLastRow()).isEqualTo(10);
            assertThat(book.getSheetAt(0).getRepeatingRows().getFirstRow()).isEqualTo(10);
            assertThat(book.getSheetAt(0).getRow(13).getHeightInPoints()).isGreaterThan(38);
            assertThat(book.getSheetAt(0).getFooter().getRight()).contains("&P", "&N");
        }
        var path = Path.of("target/task-4-export-qa"); Files.createDirectories(path);
        Files.write(path.resolve("long-chinese-37-ingredient-basis.xlsx"), bytes);
    }
}
