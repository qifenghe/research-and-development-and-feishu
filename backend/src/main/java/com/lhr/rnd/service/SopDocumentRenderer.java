package com.lhr.rnd.service;

import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.model.ProcessPlan;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Produces an operator-readable vertical R&D SOP from one fixed export view. */
public final class SopDocumentRenderer {
    private static final String FONT = ExportDisplayFormat.CJK_FONT;
    private static final String HEADER_FILL = "D9EAF7";
    private static final String BORDER = "D9D9D9";
    private static final int CONTENT_WIDTH_DXA = 9638;
    private final ProcessPlanCalculationService calculations = new ProcessPlanCalculationService();

    public record Metadata(String formalRevision, String documentVersion, String changeReason,
                           String compiledBy, LocalDateTime compiledAt, boolean preview) {}

    public byte[] render(ProcessExportView view, Metadata metadata) throws Exception {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            configurePage(document);
            title(document, view.productName() + " 研发 SOP");
            if (metadata.preview()) subtitle(document, "研发预览  非正式归档");
            paragraph(document, "本文件记录研发试验事实与已记录的控制要求，不代表生产批准或食品工艺验证。");
            metadataTable(document, view, metadata);
            summary(document, view);
            pendingItems(document, view);

            for (var major : sorted(view.snapshot().majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
                major(document, view, major);
            }
            appendix(document, view.snapshot(), view.actualPrefix());
            footer(document, view);
            document.write(output);
            return output.toByteArray();
        }
    }

    private void configurePage(XWPFDocument document) {
        var section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
        var size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(java.math.BigInteger.valueOf(11906));
        size.setH(java.math.BigInteger.valueOf(16838));
        var margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(java.math.BigInteger.valueOf(1134));
        margins.setBottom(java.math.BigInteger.valueOf(1134));
        margins.setLeft(java.math.BigInteger.valueOf(1134));
        margins.setRight(java.math.BigInteger.valueOf(1134));
        margins.setHeader(java.math.BigInteger.valueOf(567));
        margins.setFooter(java.math.BigInteger.valueOf(567));
    }

    private void metadataTable(XWPFDocument document, ProcessExportView view, Metadata metadata) {
        var first = "产品：" + view.productName();
        if (view.metadata() != null && !ExportDisplayFormat.blank(view.metadata().specification())) first += "  规格：" + view.metadata().specification();
        paragraph(document, first);
        paragraph(document, "固定数据来源：" + view.sourceLabel());
        var revision = new ArrayList<String>();
        if (!ExportDisplayFormat.blank(metadata.formalRevision())) revision.add("正式工艺修订：" + metadata.formalRevision());
        if (!ExportDisplayFormat.blank(metadata.documentVersion())) revision.add("文件版本：" + metadata.documentVersion());
        if (!ExportDisplayFormat.blank(metadata.compiledBy())) revision.add("编制人：" + metadata.compiledBy());
        var date = metadata.compiledAt() == null ? view.metadata() == null ? "" : view.metadata().date()
                : ExportDisplayFormat.dateTime(metadata.compiledAt().toString());
        if (!ExportDisplayFormat.blank(date)) revision.add("编制时间：" + date);
        if (!revision.isEmpty()) paragraph(document, String.join("  ", revision));
        if (!ExportDisplayFormat.blank(metadata.changeReason())) paragraph(document, "变更原因：" + metadata.changeReason());
    }

    private void summary(XWPFDocument document, ProcessExportView view) {
        heading(document, view.actualPrefix() + "实际记录", 1);
        paragraph(document, "外部原料实际总投入：" + ExportDisplayFormat.actualKg(view.externalInputKg())
                + "  主流程实际得率：" + ExportDisplayFormat.percent(view.mainYieldPercent()));
        paragraph(document, "口径说明：" + view.actualProvenanceLabel() + "。");
    }

