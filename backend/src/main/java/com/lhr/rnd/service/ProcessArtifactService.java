package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.ProcessRecipeService;
import com.lhr.rnd.model.ProcessArtifact;
import com.lhr.rnd.model.ProcessArtifactPublic;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import com.lhr.rnd.model.ProcessExportView;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProcessArtifactService {
    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DISPLAY_SCALE = 4;

    private final JdbcTemplate jdbc;
    private final ProcessRevisionService revisionService;
    private final LocalArchiveStorageService storage;
    private final AuditLogService auditLogService;
    private final ProcessArtifactCleanupLedgerService cleanupLedger;
    private final ProcessPlanService plans;
    private final TrialSchemeService trials;
    private final TrialPromotionService promotions;
    private final ProcessExportCheckService exportChecks;
    private final ProcessRecipeService recipeService = new ProcessRecipeService();
    private final ProcessPlanCalculationService calculationService = new ProcessPlanCalculationService();

    public ProcessArtifactService(
            JdbcTemplate jdbc,
            ProcessRevisionService revisionService,
            LocalArchiveStorageService storage,
            AuditLogService auditLogService,
            ProcessArtifactCleanupLedgerService cleanupLedger,
            ProcessPlanService plans, TrialSchemeService trials, TrialPromotionService promotions,
            ProcessExportCheckService exportChecks
    ) {
        this.jdbc = jdbc;
        this.revisionService = revisionService;
        this.storage = storage;
        this.auditLogService = auditLogService;
        this.cleanupLedger = cleanupLedger;
        this.plans = plans;
        this.trials = trials;
        this.promotions = promotions;
        this.exportChecks = exportChecks;
    }

    @Transactional(readOnly = true)
    public ProcessExportView revisionView(String formId, String revisionId, SessionPrincipal principal) {
        var revision = revisionService.find(formId, revisionId, principal);
        var source = promotions.source(formId, revisionId, principal);
        return exportChecks.view(productName(formId), "正式工艺 R" + revision.revisionNo()
                + (source == null ? "" : " / 试验方案 " + source.trialName() + " V" + source.trialVersionNo()),
                revision.snapshot(), null, List.of()).withMetadata(exportMetadata(formId, principal))
                .withActualsProvenance(source != null && source.inheritedActuals(), source == null ? null : source.sourceTrialId());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ProcessExportView draftView(String formId, int versionNo, SessionPrincipal principal) {
        plans.requireDraftReadAccess(formId, principal);
        var plan = plans.find(formId);
        requireVersion(versionNo, plan.versionNo());
        return exportChecks.view(productName(formId), "已保存正式工艺草稿 V" + plan.versionNo(), plan, null, List.of()).withMetadata(exportMetadata(formId, principal));
    }

    @Transactional(readOnly = true)
    public ProcessExportView trialView(String formId, String trialId, int versionNo, SessionPrincipal principal) {
        var trial = trials.find(formId, trialId, principal);
        requireVersion(versionNo, trial.versionNo());
        // A/B schemes do not own a packed-finished measurement; never borrow the shared experiment's value.
        return exportChecks.view(productName(formId), "试验方案 " + trial.name() + " V" + trial.versionNo(), trial.plan(), null, List.of()).withMetadata(exportMetadata(formId, principal))
                .withActualsProvenance(trial.inheritedActuals(), trial.sourceTrialId());
    }

    private ProcessExportView.Metadata exportMetadata(String formId, SessionPrincipal principal) {
        var specification = jdbc.queryForObject("select version.specification from experiment_form form join sample_version version on version.id = form.version_id where form.id = ?", String.class, formId);
        return new ProcessExportView.Metadata(specification, principal.name(), java.time.LocalDate.now().toString());
    }

    public ProcessExportView.Check check(ProcessExportView view, String type) { return exportChecks.check(view, type); }

    /** Render only: no archive storage, artifact rows, audit records, cleanup ledger or promotion previews. */
    public ArtifactDownload preview(ProcessExportView view, String type) {
        exportChecks.validateType(type);
        try {
            byte[] bytes;
            if ("PRICING_XLSX".equals(type)) bytes = new PricingFileService().renderBasis(view, true);
            else if (ProcessArtifact.FORMULA_XLSX.equals(type)) bytes = formulaBytes(view, null, null, null,
                    LocalDateTime.now(), view.metadata() == null ? null : view.metadata().compiledBy(), true);
            else {
                bytes = new SopDocumentRenderer().render(view, new SopDocumentRenderer.Metadata(null, null, null,
                        view.metadata() == null ? null : view.metadata().compiledBy(), LocalDateTime.now(), true));
            }
            var label = "SOP_DOCX".equals(type) ? "研发SOP" : "FORMULA_XLSX".equals(type) ? "研发配方" : "核价基础数据";
            return new ArtifactDownload(view.productName() + "-" + view.sourceLabel().replace('/', '-') + "-" + label + "-预览." + ("SOP_DOCX".equals(type) ? "docx" : "xlsx"),
                    "SOP_DOCX".equals(type) ? DOCX_CONTENT_TYPE : XLSX_CONTENT_TYPE, bytes);
        } catch (java.io.IOException e) { throw new IllegalStateException("Failed to render preview", e); }
        catch (Exception e) { if (e instanceof RuntimeException runtime) throw runtime; throw new IllegalStateException("Failed to render preview", e); }
    }

    private void requireVersion(int expected, int actual) {
        if (expected != actual) throw new BusinessException("PROCESS_EXPORT_VERSION_CONFLICT", "保存版本已变化，请重新加载后预览");
    }

    @Transactional(readOnly = true)
    public List<ProcessArtifact> list(String formId, String revisionId, SessionPrincipal principal) {
        requireReadAccess(formId, revisionId, principal);
        revisionService.find(formId, revisionId);
        return jdbc.query("select * from experiment_process_artifact where process_revision_id = ? order by generated_at desc, id desc",
                (rs, row) -> map(rs), revisionId);
    }

    @Transactional(readOnly = true)
    public List<ProcessArtifactPublic> listReadyPublic(String formId, String revisionId, SessionPrincipal principal) {
        requireReadAccess(formId, revisionId, principal);
        revisionService.find(formId, revisionId);
        return jdbc.query("select id, process_revision_id, artifact_type, document_version, status, generated_at from experiment_process_artifact where process_revision_id = ? and status = 'READY' order by generated_at desc, id desc",
                (rs, row) -> new ProcessArtifactPublic(rs.getString("id"), rs.getString("process_revision_id"),
                        rs.getString("artifact_type"), rs.getString("document_version"), rs.getString("status"),
                        rs.getTimestamp("generated_at").toLocalDateTime().toString()), revisionId);
    }

    @Transactional
    public ProcessArtifact generate(String formId, String revisionId, String artifactType, SessionPrincipal principal) {
        cleanupLedger.reconcileStale();
        requireWriteAccess(formId, principal);
        var revision = revisionService.find(formId, revisionId);
        validateType(artifactType);
        var exportView = revisionView(formId, revisionId, principal);
        exportChecks.requireReady(exportView, artifactType);
        if (recipeService.aggregate(revision.snapshot()).stream().map(ProcessRecipeService.RecipeLine::weightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0) {
            throw new BusinessException("EXTERNAL_MATERIAL_WEIGHT_REQUIRED", "外部物料总重量必须大于0");
        }
        // Serialize versions per immutable revision in the database, including across web requests.
        jdbc.queryForObject("select id from experiment_process_revision where id = ? for update", String.class, revisionId);
        var documentVersion = Integer.toString(jdbc.queryForObject(
                "select coalesce(max(cast(document_version as integer)), 0) + 1 from experiment_process_artifact where process_revision_id = ? and artifact_type = ?",
                Integer.class, revisionId, artifactType));
        var id = "PART-" + UUID.randomUUID();
        var generatedAt = LocalDateTime.now();
        var storageKey = storageKey(revisionId, artifactType, id);
        var productName = productName(formId);
        var readyMetadata = new boolean[]{false};
        var reservation = cleanupLedger.register(storageKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED && readyMetadata[0]) cleanupLedger.confirm(reservation);
                    else cleanupLedger.orphanAndDelete(reservation);
                }
            });
        }
        byte[] bytes = null;
        try {
            bytes = ProcessArtifact.FORMULA_XLSX.equals(artifactType)
                    ? formulaBytes(exportView, "R" + revision.revisionNo(), "V" + documentVersion, revision.changeReason(), generatedAt, principal.name(), false)
                    : new SopDocumentRenderer().render(exportView, new SopDocumentRenderer.Metadata("R" + revision.revisionNo(), "V" + documentVersion,
                            revision.changeReason(), principal.name(), generatedAt, false));
            cleanupLedger.renew(reservation);
            storage.store(storageKey, bytes);
            if (!cleanupLedger.lockForReady(reservation)) {
                storage.delete(storageKey);
                throw new BusinessException("PROCESS_ARTIFACT_RESERVATION_LOST", "成果文件生成租约已失效，请重试");
            }
            var artifact = new ProcessArtifact(id, revisionId, artifactType, documentVersion, ProcessArtifact.READY,
                    generatedAt.toString(), principal.name().trim(), storageKey, summary(revision, artifactType), null,
                    principal.userId(), sha256(bytes), (long) bytes.length);
            insert(artifact);
            auditLogService.record("PROCESS_ARTIFACT", formId, "PROCESS_ARTIFACT_GENERATED", principal.name().trim(), principal.userId(),
                    "artifactId=%s;revisionId=%s;type=%s;documentVersion=%s".formatted(id, revisionId, artifactType, documentVersion));
            readyMetadata[0] = true;
            return artifact;
        } catch (BusinessException exception) {
            return recordFailure(formId, id, revisionId, artifactType, documentVersion, generatedAt, principal, exception.getMessage());
        } catch (Exception exception) {
            return recordFailure(formId, id, revisionId, artifactType, documentVersion, generatedAt, principal, "文件生成失败");
        }
    }

    @Transactional(readOnly = true)
    public ArtifactDownload download(String formId, String revisionId, String artifactId, SessionPrincipal principal) {
        requireReadAccess(formId, revisionId, principal);
        var revision = revisionService.find(formId, revisionId);
        var rows = jdbc.query("select * from experiment_process_artifact where id = ? and process_revision_id = ?",
                (rs, row) -> map(rs), artifactId, revisionId);
        if (rows.isEmpty()) throw new BusinessException("PROCESS_ARTIFACT_NOT_FOUND", "成果文件不存在");
        var artifact = rows.get(0);
        if (!ProcessArtifact.READY.equals(artifact.status()) || blank(artifact.storageKey())) {
            throw new BusinessException("PROCESS_ARTIFACT_NOT_READY", "成果文件尚未生成成功，请重新生成");
        }
        try {
            var bytes = storage.read(artifact.storageKey());
            if (artifact.byteSize() == null || artifact.contentSha256() == null || artifact.byteSize() != bytes.length
                    || !MessageDigest.isEqual(artifact.contentSha256().getBytes(StandardCharsets.US_ASCII), sha256(bytes).getBytes(StandardCharsets.US_ASCII))) {
                throw new BusinessException("PROCESS_ARTIFACT_INTEGRITY_ERROR", "成果文件完整性校验失败，请重新生成");
            }
            return new ArtifactDownload(fileName(productName(formId), revision.revisionNo(), artifact), contentType(artifact.artifactType()), bytes);
        } catch (BusinessException exception) {
            if ("ARCHIVE_FILE_NOT_FOUND".equals(exception.code()) || "ARCHIVE_FILE_READ_FAILED".equals(exception.code())) {
                throw new BusinessException("PROCESS_ARTIFACT_FILE_NOT_FOUND", "成果文件内容不存在或已损坏，请重新生成");
            }
            throw exception;
        }
    }

    private ProcessArtifact recordFailure(String formId, String id, String revisionId, String type, String version, LocalDateTime at, SessionPrincipal operator, String reason) {
        var artifact = new ProcessArtifact(id, revisionId, type, version, ProcessArtifact.FAILED, at.toString(), operator.name().trim(),
                null, "generation failed", truncate(reason), operator.userId(), null, null);
        insert(artifact);
        auditLogService.record("PROCESS_ARTIFACT", formId, "PROCESS_ARTIFACT_GENERATION_FAILED", operator.name().trim(), operator.userId(),
                "artifactId=%s;type=%s;documentVersion=%s;reason=%s".formatted(id, type, version, truncate(reason)));
        return artifact;
    }

    private void insert(ProcessArtifact artifact) {
        jdbc.update("insert into experiment_process_artifact(id, process_revision_id, artifact_type, document_version, status, generated_at, generated_by, storage_key, content_summary, failure_reason, generated_by_user_id, content_sha256, byte_size) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                artifact.id(), artifact.processRevisionId(), artifact.artifactType(), artifact.documentVersion(), artifact.status(),
                LocalDateTime.parse(artifact.generatedAt()), artifact.generatedBy(), artifact.storageKey(), artifact.contentSummary(), artifact.failureReason(), artifact.generatedByUserId(), artifact.contentSha256(), artifact.byteSize());
    }

    private ProcessArtifact map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProcessArtifact(rs.getString("id"), rs.getString("process_revision_id"), rs.getString("artifact_type"),
                rs.getString("document_version"), rs.getString("status"), rs.getTimestamp("generated_at").toLocalDateTime().toString(),
                rs.getString("generated_by"), rs.getString("storage_key"), rs.getString("content_summary"), rs.getString("failure_reason"),
                rs.getString("generated_by_user_id"), rs.getString("content_sha256"), (Long) rs.getObject("byte_size"));
    }

    private byte[] formulaBytes(ProcessExportView view, String formalRevision, String documentVersion, String changeReason,
                                LocalDateTime generatedAt, String generatedBy, boolean preview) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var titleStyle = formulaStyle(workbook, true, 16, HorizontalAlignment.CENTER, IndexedColors.WHITE, IndexedColors.DARK_BLUE, false);
            var sectionStyle = formulaStyle(workbook, true, 11, HorizontalAlignment.LEFT, IndexedColors.BLACK, IndexedColors.LIGHT_CORNFLOWER_BLUE, false);
            var header = formulaStyle(workbook, true, 10, HorizontalAlignment.CENTER, IndexedColors.BLACK, IndexedColors.LIGHT_CORNFLOWER_BLUE, true);
            var label = formulaStyle(workbook, true, 10, HorizontalAlignment.LEFT, IndexedColors.BLACK, null, true);
            var text = formulaStyle(workbook, false, 10, HorizontalAlignment.LEFT, IndexedColors.BLACK, null, true);
            var sourceText = formulaStyle(workbook, false, 10, HorizontalAlignment.LEFT, IndexedColors.BLACK, null, true);
            var number = formulaStyle(workbook, false, 10, HorizontalAlignment.RIGHT, IndexedColors.BLACK, null, true);
            sourceText.setIndention((short) 1);
            number.setIndention((short) 1);
            number.setDataFormat(workbook.createDataFormat().getFormat("0.####"));

            var lines = recipeService.aggregate(view.snapshot());
            var totalWeight = lines.stream().map(ProcessRecipeService.RecipeLine::weightKg).map(this::safe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!preview && totalWeight.signum() <= 0) throw new BusinessException("EXTERNAL_MATERIAL_WEIGHT_REQUIRED", "外部物料总重量必须大于0");
            var displayedPercentages = totalWeight.signum() <= 0 ? List.<BigDecimal>of() : allocatePercent(lines.stream().map(ProcessRecipeService.RecipeLine::weightKg).map(this::safe).toList(), totalWeight);

            var actualSheet = workbook.createSheet(view.inheritedActuals() ? "方案实际投料" : "本次实际投料");
            var actualRowIndex = formulaPreamble(actualSheet, "研发配方—" + view.actualPrefix() + "实际外部投入", view, formalRevision,
                    documentVersion, changeReason, generatedAt, generatedBy, preview, titleStyle, sectionStyle, label, text);
            var actualSection = actualSheet.createRow(actualRowIndex++); set(actualSection, 0, view.actualPrefix() + "实际外部投入"); actualSection.getCell(0).setCellStyle(sectionStyle); actualSheet.addMergedRegion(new CellRangeAddress(actualSection.getRowNum(), actualSection.getRowNum(), 0, 5));
            var actualHeader = actualRowIndex;
            formulaHeader(actualSheet.createRow(actualRowIndex++), header, "序号", "物料编码", "物料名称", "角色", view.actualPrefix() + "实际 kg", "加入步骤");
            for (int index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                var row = actualSheet.createRow(actualRowIndex++);
                formulaNumber(row, 0, index + 1, number); formulaText(row, 1, line.materialCode(), text); formulaText(row, 2, line.materialName(), text);
                formulaText(row, 3, ExportDisplayFormat.materialRole(line.canonicalMaterialRole()), text);
                var incomplete = line.sources().stream().anyMatch(source -> source.weightKg() == null);
                if (incomplete) formulaText(row, 4, "待填写", text); else formulaNumber(row, 4, line.weightKg(), number);
                formulaText(row, 5, joinSteps(view.snapshot(), line.sources()), sourceText); formulaRowHeight(row, line.materialName(), joinSteps(view.snapshot(), line.sources()));
            }
            var actualTotal = actualSheet.createRow(actualRowIndex++); formulaText(actualTotal, 0, "合计", label);
            if (view.externalInputKg() == null) formulaText(actualTotal, 4, "待填写", text); else formulaNumber(actualTotal, 4, view.externalInputKg(), number);
            var issues = exportChecks.check(view, "FORMULA_XLSX").issues();
            if (!issues.isEmpty()) {
                actualRowIndex++; var issueSection = actualSheet.createRow(actualRowIndex++); set(issueSection, 0, "待核实项"); issueSection.getCell(0).setCellStyle(sectionStyle); actualSheet.addMergedRegion(new CellRangeAddress(issueSection.getRowNum(), issueSection.getRowNum(), 0, 5));
                for (var issue : issues) { var row = actualSheet.createRow(actualRowIndex++); formulaText(row, 0, "待核实", label); formulaText(row, 1, issue.message(), text); actualSheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, 5)); }
            }
            configureFormulaSheet(workbook, actualSheet, view, actualHeader, actualRowIndex, actualSheet.getSheetName());

            var normalizedSheet = workbook.createSheet("每100kg折算");
            var normalizedRowIndex = formulaPreamble(normalizedSheet, "研发配方—每100kg外部投入折算", view, formalRevision,
                    documentVersion, changeReason, generatedAt, generatedBy, preview, titleStyle, sectionStyle, label, text);
            var normalizedSection = normalizedSheet.createRow(normalizedRowIndex++); set(normalizedSection, 0, "每 100 kg 外部投入折算"); normalizedSection.getCell(0).setCellStyle(sectionStyle); normalizedSheet.addMergedRegion(new CellRangeAddress(normalizedSection.getRowNum(), normalizedSection.getRowNum(), 0, 5));
            normalizedRowIndex = formulaMetadataRow(normalizedSheet, normalizedRowIndex, "折算分母", view.externalInputKg() == null ? "待填写（本次实验外部原料实际总投入）" : ExportDisplayFormat.actualKg(view.externalInputKg()) + "（不含中间投入和成品重量）", label, text);
            var normalizedHeader = normalizedRowIndex;
            formulaHeader(normalizedSheet.createRow(normalizedRowIndex++), header, "序号", "物料编码", "物料名称", "角色", "每100kg外部投入折算 kg", "加入步骤");
            BigDecimal displayedHundredKg = BigDecimal.ZERO;
            for (int index = 0; index < lines.size(); index++) {
                var line = lines.get(index); var row = normalizedSheet.createRow(normalizedRowIndex++);
                formulaNumber(row, 0, index + 1, number); formulaText(row, 1, line.materialCode(), text); formulaText(row, 2, line.materialName(), text);
                formulaText(row, 3, ExportDisplayFormat.materialRole(line.canonicalMaterialRole()), text);
                var incomplete = view.externalInputKg() == null || line.sources().stream().anyMatch(source -> source.weightKg() == null);
                if (incomplete || displayedPercentages.isEmpty()) formulaText(row, 4, "待填写", text);
                else { var normalized = displayedPercentages.get(index); formulaNumber(row, 4, normalized, number); displayedHundredKg = displayedHundredKg.add(normalized); }
                formulaText(row, 5, joinSteps(view.snapshot(), line.sources()), sourceText); formulaRowHeight(row, line.materialName(), joinSteps(view.snapshot(), line.sources()));
            }
            var normalizedTotal = normalizedSheet.createRow(normalizedRowIndex++); formulaText(normalizedTotal, 0, "合计", label);
            if (view.externalInputKg() == null) formulaText(normalizedTotal, 4, "待填写", text); else formulaNumber(normalizedTotal, 4, displayedHundredKg, number);
            configureFormulaSheet(workbook, normalizedSheet, view, normalizedHeader, normalizedRowIndex, "每100kg折算");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private int formulaPreamble(org.apache.poi.ss.usermodel.Sheet sheet, String titleText, ProcessExportView view,
                                String formalRevision, String documentVersion, String changeReason,
                                LocalDateTime generatedAt, String generatedBy, boolean preview,
                                CellStyle titleStyle, CellStyle sectionStyle, CellStyle label, CellStyle text) {
        var rowIndex = 0;
        var title = sheet.createRow(rowIndex++); set(title, 0, titleText); title.getCell(0).setCellStyle(titleStyle); sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5)); title.setHeightInPoints(28);
        if (preview) {
            var banner = sheet.createRow(rowIndex++); set(banner, 0, "研发预览  非正式归档"); banner.getCell(0).setCellStyle(sectionStyle); sheet.addMergedRegion(new CellRangeAddress(banner.getRowNum(), banner.getRowNum(), 0, 5));
        }
        rowIndex = formulaMetadataRow(sheet, rowIndex, "产品", view.productName(), label, text);
        if (view.metadata() != null && !blank(view.metadata().specification())) rowIndex = formulaMetadataRow(sheet, rowIndex, "规格", view.metadata().specification(), label, text);
        rowIndex = formulaMetadataRow(sheet, rowIndex, "固定数据来源", view.sourceLabel(), label, text);
        if (view.inheritedActuals()) rowIndex = formulaMetadataRow(sheet, rowIndex, "实测来源说明", view.actualProvenanceLabel(), label, text);
        if (!blank(formalRevision)) rowIndex = formulaMetadataRow(sheet, rowIndex, "正式工艺修订", formalRevision, label, text);
        if (!blank(documentVersion)) rowIndex = formulaMetadataRow(sheet, rowIndex, "文件版本", documentVersion, label, text);
        if (!blank(changeReason)) rowIndex = formulaMetadataRow(sheet, rowIndex, "变更原因", changeReason, label, text);
        if (!blank(generatedBy)) rowIndex = formulaMetadataRow(sheet, rowIndex, "编制人", generatedBy, label, text);
        rowIndex = formulaMetadataRow(sheet, rowIndex, "编制时间", generatedAt == null ? view.metadata() == null ? "" : view.metadata().date() : TIME.format(generatedAt), label, text);
        rowIndex = formulaMetadataRow(sheet, rowIndex, "外部原料实际总投入", view.externalInputKg() == null ? "待填写" : ExportDisplayFormat.actualKg(view.externalInputKg()), label, text);
        rowIndex = formulaMetadataRow(sheet, rowIndex, "主流程实际得率", ExportDisplayFormat.percent(view.mainYieldPercent()), label, text);
        return rowIndex + 1;
    }

    private void configureFormulaSheet(XSSFWorkbook workbook, org.apache.poi.ss.usermodel.Sheet sheet, ProcessExportView view,
                                       int headerRow, int lastRow, String footerLabel) {
        int[] widths = {8, 19, 34, 14, 23, 38};
        for (int column = 0; column < widths.length; column++) sheet.setColumnWidth(column, widths[column] * 256);
        sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));
        sheet.createFreezePane(0, headerRow + 1);
        sheet.setFitToPage(true); sheet.getPrintSetup().setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE); sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0);
        sheet.setAutobreaks(true); sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.LeftMargin, 0.35); sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.RightMargin, 0.35);
        sheet.getFooter().setLeft(view.productName()); sheet.getFooter().setCenter(view.sourceLabel() + "  第 &P 页 / 共 &N 页"); sheet.getFooter().setRight(footerLabel);
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 5, 0, lastRow - 1);
    }

    private CellStyle formulaStyle(XSSFWorkbook workbook, boolean bold, int fontSize, HorizontalAlignment alignment,
                                   IndexedColors foreground, IndexedColors fill, boolean border) {
        var style = workbook.createCellStyle(); style.setAlignment(alignment); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setWrapText(true);
        var font = workbook.createFont(); font.setFontName(ExportDisplayFormat.CJK_FONT); font.setFontHeightInPoints((short) fontSize); font.setBold(bold); font.setColor(foreground.getIndex()); style.setFont(font);
        if (fill != null) { style.setFillForegroundColor(fill.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
        if (border) { style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN); style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN); style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); }
        return style;
    }

    private int formulaMetadataRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, String name, String content, CellStyle label, CellStyle text) {
        var row = sheet.createRow(rowIndex); formulaText(row, 0, name, label); formulaText(row, 2, content, text);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 1));
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 2, 5));
        row.setHeightInPoints(21);
        return rowIndex + 1;
    }
    private void formulaHeader(Row row, CellStyle style, String... labels) { for (int i = 0; i < labels.length; i++) formulaText(row, i, labels[i], style); row.setHeightInPoints(28); }
    private void formulaText(Row row, int column, String content, CellStyle style) { var cell = row.createCell(column); cell.setCellValue(value(content)); cell.setCellStyle(style); }
    private void formulaNumber(Row row, int column, Number number, CellStyle style) { var cell = row.createCell(column); cell.setCellValue(number.doubleValue()); cell.setCellStyle(style); }
    private void formulaRowHeight(Row row, String... values) { var length = java.util.Arrays.stream(values).filter(java.util.Objects::nonNull).mapToInt(String::length).max().orElse(0); row.setHeightInPoints(Math.max(22, (float)Math.ceil(length / 18.0) * 15)); }

    private void requireWriteAccess(String formId, SessionPrincipal principal) {
        requirePrincipal(principal);
        if ("RND_DIRECTOR".equals(principal.role())) return;
        if (!"RND_ENGINEER".equals(principal.role()) || !isOwner(formId, principal)) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权生成该工艺成果文件");
        }
    }

    private void requireReadAccess(String formId, String revisionId, SessionPrincipal principal) {
        requirePrincipal(principal);
        if ("RND_DIRECTOR".equals(principal.role())) return;
        if ("TESTER".equals(principal.role()) || "QA_TESTER".equals(principal.role())) {
            var count = jdbc.queryForObject("select count(*) from test_assignment where experiment_form_id = ? and process_revision_id = ? and tester_user_id = ? and archived_at is null",
                    Integer.class, formId, revisionId, principal.userId());
            if (count != null && count > 0) return;
            throw new BusinessException("PROCESS_REVISION_NOT_ASSIGNED", "测试人员只能查看当前测试任务绑定版本的成果文件");
        }
        if (!"RND_ENGINEER".equals(principal.role()) || !isOwner(formId, principal)) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权查看该工艺成果文件");
        }
    }

    private void requirePrincipal(SessionPrincipal principal) {
        if (principal == null || blank(principal.userId()) || blank(principal.name()) || blank(principal.role())) {
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "工艺成果文件必须使用服务端会话身份");
        }
    }

    private boolean isOwner(String formId, SessionPrincipal principal) {
        var owners = jdbc.query("select task.assignee_user_id, task.assignee_name from experiment_form form join rnd_task task on form.task_id = task.id where form.id = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, formId);
        if (owners.isEmpty()) return false;
        var owner = owners.get(0);
        if (!blank(owner[0])) return owner[0].equals(principal.userId());
        if (blank(owner[1])) return false;
        var ids = jdbc.query("select id from user_account where name = ? and status = 'ACTIVE'", (rs, row) -> rs.getString(1), owner[1]);
        return ids.size() == 1 && ids.get(0).equals(principal.userId());
    }

    private void validateType(String type) {
        if (!ProcessArtifact.FORMULA_XLSX.equals(type) && !ProcessArtifact.SOP_DOCX.equals(type)) {
            throw new BusinessException("PROCESS_ARTIFACT_TYPE_INVALID", "只支持生成研发配方或研发SOP");
        }
    }

    private String storageKey(String revisionId, String type, String id) {
        return "process-artifacts/" + revisionId + "/" + type.toLowerCase() + "/" + id + (ProcessArtifact.FORMULA_XLSX.equals(type) ? ".xlsx" : ".docx");
    }

    private String summary(ProcessRevision revision, String type) {
        return "sourceRevision=V%s;type=%s;recipeLines=%d;finishedYield=%s".formatted(revision.revisionNo(), type,
                recipeService.aggregate(revision.snapshot()).size(), percent(calculationService.calculateBatch(revision.snapshot())));
    }

    private String productName(String formId) {
        var names = jdbc.query("select product_name from experiment_form where id = ?", (rs, row) -> rs.getString(1), formId);
        if (names.isEmpty()) throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        return names.get(0);
    }

    private String fileName(String productName, int revisionNo, ProcessArtifact artifact) {
        var label = ProcessArtifact.FORMULA_XLSX.equals(artifact.artifactType()) ? "研发配方" : "研发SOP";
        var ext = ProcessArtifact.FORMULA_XLSX.equals(artifact.artifactType()) ? ".xlsx" : ".docx";
        return productName + "-工艺V" + revisionNo + "-" + label + "V" + artifact.documentVersion() + ext;
    }

    private String contentType(String type) {
        return ProcessArtifact.FORMULA_XLSX.equals(type) ? XLSX_CONTENT_TYPE : DOCX_CONTENT_TYPE;
    }

    private void set(Row row, int start, String... values) {
        for (int index = 0; index < values.length; index++) set(row, start + index, values[index]);
    }

    private void set(Row row, int index, String value) {
        row.createCell(index).setCellValue(value(value));
    }

    private String joinSteps(ProcessPlan plan, List<ProcessRecipeService.RecipeSource> sources) {
        var names = new LinkedHashMap<String, String>();
        for (var major : values(plan.majorProcesses())) for (var step : values(major.steps())) {
            names.put(major.sequence() + "." + step.sequence(), major.sequence() + "." + step.sequence() + " " + value(step.stepName()));
        }
        return sources.stream().map(source -> names.getOrDefault(source.majorSequence() + "." + source.stepSequence(),
                source.majorSequence() + "." + source.stepSequence())).distinct().collect(Collectors.joining("；"));
    }

    private String percent(BigDecimal value) {
        return value == null ? "待填写" : value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private List<BigDecimal> allocatePercent(List<BigDecimal> weights, BigDecimal total) {
        var units = BigDecimal.valueOf(1_000_000L); // 100.0000 expressed in ten-thousandth units.
        var floors = new ArrayList<Long>(); var remainders = new ArrayList<BigDecimal>(); long allocated = 0;
        for (var weight : weights) {
            var exact = safe(weight).max(BigDecimal.ZERO).multiply(units).divide(total, 12, RoundingMode.DOWN);
            var floor = exact.setScale(0, RoundingMode.DOWN).longValue(); floors.add(floor); allocated += floor;
            remainders.add(exact.subtract(BigDecimal.valueOf(floor)));
        }
        var order = java.util.stream.IntStream.range(0, weights.size()).boxed()
                .sorted(Comparator.<Integer, BigDecimal>comparing(remainders::get).reversed().thenComparingInt(Integer::intValue)).toList();
        for (int item = 0; item < units.longValue() - allocated; item++) floors.set(order.get(item % order.size()), floors.get(order.get(item % order.size())) + 1);
        return floors.stream().map(value -> BigDecimal.valueOf(value, DISPLAY_SCALE)).toList();
    }
    private String sha256(byte[] value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value);
            var hex = new StringBuilder(64);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (Exception exception) { throw new IllegalStateException("成果文件摘要失败", exception); }
    }
    private String truncate(String value) { return value == null ? "生成失败" : value.substring(0, Math.min(value.length(), 900)); }
    private String value(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private <T> List<T> values(List<T> values) { return values == null ? List.of() : values; }

    public record ArtifactDownload(String fileName, String contentType, byte[] content) { }
}
