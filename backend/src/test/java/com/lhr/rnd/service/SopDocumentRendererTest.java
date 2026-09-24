package com.lhr.rnd.service;

import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.model.ProcessPlan;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SopDocumentRendererTest {
    @Test void inheritedObservationsAreExplicitAndQualitativeRequirementsNeedNoNumericTarget() throws Exception {
        var base = view(true);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.valueToTree(base.snapshot());
        var control = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("majorProcesses").get(1).path("steps").get(0).path("controlPoints").get(1);
        control.putNull("targetValue"); control.putNull("upperLimit");
        control.put("basisOrRemark", "外观均匀，无可见异物（软件测试定性要求）");
        var plan = mapper.treeToValue(tree, ProcessPlan.class);
        var inherited = new ProcessExportCheckService().view(base.productName(), base.sourceLabel(), plan, null, List.of())
                .withMetadata(base.metadata()).withActualsProvenance(true, "TRIAL-SOURCE-INTERNAL");
        var bytes = new SopDocumentRenderer().render(inherited, new SopDocumentRenderer.Metadata("R3", "V3", null, "研发张三", LocalDateTime.of(2026, 9, 22, 10, 0), false));
        var file = Path.of("target", "final-fix-export-qa", "inherited-qualitative-numeric-sop.docx");
        Files.createDirectories(file.getParent()); Files.write(file, bytes);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var text = document.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n")) + document.getTables().stream().map(t -> t.getText()).collect(Collectors.joining("\n"));
            assertThat(text).contains("含继承实测", "不表示本次新观测", "方案记录实际重量", "定性要求/依据：外观均匀", "目标：78℃", "复测结果：符合");
            assertThat(text).doesNotContain("TRIAL-SOURCE-INTERNAL", "本次实验实际重量", "目标或范围：待填写");
        }
    }
    @Test
    void rendersVerticalChineseSopWithNearbyControlsAndSeparateTraceAppendix() throws Exception {
        var view = view(true);
        var metadata = new SopDocumentRenderer.Metadata("R3", "V2", "调整熟制时间", "研发张三",
                LocalDateTime.of(2026, 8, 20, 9, 10, 11), false);

        var bytes = new SopDocumentRenderer().render(view, metadata);
        var output = Path.of("target", "task-5-export-qa", "two-major-control-heavy-rnd-sop-v4.docx");
        Files.createDirectories(output.getParent());
        Files.write(output, bytes);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var paragraphs = document.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n"));
            var tables = document.getTables();
            var allTables = tables.stream().map(t -> t.getText()).collect(Collectors.joining("\n"));
            var content = paragraphs + "\n" + allTables;

            assertThat(content).contains("研发 SOP", "本次实验实际记录", "正式工艺修订：R3", "试验方案 长名方案 V7",
                    "本文件记录研发试验事实与已记录的控制要求，不代表生产批准或食品工艺验证。",
                    "1.1 预处理与复合调味", "1.2 慢火熟制", "2.1 冷却与包装",
                    "1.2 控制要求", "2.1 控制要求", "测量记录追溯附录");
            assertThat(allTables).contains("实际外部投料", "本次实验实际参数", "操作要求", "产出", "控制要求",
                    "特级超长名称冷冻牛腩主料", "复合香辛料超长添加物名称", "主料", "辅料",
                    "承接 1.1 预处理与复合调味", "流向 1.2 慢火熟制",
                    "承接 1.2 慢火熟制", "流向 2.1 冷却与包装", "工艺用水超长名称",
                    "中心温度", "数字探针检测", "每锅检测", "继续加热并复测",
                    "目标：78℃", "允许范围：75 至 82℃", "目标：0℃", "允许范围：无下限 至 0℃",
                    "79.9", "2026-08-19 22:00:22", "符合", "复测结果：符合", "实测值：待填写", "实测值：0℃");
            assertThat(allTables).doesNotContain("实测值：℃");
            assertThat(content).doesNotContain("OUT-RAW-123", "MAT-RAW-456", "PRIMARY", "FROZEN_SOLID",
                    "FOOD_SAFETY", "CRITICAL", "PASS", "PACKED", "2026-08-19T22:00:22", ".123456");
            assertThat(allTables).doesNotContain("固定数据来源", "正式工艺修订");
            assertThat(paragraphs).contains("1.2 控制要求");
            var materialTable = tables.stream().filter(table -> table.getRow(0).getCell(0).getText().equals("来源")).findFirst().orElseThrow();
            assertThat(materialTable.getRow(0).getTableCells()).hasSize(4);
            var outputTable = tables.stream().filter(table -> table.getRow(0).getCell(0).getText().equals("产出名称")).findFirst().orElseThrow();
            assertThat(outputTable.getRow(0).getTableCells()).hasSize(4);
            assertThat(document.getDocument().xmlText()).contains("tblGrid", "type=\"dxa\"");
            var secondMajorSummary = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().startsWith("主料实际投入：80 kg"))
                    .findFirst().orElseThrow();
            assertThat(secondMajorSummary.getCTP().getPPr().isSetKeepNext()).isTrue();
            assertThat(tables).allSatisfy(table -> assertThat(table.getRow(0).isRepeatHeader()).isTrue());
            assertThat(document.getFooterList()).isNotEmpty();
            assertThat(document.getFooterList().get(0).getParagraphs().stream().map(p -> p.getCTP().xmlText()).collect(Collectors.joining()))
                    .contains("PAGE", "NUMPAGES");
        }
    }

    @Test
    void omitsEmptyOptionalRowsAndMeasurementAppendix() throws Exception {
        var view = view(false);
        var metadata = new SopDocumentRenderer.Metadata(null, null, null, "研发张三", null, true);

        try (var document = new XWPFDocument(new ByteArrayInputStream(new SopDocumentRenderer().render(view, metadata)))) {
            var text = document.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n"))
                    + document.getTables().stream().map(t -> t.getText()).collect(Collectors.joining("\n"));
            assertThat(text).contains("研发预览", "非正式归档").doesNotContain("测量记录追溯附录", "备注\t", "变更原因\t");
        }
    }

    private ProcessExportView view(boolean withMeasurement) {
        var measurement = new ProcessPlan.ControlMeasurement("CM-RAW", 1, new BigDecimal("79.9000"),
                "2026-08-19T22:00:22.123456", "PASS", null, "PASS", null);
        var control = new ProcessPlan.ControlPoint("CP-RAW", 1, "FOOD_SAFETY", "CRITICAL", "中心温度",
                new BigDecimal("78"), new BigDecimal("75"), new BigDecimal("82"), "℃", "数字探针检测",
                "校准温度探针", "每锅检测", "继续加热并复测", true, "研发李四",
                "2026-08-19T22:05:11.654321", "试验控制记录", withMeasurement ? List.of(measurement) : List.of());
        var first = new ProcessPlan.MinorStep("STEP-RAW-1", 1, "PREP", "预处理与复合调味", "NORMAL",
                "搅拌时间", "12.5", "min", "真空度", "-0.08", "MPa", "真空滚揉机",
                "按顺序加入外部原料并搅拌至均匀",
                List.of(
                        new ProcessPlan.StepMaterial("MAT-RAW-456", 1, "PRIMARY", "BEEF-001", "特级超长名称冷冻牛腩主料", "FROZEN_SOLID", new BigDecimal("100"), "F-BEEF", null, "EXTERNAL", null),
                        new ProcessPlan.StepMaterial("MAT-RAW-789", 2, "AUXILIARY", "SPICE-002", "复合香辛料超长添加物名称", "POWDER", new BigDecimal("1"), "F-SPICE", null, "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput("OUT-RAW-123", 1, "INTERMEDIATE", "调味牛腩", "MARINATED", new BigDecimal("100"), true, true, null)), List.of());
        var second = new ProcessPlan.MinorStep("STEP-RAW-2", 2, "COOK", "慢火熟制", "NORMAL",
                "本次实验实际中心温度", "79.9", "℃", null, null, null, "夹层锅", "加热至记录温度并按控制要求检查",
                List.of(new ProcessPlan.StepMaterial("MAT-STEP", 1, "PRIMARY", null, "调味牛腩", "MARINATED", new BigDecimal("100"), null, null, "STEP_OUTPUT", "OUT-RAW-123")),
                List.of(new ProcessPlan.StepOutput("OUT-FINAL", 1, "INTERMEDIATE", "熟制牛腩", "COOKED", new BigDecimal("80"), true, true, null)), List.of(control));
        var major = new ProcessPlan.MajorProcess("MAJOR-RAW", 1, "HEAT", "复合调味牛腩热加工序", null, "PRIMARY_INPUT", null,
                List.of(first, second), List.of(), List.of(), null);
        var packageMeasurement = new ProcessPlan.ControlMeasurement("CM-PACK", 1, new BigDecimal("18.5"),
                "2026-08-19T22:15:22.123456", "PASS", null, null, null);
        var missingPackageMeasurement = new ProcessPlan.ControlMeasurement("CM-PACK-MISSING", 2, null,
                "2026-08-19T22:16:22.123456", "PENDING", null, null, "本次未记录实测值");
        var zeroPackageMeasurement = new ProcessPlan.ControlMeasurement("CM-PACK-ZERO", 3, BigDecimal.ZERO,
                "2026-08-19T22:17:22.123456", "PASS", null, null, "真实零值记录");
        var packageControl = new ProcessPlan.ControlPoint("CP-PACK", 1, "PROCESS", "IMPORTANT", "装袋前温度",
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("22"), "℃", "数字探针检测",
                "校准温度探针", "每批检测", "继续冷却并复测", true, "研发王五",
                "2026-08-19T22:20:11.654321", "本次研发记录",
                withMeasurement ? List.of(packageMeasurement, missingPackageMeasurement, zeroPackageMeasurement) : List.of());
        var zeroLimitControl = new ProcessPlan.ControlPoint("CP-ZERO", 2, "PROCESS", "NORMAL", "零值控制验证",
                BigDecimal.ZERO, null, BigDecimal.ZERO, "℃", "记录核对", null, "每批", "记录并核对",
                false, null, null, null, List.of());
        var third = new ProcessPlan.MinorStep("STEP-RAW-3", 1, "PACK", "冷却与包装", "NORMAL",
                "本次实验实际装袋温度", "18.5", "℃", null, null, null, "冷却台与封口机",
                "冷却至记录温度后装袋",
                List.of(
                        new ProcessPlan.StepMaterial("MAT-STEP-2", 1, "PRIMARY", null, "熟制牛腩", "COOKED", new BigDecimal("80"), null, null, "STEP_OUTPUT", "OUT-FINAL"),
                        new ProcessPlan.StepMaterial("MAT-WATER", 2, "AUXILIARY", "WATER-003", "工艺用水超长名称", "LIQUID", new BigDecimal("1"), "F-WATER", null, "EXTERNAL", null)),
                List.of(new ProcessPlan.StepOutput("OUT-PACKED", 1, "FINISHED", "包装熟制牛腩", "PACKED", new BigDecimal("72"), true, false, null)),
                List.of(packageControl, zeroLimitControl));
        var finishing = new ProcessPlan.MajorProcess("MAJOR-PACK", 2, "PACK", "冷却包装工序", null, "PRIMARY_INPUT", null,
                List.of(third), List.of(), List.of(), null);
        var plan = new ProcessPlan("PLAN-RAW", "FORM-RAW", 7, "DRAFT", List.of(major, finishing), new BigDecimal("72"), false);
        return new ProcessExportCheckService().view("超长产品名称复合调味牛腩样品", "试验方案 长名方案 V7", plan, null, List.of())
                .withMetadata(new ProcessExportView.Metadata("1 kg 袋装", "研发张三", "2026-08-20"));
    }
}