    private void pendingItems(XWPFDocument document, ProcessExportView view) {
        if (view.issues() == null || view.issues().isEmpty()) return;
        heading(document, "待核实项", 1);
        for (var issue : view.issues()) paragraph(document, "待核实：" + issue.message());
    }

    private void major(XWPFDocument document, ProcessExportView view, ProcessPlan.MajorProcess major) {
        var paragraphStart = document.getParagraphs().size();
        heading(document, "大工序 " + major.sequence() + " " + ExportDisplayFormat.required(major.processName()), 1);
        if (!ExportDisplayFormat.blank(major.description())) paragraph(document, "工序说明：" + major.description());
        if (!ExportDisplayFormat.blank(major.remark())) paragraph(document, "备注：" + major.remark());
        var measured = view.majors().stream().filter(item -> item.sequence() == major.sequence()).findFirst().orElse(null);
        if (measured != null) {
            paragraph(document, "主料实际投入：" + ExportDisplayFormat.actualKg(measured.primaryInputKg())
                    + "  主料实际产出：" + ExportDisplayFormat.actualKg(measured.primaryOutputKg())
                    + "  " + view.actualPrefix() + "大工序得率：" + ExportDisplayFormat.percent(measured.mainYieldPercent()));
        }

        var steps = sorted(major.steps(), ProcessPlan.MinorStep::sequence);
        if (!steps.isEmpty()) {
            for (int i = paragraphStart; i < document.getParagraphs().size(); i++) keepNext(document.getParagraphs().get(i));
        }
        for (var step : steps) step(document, view.snapshot(), major, step, view.actualPrefix());
    }

    private void step(XWPFDocument document, ProcessPlan plan, ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step, String actualPrefix) {
        var paragraphStart = document.getParagraphs().size();
        var tableStart = document.getTables().size();
        heading(document, major.sequence() + "." + step.sequence() + " " + ExportDisplayFormat.required(step.stepName()), 2);
        materials(document, plan, step, actualPrefix);
        operations(document, step, actualPrefix);
        outputs(document, plan, step, actualPrefix);
        controls(document, major, step);
        keepStepTogether(document, paragraphStart, tableStart);
    }

    private void materials(XWPFDocument document, ProcessPlan plan, ProcessPlan.MinorStep step, String actualPrefix) {
        var materials = sorted(step.materials(), ProcessPlan.StepMaterial::sequence);
        if (materials.isEmpty()) return;
        sectionLabel(document, "投料与前序承接");
        var remarks = materials.stream().anyMatch(material -> !ExportDisplayFormat.blank(material.remark()));
        var widths = remarks ? new int[]{14, 34, 21, 18, 13} : new int[]{15, 38, 24, 23};
        var table = table(document, remarks
                        ? new String[]{"来源", "名称与编码", "角色与状态", actualPrefix + "实际重量", "备注"}
                        : new String[]{"来源", "名称与编码", "角色与状态", actualPrefix + "实际重量"}, widths);
        for (var material : materials) {
            var row = createRow(table, widths);
            cell(row, 0, "EXTERNAL".equals(material.sourceType()) ? "实际外部投料" : producerLabel(plan, material.sourceStepOutputId()));
            cell(row, 1, join("\n", material.materialName(), material.materialCode()));
            cell(row, 2, join(" / ", ExportDisplayFormat.materialRole(material.materialRole()), ExportDisplayFormat.materialState(material.materialState())));
            cell(row, 3, ExportDisplayFormat.actualKg(material.weightKg()));
            if (remarks) cell(row, 4, ExportDisplayFormat.optional(material.remark()));
        }
    }

