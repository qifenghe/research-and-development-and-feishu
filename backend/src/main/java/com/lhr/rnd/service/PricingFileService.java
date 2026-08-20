package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.PricingPackagingItem;
import com.lhr.rnd.model.ProcessRevision;
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
        var materials = resolveFormalPricingMaterials(version, revision);
        var finishedYieldPercent = new ProcessPlanCalculationService().calculateBatch(revision.snapshot());
        return generateWorkbook(version, pricingVersionNo, customerName,
                YieldCalculationMode.SELECTED_PRIMARY_MATERIALS, packagingItems, materials,
                finishedYieldPercent, revisionSource(revision, finishedYieldPercent));
    }

    List<ExperimentMaterial> resolveFormalPricingMaterials(SampleVersion version, ProcessRevision revision) {
        var recipe = new ProcessRecipeService().aggregate(revision.snapshot());
        var resolved = new java.util.LinkedHashMap<String, FormalPricingMaterial>();
        for (var line : recipe) {
            var materialCode = blankToDefault(line.materialCode(), line.formulaMaterialId());
            if (materialCode == null || materialCode.isBlank()) {
                throw new BusinessException("PROCESS_REVISION_PRICING_MATERIAL_UNPRICED", "正式工艺配方存在无法按物料标识核价的外部物料");
            }
            var costSource = costSourceFor(version.materials(), line);
            var roles = line.sources().stream()
                    .map(ProcessRecipeService.RecipeSource::materialRole)
                    .map(this::normalizedStableValue)
                    .distinct()
                    .toList();
            if (roles.size() != 1 || roles.get(0) == null) {
                throw new BusinessException("PROCESS_REVISION_PRICING_MATERIAL_ATTRIBUTE_CONFLICT",
                        "正式工艺配方物料的投料角色冲突");
            }
            var role = roles.get(0);
            var canonicalCode = costSource.materialCode().trim();
            var prior = resolved.get(canonicalCode);
            if (prior == null) {
                resolved.put(canonicalCode, new FormalPricingMaterial(line, costSource, role, line.weightKg()));
            } else if (!java.util.Objects.equals(prior.materialRole, role)
                    || !java.util.Objects.equals(prior.costSource.stage(), costSource.stage())
                    || !java.util.Objects.equals(prior.costSource.materialCategory(), costSource.materialCategory())
                    || !sameDecimal(prior.costSource.utilizationRate(), costSource.utilizationRate())
                    || !java.util.Objects.equals(normalizedStableValue(prior.costSource.inputUnit()),
                    normalizedStableValue(costSource.inputUnit()))) {
                throw new BusinessException("PROCESS_REVISION_PRICING_MATERIAL_ATTRIBUTE_CONFLICT", "正式工艺配方物料的核价属性冲突");
            } else {
                prior.weightKg = prior.weightKg.add(line.weightKg());
            }
        }
        var materials = new ArrayList<ExperimentMaterial>();
        var totalWeight = resolved.values().stream().map(item -> item.weightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        int index = 0;
        for (var item : resolved.values()) {
            index++;
            var line = item.line;
            var costSource = item.costSource;
            materials.add(new ExperimentMaterial(
                    costSource.stage(),
                    index,
                    costSource.materialCode(),
                    line.materialName(),
                    item.weightKg,
                    costSource.utilizationRate() == null ? BigDecimal.ONE : costSource.utilizationRate(),
                    "正式工艺版本 " + revision.revisionNo(),
                    costSource.materialCategory() == null
                            ? ("PRIMARY".equals(item.materialRole) ? "RAW" : "AUXILIARY")
                            : costSource.materialCategory(),
                    "PRIMARY".equals(item.materialRole),
                    totalWeight.signum() == 0 ? null : item.weightKg.divide(totalWeight, 6, RoundingMode.HALF_UP),
                    costSource.inputUnit() == null ? "kg" : costSource.inputUnit()
            ));
        }
        return List.copyOf(materials);
    }

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

    private String revisionSource(ProcessRevision revision, BigDecimal finishedYieldPercent) {
        var yield = finishedYieldPercent == null ? "UNAVAILABLE" : finishedYieldPercent.setScale(6, RoundingMode.HALF_UP) + "%";
        return "source=FORMAL_PROCESS_REVISION;revisionNo=%d;revisionId=%s;finishedYield=%s".formatted(
                revision.revisionNo(), revision.id(), yield);
    }

    private ExperimentMaterial costSourceFor(
            List<ExperimentMaterial> legacyMaterials,
            ProcessRecipeService.RecipeLine recipeLine
    ) {
        var matches = new ArrayList<ExperimentMaterial>();
        for (var material : legacyMaterials == null ? List.<ExperimentMaterial>of() : legacyMaterials) {
            if (sameStableMaterialKey(material.materialCode(), recipeLine.formulaMaterialId())
                    || sameStableMaterialKey(material.materialCode(), recipeLine.materialCode())) {
                matches.add(material);
            }
        }
        if (matches.isEmpty()) {
            throw new BusinessException(
                    "PROCESS_REVISION_PRICING_MATERIAL_UNPRICED",
                    "正式工艺配方物料未关联现有核价物料编码"
            );
        }
        if (matches.size() != 1) {
            throw new BusinessException(
                    "PROCESS_REVISION_PRICING_MATERIAL_MAPPING_AMBIGUOUS",
                    "正式工艺配方物料匹配到多个核价物料编码"
            );
        }
        return matches.get(0);
    }

    private boolean sameStableMaterialKey(String first, String second) {
        return first != null && !first.isBlank() && second != null && !second.isBlank()
                && first.trim().equals(second.trim());
    }

    private String normalizedStableValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean sameDecimal(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private static final class FormalPricingMaterial {
        private final ProcessRecipeService.RecipeLine line;
        private final ExperimentMaterial costSource;
        private final String materialRole;
        private BigDecimal weightKg;

        private FormalPricingMaterial(ProcessRecipeService.RecipeLine line, ExperimentMaterial costSource, String materialRole, BigDecimal weightKg) {
            this.line = line;
            this.costSource = costSource;
            this.materialRole = materialRole;
            this.weightKg = weightKg;
        }
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
