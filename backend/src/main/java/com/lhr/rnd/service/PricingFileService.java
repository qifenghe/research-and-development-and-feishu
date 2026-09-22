package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.PricingPackagingItem;
import com.lhr.rnd.model.ProcessRevision;
import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.model.SampleVersion;
import com.lhr.rnd.model.YieldCalculationMode;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.ProcessRecipeService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PricingFileService {
    private static final String TEMPLATE_PATH = "/templates/pricing-material-list-template.xlsx";
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Pattern BAGS_PER_BOX = Pattern.compile("(\\d+)袋/箱");

    private static final int MATERIAL_START_ROW = 12;
    private static final int TEMPLATE_LAST_MATERIAL_ROW = 48;
    private static final int TEMPLATE_TOTAL_ROW = 49;
    private static final int TEMPLATE_LAST_ROW = 63;
    private static final int TEMPLATE_PACKAGING_TITLE_ROW = 54;
    private static final int TEMPLATE_PACKAGING_ITEM_FIRST_ROW = 57;
    private static final int TEMPLATE_FOOTER_ROW = 63;
    private static final int PRINT_LAST_COLUMN = 10;

    public PricingFileResult generate(SampleVersion version, String pricingVersionNo) {
        return generate(version, pricingVersionNo, "LHYC");
    }

    public PricingFileResult generate(SampleVersion version, String pricingVersionNo, String customerName) {
        return generate(version, pricingVersionNo, customerName, YieldCalculationMode.SELECTED_PRIMARY_MATERIALS);
    }

    public PricingFileResult generate(
            SampleVersion version,
            String pricingVersionNo,
            String customerName,
            YieldCalculationMode yieldCalculationMode
    ) {
        return generate(version, pricingVersionNo, customerName, yieldCalculationMode, List.of());
    }

    public PricingFileResult generate(
            SampleVersion version,
            String pricingVersionNo,
            String customerName,
            YieldCalculationMode yieldCalculationMode,
            List<PricingPackagingItem> packagingItems
    ) {
        return generateWorkbook(version, pricingVersionNo, customerName, yieldCalculationMode, packagingItems,
                version.materials() == null ? List.of() : version.materials(), null, null);
    }

    public PricingFileResult generate(
            SampleVersion version,
            String pricingVersionNo,
            String customerName,
            ProcessRevision revision,
            List<PricingPackagingItem> packagingItems
    ) {
        if (revision == null) {
            return generate(version, pricingVersionNo, customerName,
                    YieldCalculationMode.SELECTED_PRIMARY_MATERIALS, packagingItems);
        }
        return generate(version, pricingVersionNo, customerName, revision, packagingItems, null, null);
    }

    public PricingFileResult generate(SampleVersion version, String pricingVersionNo, String customerName,
            ProcessRevision revision, List<PricingPackagingItem> packagingItems,
            ProcessExportView.FinishedQuantity finished, TrialPromotionService.SourceMetadata source) {
        var label = "正式工艺 R" + revision.revisionNo() + (source == null ? "" : " / 试验方案 " + source.trialName() + " V" + source.trialVersionNo());
        var view = new ProcessExportCheckService().view(version.productName(), label, revision.snapshot(), finished, packagingItems)
                .withMetadata(new ProcessExportView.Metadata(version.specification(), version.authorName(), LocalDate.now().toString()));
        return new PricingFileResult(version.productName() + "-产品核价基础数据表-R" + revision.revisionNo() + ".xlsx",
                version.versionNo() + "-核价" + pricingVersionNo, renderBasis(view, false));
    }

    /** Clean formal-process mode. Legacy template generation above remains unchanged. */
    public byte[] renderBasis(ProcessExportView view, boolean preview) {
        var checks = new ProcessExportCheckService();
        if (!preview) checks.requireReady(view, "PRICING_XLSX");
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var ingredients = basisSheet(book, "实际投料依据", view, preview,
                    List.of("工序/步骤", "物料编码", "物料名称", "角色", "实际投入 kg", "说明"));
            int row = 6;
            for (var item : view.ingredients()) {
                basisRow(ingredients, row++, item.majorSequence() + "." + item.stepSequence(), item.materialCode(), item.materialName(), roleLabel(item.role()), item.weightKg(), "外部实际投料");
            }
            basisRow(ingredients, row++, "外部投料合计", "", "中间产物只流转，不重复领入", "", view.externalInputKg(), "");
            basisRow(ingredients, row++, "口径说明", "仅列实际批次；主料得率与独立包装产量分别确认");
            finishBasisSheet(book, ingredients, row);

            var process = basisSheet(book, "主料流转依据", view, preview,
                    List.of("大工序", "口径", "实际主料投入 kg", "实际主料产出 kg", "主料得率 %", "说明"));
            row = 6;
            for (var major : view.majors()) basisRow(process, row++, major.sequence() + " " + major.name(),
                    "PRIMARY_INPUT".equals(major.yieldBasis()) ? "主料投入" : "NONE".equals(major.yieldBasis()) ? "不参与" : "旧版口径待确认",
                    major.primaryInputKg(), major.primaryOutputKg(), major.mainYieldPercent(), "NONE".equals(major.yieldBasis()) ? "不参与连乘" : major.mainYieldPercent() == null ? "待完善主料流转" : "实际数据");
            basisRow(process, row++, "全流程主料得率 %", view.mainYieldPercent(), "仅主料工序得率连乘");
            finishBasisSheet(book, process, row);

            var packing = basisSheet(book, "独立成品与包装", view, preview,
                    List.of("编码/项目", "物料/说明", "实际数量", "数量单位", "规格", "换算规则"));
            row = 6; var finished = view.finishedQuantity();
            basisRow(packing, row++, "独立成品净重", finished == null ? "试验方案未关联独立实测成品" : finished.sourceLabel(), finished == null ? null : finished.weightKg(), "kg");
            basisRow(packing, row++, "独立成品数量", "不得由主料得率或参考产量推算", finished == null ? null : finished.quantity(), finished == null ? null : finished.unit());
            for (var item : view.packaging()) basisRow(packing, row++, item.materialCode() == null ? "—" : item.materialCode(), item.materialName(), item.quantity(), item.quantityUnit(), item.packageSpec(), item.conversionRule());
            if (view.packaging().isEmpty()) basisRow(packing, row++, "包装物料", "待填写 / 未关联");
            if (preview) for (var issue : checks.check(view, "PRICING_XLSX").issues()) basisRow(packing, row++, "待补充", (issue.majorSequence() == null ? "" : "工序 " + issue.majorSequence() + (issue.stepSequence() == null ? "" : " / 步骤 " + issue.stepSequence()) + "：") + issue.message());
            finishBasisSheet(book, packing, row);
            book.write(out); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Failed to generate basis workbook", e); }
    }

    private Sheet basisSheet(XSSFWorkbook book, String name, ProcessExportView view, boolean preview, List<String> headers) {
        var sheet = book.createSheet(name);
        basisRow(sheet, 0, (preview ? "研发预览 · 非正式归档 · " : "") + "产品核价基础数据表");
        basisRow(sheet, 1, "产品 / 产品规格", view.productName() + " / " + (view.metadata() == null || view.metadata().specification() == null ? "待填写" : view.metadata().specification()));
        basisRow(sheet, 2, "来源", view.sourceLabel());
        basisRow(sheet, 3, "实际基准批次 kg", view.externalInputKg(), "重量单位统一 kg；计划目标不参与计算");
        basisRow(sheet, 4, "编制人 / 日期", (view.metadata() == null || view.metadata().compiledBy() == null ? "待填写" : view.metadata().compiledBy()) + " / " + (view.metadata() == null ? LocalDate.now() : view.metadata().date()));
        basisRow(sheet, 5, headers.toArray());
        var widths = "实际投料依据".equals(name) ? new int[]{19, 20, 60, 12, 20, 24}
                : "独立成品与包装".equals(name) ? new int[]{24, 42, 22, 18, 26, 32} : new int[]{28, 20, 27, 27, 24, 28};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
        for (int r = 0; r < 5; r++) {
            if (r == 0) sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 5));
            else if (r != 3) sheet.addMergedRegion(new CellRangeAddress(r, r, 1, 5));
            else sheet.addMergedRegion(new CellRangeAddress(r, r, 2, 5));
        }
        sheet.getFooter().setLeft(view.productName() + " / " + view.sourceLabel());
        sheet.getFooter().setCenter(name);
        sheet.getFooter().setRight("第 &P 页 / 共 &N 页");
        sheet.createFreezePane(0, 6); return sheet;
    }

    private void basisRow(Sheet sheet, int index, Object... values) {
        var row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            var cell = row.createCell(c); var value = values[c];
            if (value instanceof Number n) cell.setCellValue(n.doubleValue()); else cell.setCellValue(value == null ? "待填写" : value.toString());
        }
        if (index >= 6 && values.length == 2 && values[1] instanceof String)
            sheet.addMergedRegion(new CellRangeAddress(index, index, 1, 5));
    }

    private void finishBasisSheet(XSSFWorkbook book, Sheet sheet, int rows) {
        var normal = book.createCellStyle(); normal.setWrapText(true); normal.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        normal.setDataFormat(book.createDataFormat().getFormat("0.####"));
        var font = book.createFont(); font.setFontName("Noto Sans CJK SC"); font.setFontHeightInPoints((short)11); normal.setFont(font);
        var header = book.createCellStyle(); header.cloneStyleFrom(normal); header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); header.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        var integer = book.createCellStyle(); integer.cloneStyleFrom(normal); integer.setDataFormat(book.createDataFormat().getFormat("0"));
        for (var row : sheet) for (var cell : row) cell.setCellStyle(row.getRowNum() == 0 || row.getRowNum() == 5 ? header
                : cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() == Math.rint(cell.getNumericCellValue()) ? integer : normal);
        for (var row : sheet) {
            float height = row.getRowNum() == 0 ? 30 : row.getRowNum() < 5 ? 23 : 27;
            for (var cell : row) {
                if (cell.getCellType() != CellType.STRING) continue;
                int first = cell.getColumnIndex(), last = first;
                for (var merge : sheet.getMergedRegions()) if (merge.isInRange(row.getRowNum(), first)) { last = merge.getLastColumn(); break; }
                double width = 0; for (int c = first; c <= last; c++) width += sheet.getColumnWidth(c) / 256d;
                int lines = 0;
                for (var line : cell.getStringCellValue().split("\\n", -1)) {
                    double units = line.codePoints().mapToDouble(cp -> cp > 255 ? 2.2 : 1.1).sum();
                    lines += Math.max(1, (int)Math.ceil(units / Math.max(1, width - 3)));
                }
                height = Math.max(height, lines * 17 + 9);
            }
            row.setHeightInPoints(height);
        }
        sheet.setDisplayGridlines(false); sheet.setFitToPage(true); sheet.setAutobreaks(true);
        sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0);
        sheet.setRepeatingRows(new CellRangeAddress(5, 5, -1, -1));
        book.setPrintArea(book.getSheetIndex(sheet), 0, 5, 0, rows - 1);
    }
    private String roleLabel(String role) { return "PRIMARY".equals(role) ? "主料" : "PROCESS_WATER".equals(role) ? "工艺用水" : "辅料"; }


    private PricingFileResult generateWorkbook(
            SampleVersion version,
            String pricingVersionNo,
            String customerName,
            YieldCalculationMode yieldCalculationMode,
            List<PricingPackagingItem> packagingItems,
            List<ExperimentMaterial> materials,
            BigDecimal finishedYieldPercent,
            String sourceDescription
    ) {
        try (InputStream template = requireTemplate();
             Workbook workbook = WorkbookFactory.create(template);
             var output = new ByteArrayOutputStream()) {
            sanitizeWorkbookMetadata(workbook);
            var sheet = workbook.getSheetAt(0);
            var customer = blankToDefault(customerName, "LHYC");
            var effectiveDate = version.effectiveDate() == null ? LocalDate.now() : version.effectiveDate();
            var confirmedPackaging = packagingItems == null ? List.<PricingPackagingItem>of() : packagingItems;
            var layout = layoutRows(materials.size(), confirmedPackaging.size());

            relocateTemplateStructure(sheet, layout);
            fillHeader(sheet, version, customer, effectiveDate, sourceDescription);
            fillMaterials(sheet, materials, layout);
            fillSummary(sheet, materials, version, layout, yieldCalculationMode, finishedYieldPercent,
                    sourceDescription != null);
            if (confirmedPackaging.isEmpty()) {
                fillPackagingQuantities(sheet, version, layout);
            } else {
                fillPackagingItems(sheet, confirmedPackaging, layout);
            }
            configurePrintLayout(workbook, sheet, layout);

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
            return new PricingFileResult(fileName, pricingVersion, sanitizeOoxmlPackage(output.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate pricing workbook", e);
        }
    }

    LayoutRows layoutRows(int materialCount) {
        return layoutRows(materialCount, 6);
    }

    LayoutRows layoutRows(int materialCount, int packagingItemCount) {
        var actualMaterialCount = Math.max(materialCount, 1);
        var lastMaterialRow = MATERIAL_START_ROW + actualMaterialCount - 1;
        var totalRow = lastMaterialRow + 1;
        var referenceOutputRow = totalRow + 1;
        var yieldRateRow = referenceOutputRow + 1;
        var packageCountRow = yieldRateRow + 1;
        var packagingStartRow = packageCountRow + 2;
        return new LayoutRows(
                lastMaterialRow,
                totalRow,
                referenceOutputRow,
                yieldRateRow,
                packageCountRow,
                packagingStartRow,
                Math.max(6, packagingItemCount)
        );
    }

    private InputStream requireTemplate() {
        var stream = PricingFileService.class.getResourceAsStream(TEMPLATE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Pricing template not found: " + TEMPLATE_PATH);
        }
        return stream;
    }

    private void relocateTemplateStructure(Sheet sheet, LayoutRows layout) {
        var copiedMerges = new ArrayList<CellRangeAddress>();
        copiedMerges.addAll(templateMerges(sheet, TEMPLATE_TOTAL_ROW, TEMPLATE_PACKAGING_ITEM_FIRST_ROW - 1,
                layout.totalRow()));
        for (int itemIndex = 0; itemIndex < layout.packagingItemCount(); itemIndex++) {
            copiedMerges.addAll(templateMerges(sheet, TEMPLATE_PACKAGING_ITEM_FIRST_ROW, TEMPLATE_PACKAGING_ITEM_FIRST_ROW,
                    packagingItemRow(layout, itemIndex)));
        }
        copiedMerges.addAll(templateMerges(sheet, TEMPLATE_FOOTER_ROW, TEMPLATE_FOOTER_ROW, footerRow(layout)));
        removeMergesIntersecting(sheet, MATERIAL_START_ROW, Math.max(TEMPLATE_LAST_ROW, footerRow(layout)));
        copyTemplateRows(sheet, TEMPLATE_TOTAL_ROW, TEMPLATE_PACKAGING_ITEM_FIRST_ROW - 1, layout.totalRow());
        copyTemplateRow(sheet, TEMPLATE_FOOTER_ROW, footerRow(layout));
        for (int itemIndex = 0; itemIndex < layout.packagingItemCount(); itemIndex++) {
            copyTemplateRow(sheet, TEMPLATE_PACKAGING_ITEM_FIRST_ROW, packagingItemRow(layout, itemIndex));
        }
        clearRowsExcept(sheet, TEMPLATE_TOTAL_ROW, TEMPLATE_LAST_ROW, layout.totalRow(), footerRow(layout));
        copiedMerges.forEach(sheet::addMergedRegion);
    }

    private List<CellRangeAddress> templateMerges(Sheet sheet, int sourceFirstRow, int sourceLastRow, int targetFirstRow) {
        var merges = new ArrayList<CellRangeAddress>();
        for (int index = 0; index < sheet.getNumMergedRegions(); index++) {
            var source = sheet.getMergedRegion(index);
            if (source.getFirstRow() >= sourceFirstRow && source.getLastRow() <= sourceLastRow) {
                var offset = targetFirstRow - sourceFirstRow;
                merges.add(new CellRangeAddress(
                        source.getFirstRow() + offset,
                        source.getLastRow() + offset,
                        source.getFirstColumn(),
                        source.getLastColumn()
                ));
            }
        }
        return merges;
    }

    private void copyTemplateRows(Sheet sheet, int sourceFirstRow, int sourceLastRow, int targetFirstRow) {
        if (targetFirstRow < sourceFirstRow) {
            for (int sourceRow = sourceFirstRow; sourceRow <= sourceLastRow; sourceRow++) {
                copyTemplateRow(sheet, sourceRow, targetFirstRow + sourceRow - sourceFirstRow);
            }
            return;
        }
        for (int sourceRow = sourceLastRow; sourceRow >= sourceFirstRow; sourceRow--) {
            copyTemplateRow(sheet, sourceRow, targetFirstRow + sourceRow - sourceFirstRow);
        }
    }

    private void copyTemplateRow(Sheet sheet, int sourceRowIndex, int targetRowIndex) {
        var source = sheet.getRow(sourceRowIndex);
        if (source == null) {
            return;
        }
        var target = sheet.getRow(targetRowIndex) == null
                ? sheet.createRow(targetRowIndex)
                : sheet.getRow(targetRowIndex);
        target.setHeight(source.getHeight());

        var lastCell = Math.max(source.getLastCellNum(), target.getLastCellNum());
        for (int col = 0; col < lastCell; col++) {
            var sourceCell = source.getCell(col);
            var targetCell = target.getCell(col);
            if (sourceCell == null) {
                if (targetCell != null) {
                    targetCell.setBlank();
                }
                continue;
            }
            if (targetCell == null) {
                targetCell = target.createCell(col);
            }
            copyCell(sheet, sourceCell, targetCell);
        }
    }

    private void copyCell(Sheet sheet, Cell source, Cell target) {
        target.setCellStyle(source.getCellStyle());
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getRichStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case ERROR -> target.setCellErrorValue(source.getErrorCellValue());
            case BLANK, _NONE -> target.setBlank();
        }
        copyComment(sheet, source.getCellComment(), target);
    }

    private void copyComment(Sheet sheet, Comment sourceComment, Cell target) {
        if (sourceComment == null) {
            target.removeCellComment();
            return;
        }
        var anchor = sheet.getWorkbook().getCreationHelper().createClientAnchor();
        var targetComment = sheet.createDrawingPatriarch().createCellComment(anchor);
        targetComment.setAuthor(sourceComment.getAuthor());
        targetComment.setString(sourceComment.getString());
        target.setCellComment(targetComment);
    }

    private void clearRowsExcept(Sheet sheet, int firstRow, int lastRow, int keepFirstRow, int keepLastRow) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            if (rowIndex >= keepFirstRow && rowIndex <= keepLastRow) {
                continue;
            }
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                cell.setBlank();
                cell.removeCellComment();
            }
        }
    }

    private void removeMergesIntersecting(Sheet sheet, int firstRow, int lastRow) {
        for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
            var range = sheet.getMergedRegion(index);
            if (range.getFirstRow() <= lastRow && range.getLastRow() >= firstRow) {
                sheet.removeMergedRegion(index);
            }
        }
    }

    private void fillHeader(
            Sheet sheet,
            SampleVersion version,
            String customer,
            LocalDate effectiveDate,
            String sourceDescription
    ) {
        setText(sheet, 2, 3, formatProductTitle(version.productName(), customer));
        setText(sheet, 4, 3, "产品负责人:" + blankToDefault(version.ownerName(), ""));
        setText(sheet, 4, 9, "规格：" + blankToDefault(version.specification(), ""));
        setText(sheet, 6, 3, blankToDefault(version.productType(), ""));
        setText(sheet, 6, 9, formatVersionLabel(version.versionNo()));
        setText(sheet, 7, 3, "LHRZP-03-YF-");
        setText(sheet, 7, 6, effectiveDate.format(HEADER_DATE));
        setText(sheet, 8, 3, blankToDefault(version.authorName(), ""));
        setText(sheet, 8, 6, sourceDescription == null ? "——" : sourceDescription);
    }

    private void fillMaterials(Sheet sheet, List<ExperimentMaterial> materials, LayoutRows layout) {
        clearRows(sheet, MATERIAL_START_ROW, layout.lastMaterialRow());
        for (int rowIndex = TEMPLATE_LAST_MATERIAL_ROW + 1; rowIndex <= layout.lastMaterialRow(); rowIndex++) {
            copyTemplateRow(sheet, MATERIAL_START_ROW, rowIndex);
        }

        for (int index = 0; index < materials.size(); index++) {
            var material = materials.get(index);
            var rowIndex = MATERIAL_START_ROW + index;
            setNumeric(sheet, rowIndex, 2, material.sequence());
            setText(sheet, rowIndex, 3, material.materialCode());
            setText(sheet, rowIndex, 4, material.materialName());
            setNumeric(sheet, rowIndex, 6, decimalValue(material.weightKg()));
            setNumeric(sheet, rowIndex, 7, decimalValue(material.utilizationRate()));
            setFormula(sheet, rowIndex, 8, "G%s/H%s".formatted(rowIndex + 1, rowIndex + 1));
            setText(sheet, rowIndex, 9, material.remark());
        }

        for (int rowIndex = MATERIAL_START_ROW; rowIndex <= layout.lastMaterialRow(); rowIndex++) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 4, 5));
        }
        applyStageMerges(sheet, materials);
    }

    private void clearRows(Sheet sheet, int firstRow, int lastRow) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            for (Cell cell : row) {
                cell.setBlank();
                cell.removeCellComment();
            }
        }
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
                    && java.util.Objects.equals(stage, materials.get(groupEnd + 1).stage())) {
                groupEnd++;
            }
            int firstRow = MATERIAL_START_ROW + groupStart;
            int lastRow = MATERIAL_START_ROW + groupEnd;
            setText(sheet, firstRow, 0, blankToDefault(stage, ""));
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, 0, 1));
            groupStart = groupEnd + 1;
        }
    }

    private void fillSummary(
            Sheet sheet,
            List<ExperimentMaterial> materials,
            SampleVersion version,
            LayoutRows layout,
            YieldCalculationMode yieldCalculationMode,
            BigDecimal finishedYieldPercent,
            boolean finishedYieldAuthoritative
    ) {
        var firstExcelRow = MATERIAL_START_ROW + 1;
        var lastExcelRow = layout.lastMaterialRow() + 1;

        setNumeric(sheet, layout.totalRow(), 2, materials.size() + 1D);
        setText(sheet, layout.totalRow(), 3, "总计");
        setFormula(sheet, layout.totalRow(), 6, "SUM(G%s:G%s)".formatted(firstExcelRow, lastExcelRow));
        setFormula(sheet, layout.totalRow(), 8, "SUM(I%s:I%s)".formatted(firstExcelRow, lastExcelRow));

        setText(sheet, layout.referenceOutputRow(), 3, "研发部参考出成(kg）");
        if (version.referenceOutputKg() != null) {
            setNumeric(sheet, layout.referenceOutputRow(), 6, decimalValue(version.referenceOutputKg()));
        }
        setText(sheet, layout.yieldRateRow(), 3, "研发部参考得率(%)");
        if (finishedYieldPercent != null) {
            setNumeric(sheet, layout.yieldRateRow(), 6,
                    decimalValue(finishedYieldPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)));
        } else if (!finishedYieldAuthoritative) {
            var yieldRows = yieldFormulaRows(materials, yieldCalculationMode);
            if (!yieldRows.isEmpty() && version.referenceOutputKg() != null) {
            var denominatorCells = yieldRows.stream()
                    .map(index -> "I" + (MATERIAL_START_ROW + index + 1))
                    .toList();
            var denominator = denominatorCells.size() == 1
                    ? denominatorCells.get(0)
                    : "SUM(%s)".formatted(String.join(",", denominatorCells));
            setFormula(sheet, layout.yieldRateRow(), 6,
                    "G%s/%s".formatted(layout.referenceOutputRow() + 1, denominator));
            }
        }
        setText(sheet, layout.packageCountRow(), 3, "研发部参考包数");
        if (version.referenceOutputKg() != null && version.unitWeightKg() != null) {
            var packageCount = version.referenceOutputKg().divide(version.unitWeightKg(), 0, RoundingMode.DOWN);
            setNumeric(sheet, layout.packageCountRow(), 6, packageCount.doubleValue());
        }
    }

    private List<Integer> yieldFormulaRows(List<ExperimentMaterial> materials, YieldCalculationMode mode) {
        var resolvedMode = mode == null ? YieldCalculationMode.SELECTED_PRIMARY_MATERIALS : mode;
        var selected = new ArrayList<Integer>();
        for (int index = 0; index < materials.size(); index++) {
            var material = materials.get(index);
            if ("PACKAGING".equals(material.materialCategory())) {
                continue;
            }
            if (resolvedMode == YieldCalculationMode.TOTAL_PICKING_WEIGHT || material.primaryMaterial()) {
                selected.add(index);
            }
        }
        if (selected.isEmpty() && resolvedMode == YieldCalculationMode.SELECTED_PRIMARY_MATERIALS) {
            for (int index = 0; index < materials.size(); index++) {
                if (!"PACKAGING".equals(materials.get(index).materialCategory())) {
                    selected.add(index);
                    break;
                }
            }
        }
        return selected;
    }

    private void fillPackagingQuantities(Sheet sheet, SampleVersion version, LayoutRows layout) {
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

        setNumeric(sheet, packagingItemRow(layout, 0), packageCount);
        setNumeric(sheet, packagingItemRow(layout, 1), packageCount);
        setNumeric(sheet, packagingItemRow(layout, 2), boxCount);
        setNumeric(sheet, packagingItemRow(layout, 3), boxCount);
    }

    private void fillPackagingItems(
            Sheet sheet,
            List<PricingPackagingItem> packagingItems,
            LayoutRows layout
    ) {
        for (int index = 0; index < packagingItems.size(); index++) {
            var item = packagingItems.get(index);
            var rowIndex = packagingItemRow(layout, index);
            setNumeric(sheet, rowIndex, 2, item.sequence());
            if (item.materialCode() == null || item.materialCode().isBlank()) {
                cell(sheet, rowIndex, 3).setBlank();
            } else {
                setText(sheet, rowIndex, 3, item.materialCode());
            }
            setText(sheet, rowIndex, 4, item.materialName());
            setNumeric(sheet, rowIndex, 6, decimalValue(item.quantity()));
            setText(sheet, rowIndex, 9, item.packageSpec());
        }
    }

    private void setNumeric(Sheet sheet, int rowIndex, int columnIndex, double value) {
        cell(sheet, rowIndex, columnIndex).setCellValue(value);
    }

    private void setNumeric(Sheet sheet, int rowIndex, int value) {
        setNumeric(sheet, rowIndex, 6, value);
    }

    private void configurePrintLayout(Workbook workbook, Sheet sheet, LayoutRows layout) {
        var printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(false);
        printSetup.setFitWidth((short) 1);
        sheet.setAutobreaks(true);
        sheet.setFitToPage(true);
        sheet.setHorizontallyCenter(true);
        sheet.setVerticallyCenter(false);
        sheet.setDisplayGridlines(false);
        workbook.setPrintArea(0, 0, PRINT_LAST_COLUMN, 0, footerRow(layout));
    }

    private void sanitizeWorkbookMetadata(Workbook workbook) {
        new ArrayList<>(workbook.getAllNames()).forEach(workbook::removeName);
        for (int sheetIndex = workbook.getNumberOfSheets() - 1; sheetIndex >= 0; sheetIndex--) {
            if (workbook.getSheetVisibility(sheetIndex) != org.apache.poi.ss.usermodel.SheetVisibility.VISIBLE) {
                workbook.removeSheetAt(sheetIndex);
            }
        }
        if (!(workbook instanceof XSSFWorkbook xssfWorkbook)) {
            return;
        }

        var coreProperties = xssfWorkbook.getProperties().getCoreProperties();
        coreProperties.setCategory("");
        coreProperties.setContentStatus("");
        coreProperties.setContentType("");
        coreProperties.setCreator("");
        coreProperties.setDescription("");
        coreProperties.setIdentifier("");
        coreProperties.setKeywords("");
        coreProperties.setLastModifiedByUser("");
        coreProperties.setSubjectProperty("");
        coreProperties.setTitle("");
        coreProperties.setVersion("");
        coreProperties.setRevision("");

        var extendedProperties = xssfWorkbook.getProperties().getExtendedProperties().getUnderlyingProperties();
        if (extendedProperties.isSetCompany()) {
            extendedProperties.unsetCompany();
        }
        if (extendedProperties.isSetManager()) {
            extendedProperties.unsetManager();
        }
        if (extendedProperties.isSetTemplate()) {
            extendedProperties.unsetTemplate();
        }
        if (extendedProperties.isSetTitlesOfParts()) {
            extendedProperties.unsetTitlesOfParts();
        }
        if (extendedProperties.isSetHeadingPairs()) {
            extendedProperties.unsetHeadingPairs();
        }
        xssfWorkbook.getProperties().getCustomProperties().getUnderlyingProperties().setPropertyArray(
                new org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty[0]
        );
    }

    private byte[] sanitizeOoxmlPackage(byte[] content) throws IOException {
        try (var input = new ZipInputStream(new java.io.ByteArrayInputStream(content));
             var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output)) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (isMetadataPackageEntry(entry.getName())) {
                    continue;
                }
                var entryContent = input.readAllBytes();
                if (entry.getName().equals("[Content_Types].xml")) {
                    entryContent = replaceXml(entryContent,
                            "<Override\\b(?=[^>]*PartName=\"/(?:docProps/custom\\.xml|customXml/[^\"]+|xl/externalLinks/[^\"]+)\")[^>]*/>");
                } else if (entry.getName().endsWith(".rels")) {
                    entryContent = stripMetadataRelationships(entryContent);
                } else if (entry.getName().equals("xl/workbook.xml")) {
                    entryContent = replaceXml(entryContent, "(?s)<externalReferences>.*?</externalReferences>");
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(entryContent);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private boolean isMetadataPackageEntry(String entryName) {
        return entryName.equals("docProps/custom.xml")
                || entryName.startsWith("customXml/")
                || entryName.startsWith("xl/externalLinks/");
    }

    private byte[] stripMetadataRelationships(byte[] content) {
        var withoutCustomXmlOrExternalLinks = replaceXml(content,
                "<Relationship\\b(?=[^>]*Target=\"[^\"]*(?:customXml|externalLinks)[^\"]*\")[^>]*/>");
        return replaceXml(withoutCustomXmlOrExternalLinks,
                "<Relationship\\b(?=[^>]*Target=\"docProps/custom\\.xml\")[^>]*/>");
    }

    private byte[] replaceXml(byte[] content, String expression) {
        return new String(content, StandardCharsets.UTF_8)
                .replaceAll(expression, "")
                .getBytes(StandardCharsets.UTF_8);
    }

    private int footerRow(LayoutRows layout) {
        return packagingItemRow(layout, layout.packagingItemCount());
    }

    private int packagingItemRow(LayoutRows layout, int index) {
        return layout.packagingStartRow() + TEMPLATE_PACKAGING_ITEM_FIRST_ROW - TEMPLATE_PACKAGING_TITLE_ROW + index;
    }

    private int parseBagsPerBox(String specification) {
        if (specification == null || specification.isBlank()) {
            return 0;
        }
        var matcher = BAGS_PER_BOX.matcher(specification);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String formatProductTitle(String productName, String customer) {
        var name = blankToDefault(productName, "未命名产品");
        return name.contains("（核价）") ? name : name + "-" + customer + "（核价）";
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


    private double decimalValue(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }

    private void setText(Sheet sheet, int rowIndex, int columnIndex, String value) {
        cell(sheet, rowIndex, columnIndex).setCellValue(value == null ? "" : value);
    }

    private void setFormula(Sheet sheet, int rowIndex, int columnIndex, String formula) {
        cell(sheet, rowIndex, columnIndex).setCellFormula(formula);
    }

    private Cell cell(Sheet sheet, int rowIndex, int columnIndex) {
        var row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        var cell = row.getCell(columnIndex);
        return cell == null ? row.createCell(columnIndex) : cell;
    }
}

record LayoutRows(
        int lastMaterialRow,
        int totalRow,
        int referenceOutputRow,
        int yieldRateRow,
        int packageCountRow,
        int packagingStartRow,
        int packagingItemCount
) {
}