    private void operations(XWPFDocument document, ProcessPlan.MinorStep step, String actualPrefix) {
        sectionLabel(document, "操作、设备与" + actualPrefix + "实际参数");
        var rows = new ArrayList<String[]>();
        if (!ExportDisplayFormat.blank(step.equipment())) rows.add(pair("设备工具", step.equipment()));
        var parameters = join("；", parameter(step.parameter1Name(), step.parameter1Value(), step.parameter1Unit()),
                parameter(step.parameter2Name(), step.parameter2Value(), step.parameter2Unit()));
        if (!ExportDisplayFormat.blank(parameters)) rows.add(pair(actualPrefix + "实际参数", parameters));
        rows.add(pair("操作要求", ExportDisplayFormat.required(step.instruction())));
        rows.add(pair(actualPrefix + "步骤得率", ExportDisplayFormat.percent(calculations.calculateStep(step).mainYieldPercent())));
        fieldTable(document, rows);
    }

    private void outputs(XWPFDocument document, ProcessPlan plan, ProcessPlan.MinorStep step, String actualPrefix) {
        var outputs = sorted(step.outputs(), ProcessPlan.StepOutput::sequence);
        if (outputs.isEmpty()) return;
        sectionLabel(document, "产出");
        var remarks = outputs.stream().anyMatch(output -> !ExportDisplayFormat.blank(output.remark()));
        var widths = remarks ? new int[]{29, 22, 18, 18, 13} : new int[]{34, 24, 20, 22};
        var table = table(document, remarks
                        ? new String[]{"产出名称", "类型与状态", actualPrefix + "实际重量", "后续流转", "备注"}
                        : new String[]{"产出名称", "类型与状态", actualPrefix + "实际重量", "后续流转"}, widths);
        for (var output : outputs) {
            var row = createRow(table, widths);
            cell(row, 0, ExportDisplayFormat.required(output.outputName()));
            cell(row, 1, join(" / ", ExportDisplayFormat.outputType(output.outputType()), ExportDisplayFormat.materialState(output.materialState())));
            cell(row, 2, ExportDisplayFormat.actualKg(output.weightKg()));
            cell(row, 3, consumerLabel(plan, output));
            if (remarks) cell(row, 4, ExportDisplayFormat.optional(output.remark()));
        }
    }

    private void controls(XWPFDocument document, ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step) {
        var controls = sorted(step.controlPoints(), ProcessPlan.ControlPoint::sequence);
        if (controls.isEmpty()) return;
        sectionLabel(document, major.sequence() + "." + step.sequence() + " 控制要求");
        var widths = new int[]{25, 75};
        var table = table(document, new String[]{"控制项目", "要求与处置"}, widths);
        for (var control : controls) {
            var row = createRow(table, widths);
            cell(row, 0, ExportDisplayFormat.required(control.itemName()));
            var details = new ArrayList<String>();
            details.add("类型：" + ExportDisplayFormat.controlType(control.controlType()));
            details.add("重要性：" + ExportDisplayFormat.importance(control.importance()));
            details.add(controlRange(control));
            details.add("检测方法：" + ExportDisplayFormat.required(control.method()));
            if (!ExportDisplayFormat.blank(control.measurementTool())) details.add("工具：" + control.measurementTool());
            details.add("频次：" + ExportDisplayFormat.required(control.frequency()));
            details.add("偏差处理：" + ExportDisplayFormat.required(control.deviationAction()));
            if (!ExportDisplayFormat.blank(control.basisOrRemark())) details.add("依据或备注：" + control.basisOrRemark());
            cell(row, 1, String.join("；", details));
        }
    }

    private void appendix(XWPFDocument document, ProcessPlan plan, String actualPrefix) {
        var records = new ArrayList<String[]>();
        for (var major : sorted(plan.majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
            for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) {
                for (var control : sorted(step.controlPoints(), ProcessPlan.ControlPoint::sequence)) {
                    for (var measurement : sorted(control.measurements(), ProcessPlan.ControlMeasurement::sequence)) {
                        var details = new ArrayList<String>();
                        details.add("实测值：" + (measurement.measuredValue() == null
                                ? "待填写"
                                : ExportDisplayFormat.number(measurement.measuredValue()) + ExportDisplayFormat.optional(control.unit())));
                        details.add("测量时间：" + ExportDisplayFormat.required(ExportDisplayFormat.dateTime(measurement.measuredAt())));
                        details.add("结果：" + ExportDisplayFormat.measurementResult(measurement.result()));
                        if (!ExportDisplayFormat.blank(control.confirmedBy())) details.add("确认人：" + control.confirmedBy());
                        if (!ExportDisplayFormat.blank(control.confirmedAt())) details.add("确认时间：" + ExportDisplayFormat.dateTime(control.confirmedAt()));
                        if (!ExportDisplayFormat.blank(measurement.deviationAction())) details.add("实际偏差处理：" + measurement.deviationAction());
                        if (!ExportDisplayFormat.blank(measurement.retestResult())) details.add("复测结果：" + (List.of("PASS", "FAIL", "PENDING").contains(measurement.retestResult()) ? ExportDisplayFormat.measurementResult(measurement.retestResult()) : measurement.retestResult()));
                        if (!ExportDisplayFormat.blank(measurement.remark())) details.add("备注：" + measurement.remark());
                        records.add(new String[]{major.sequence() + "." + step.sequence() + " " + step.stepName() + "\n" + control.itemName(), String.join("；", details)});
                    }
                }
            }
        }
        if (records.isEmpty()) return;
        heading(document, "测量记录追溯附录", 1);
        paragraph(document, "附录为" + actualPrefix + "实测与审计记录，不作为生产指令标准。");
        var widths = new int[]{25, 75};
        var table = table(document, new String[]{"记录位置", "实测与追溯信息"}, widths);
        for (var record : records) {
            var row = createRow(table, widths); cell(row, 0, record[0]); cell(row, 1, record[1]);
        }
    }

    private void footer(XWPFDocument document, ProcessExportView view) {
        var footer = document.createFooter(HeaderFooterType.DEFAULT);
        var paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run(paragraph, view.productName() + "  " + view.sourceLabel() + "  第 ", 8, false);
        field(paragraph, "PAGE");
        run(paragraph, " 页 / 共 ", 8, false);
        field(paragraph, "NUMPAGES");
        run(paragraph, " 页", 8, false);
    }

    private void title(XWPFDocument document, String text) {
        var paragraph = document.createParagraph(); paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(160); run(paragraph, text, 18, true);
    }
    private void subtitle(XWPFDocument document, String text) {
        var paragraph = document.createParagraph(); paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(160); run(paragraph, text, 11, true);
    }
    private void heading(XWPFDocument document, String text, int level) {
        var paragraph = document.createParagraph(); keepNext(paragraph);
        paragraph.setSpacingBefore(level == 1 ? 220 : 160); paragraph.setSpacingAfter(80);
        run(paragraph, text, level == 1 ? 14 : 12, true);
    }
    private void sectionLabel(XWPFDocument document, String text) {
        var paragraph = document.createParagraph(); keepNext(paragraph); paragraph.setSpacingBefore(100); paragraph.setSpacingAfter(50);
        run(paragraph, text, 10.5, true);
    }
    private void paragraph(XWPFDocument document, String text) {
        var paragraph = document.createParagraph(); paragraph.setSpacingAfter(90); paragraph.setSpacingBetween(1.15);
        run(paragraph, text, 10.5, false);
    }

    private void fieldTable(XWPFDocument document, List<String[]> rows) {
        if (rows.isEmpty()) return;
        var widths = new int[]{25, 75};
        var table = table(document, new String[]{"字段", "内容"}, widths);
        for (var pair : rows) { var row = createRow(table, widths); cell(row, 0, pair[0]); cell(row, 1, pair[1]); }
    }

    private XWPFTable table(XWPFDocument document, String[] headers, int[] widths) {
        var table = document.createTable(1, headers.length);
        table.setWidth(Integer.toString(CONTENT_WIDTH_DXA));
        table.setTableAlignment(TableRowAlign.CENTER);
        var grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        for (int width : widths) grid.addNewGridCol().setW(java.math.BigInteger.valueOf(twips(width)));
        var borders = table.getCTTbl().getTblPr().isSetTblBorders() ? table.getCTTbl().getTblPr().getTblBorders() : table.getCTTbl().getTblPr().addNewTblBorders();
        for (var border : List.of(borders.isSetTop() ? borders.getTop() : borders.addNewTop(), borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(),
                borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(), borders.isSetRight() ? borders.getRight() : borders.addNewRight(),
                borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH(), borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE); border.setColor(BORDER); border.setSz(java.math.BigInteger.valueOf(4));
        }
        var header = table.getRow(0); header.setRepeatHeader(true); header.setCantSplitRow(true);
        applyWidths(header, widths);
        for (int i = 0; i < headers.length; i++) {
            cell(header, i, headers[i]); header.getCell(i).setColor(HEADER_FILL);
            header.getCell(i).getParagraphs().forEach(p -> p.getRuns().forEach(r -> r.setBold(true)));
        }
        return table;
    }

    private XWPFTableRow createRow(XWPFTable table, int[] widths) {
        var row = table.createRow(); applyWidths(row, widths); return row;
    }
    private void applyWidths(XWPFTableRow row, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            var cell = row.getCell(i); var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            var cellWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW(); cellWidth.setType(STTblWidth.DXA); cellWidth.setW(java.math.BigInteger.valueOf(twips(widths[i])));
        }
    }
    private int twips(int percent) { return Math.round(CONTENT_WIDTH_DXA * percent / 100f); }

    private void cell(XWPFTableRow row, int index, String text) {
        row.setCantSplitRow(true);
        var cell = row.getCell(index); cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        while (cell.getParagraphs().size() > 1) cell.removeParagraph(1);
        var paragraph = cell.getParagraphs().get(0); paragraph.setSpacingAfter(0); paragraph.setSpacingBetween(1.05);
        run(paragraph, text == null ? "" : text, 9.5, false);
        var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        var margins = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        setMargin(margins.isSetTop() ? margins.getTop() : margins.addNewTop(), 90);
        setMargin(margins.isSetBottom() ? margins.getBottom() : margins.addNewBottom(), 90);
        setMargin(margins.isSetLeft() ? margins.getLeft() : margins.addNewLeft(), 100);
        setMargin(margins.isSetRight() ? margins.getRight() : margins.addNewRight(), 100);
    }

    private void setMargin(CTTblWidth margin, long width) { margin.setType(STTblWidth.DXA); margin.setW(java.math.BigInteger.valueOf(width)); }
    private XWPFRun run(XWPFParagraph paragraph, String text, double size, boolean bold) {
        var run = paragraph.createRun(); run.setText(text == null ? "" : text); run.setFontFamily(FONT); run.setFontSize(size); run.setBold(bold); run.setColor("000000");
        var fonts = run.getCTR().isSetRPr() && run.getCTR().getRPr().sizeOfRFontsArray() > 0 ? run.getCTR().getRPr().getRFontsArray(0) : run.getCTR().getRPr().addNewRFonts();
        fonts.setEastAsia(FONT); fonts.setAscii(FONT); fonts.setHAnsi(FONT);
        return run;
    }
    private void field(XWPFParagraph paragraph, String instruction) {
        var begin = paragraph.createRun(); begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        var code = paragraph.createRun(); var text = code.getCTR().addNewInstrText(); text.setStringValue(instruction); text.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
        var separate = paragraph.createRun(); separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        run(paragraph, "1", 8, false);
        var end = paragraph.createRun(); end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
    }

    private void keepNext(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr()) paragraph.getCTP().addNewPPr();
        paragraph.setKeepNext(true);
    }

    private void keepStepTogether(XWPFDocument document, int paragraphStart, int tableStart) {
        for (int i = paragraphStart; i < document.getParagraphs().size(); i++) keepNext(document.getParagraphs().get(i));
        for (int i = tableStart; i < document.getTables().size(); i++) {
            for (var row : document.getTables().get(i).getRows()) for (var cell : row.getTableCells()) for (var paragraph : cell.getParagraphs()) keepNext(paragraph);
        }
        if (document.getTables().size() > tableStart) {
            var table = document.getTables().get(document.getTables().size() - 1);
            var row = table.getRow(table.getNumberOfRows() - 1);
            for (var cell : row.getTableCells()) for (var paragraph : cell.getParagraphs()) {
                if (!paragraph.getCTP().isSetPPr()) paragraph.getCTP().addNewPPr();
                paragraph.setKeepNext(false);
            }
        } else if (document.getParagraphs().size() > paragraphStart) {
            document.getParagraphs().get(document.getParagraphs().size() - 1).setKeepNext(false);
        }
    }

    private String producerLabel(ProcessPlan plan, String outputId) {
        if (ExportDisplayFormat.blank(outputId)) return "承接前序产出（来源待核实）";
        for (var major : sorted(plan.majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
            for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) {
                for (var output : sorted(step.outputs(), ProcessPlan.StepOutput::sequence)) {
                    if (outputId.equals(output.id())) return "承接 " + major.sequence() + "." + step.sequence() + " " + ExportDisplayFormat.required(step.stepName());
                }
            }
        }
        return "承接前序产出（来源待核实）";
    }

    private String consumerLabel(ProcessPlan plan, ProcessPlan.StepOutput output) {
        var consumers = new ArrayList<String>();
        if (!ExportDisplayFormat.blank(output.id())) {
            for (var major : sorted(plan.majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
                for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) {
                    if (sorted(step.materials(), ProcessPlan.StepMaterial::sequence).stream().anyMatch(material -> output.id().equals(material.sourceStepOutputId()))) {
                        consumers.add(major.sequence() + "." + step.sequence() + " " + ExportDisplayFormat.required(step.stepName()));
                    }
                }
            }
        }
        if (!consumers.isEmpty()) return "流向 " + String.join("、", consumers);
        return output.continueFlow() ? "后续步骤待核实" : "未记录后续流转";
    }

    private String controlRange(ProcessPlan.ControlPoint control) {
        var values = new ArrayList<String>();
        if (control.targetValue() != null) {
            values.add("目标：" + ExportDisplayFormat.number(control.targetValue()) + ExportDisplayFormat.optional(control.unit()));
        }
        if (control.lowerLimit() != null || control.upperLimit() != null) {
            values.add("允许范围：" + (control.lowerLimit() == null ? "无下限" : ExportDisplayFormat.number(control.lowerLimit())) + " 至 "
                    + (control.upperLimit() == null ? "无上限" : ExportDisplayFormat.number(control.upperLimit())) + ExportDisplayFormat.optional(control.unit()));
        }
        return values.isEmpty() ? (ExportDisplayFormat.blank(control.basisOrRemark()) ? "目标或范围：待填写" : "定性要求/依据：" + control.basisOrRemark()) : String.join("；", values);
    }
    private String parameter(String name, String value, String unit) {
        return ExportDisplayFormat.blank(name) ? "" : name.trim() + " " + ExportDisplayFormat.required(value) + ExportDisplayFormat.optional(unit);
    }
    private String join(String separator, String... values) {
        return java.util.Arrays.stream(values).filter(value -> !ExportDisplayFormat.blank(value)).collect(Collectors.joining(separator));
    }
    private String[] pair(String label, String value) { return new String[]{label, value}; }
    private <T> List<T> sorted(List<T> values, ToIntFunction<T> sequence) {
        return values == null ? List.of() : values.stream().sorted(Comparator.comparingInt(sequence)).toList();
    }
}
