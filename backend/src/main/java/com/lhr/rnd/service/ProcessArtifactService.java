package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.ProcessRecipeService;
import com.lhr.rnd.model.ProcessArtifact;
import com.lhr.rnd.model.ProcessArtifactPublic;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
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
    private final ProcessRecipeService recipeService = new ProcessRecipeService();
    private final ProcessPlanCalculationService calculationService = new ProcessPlanCalculationService();

    public ProcessArtifactService(
            JdbcTemplate jdbc,
            ProcessRevisionService revisionService,
            LocalArchiveStorageService storage,
            AuditLogService auditLogService,
            ProcessArtifactCleanupLedgerService cleanupLedger
    ) {
        this.jdbc = jdbc;
        this.revisionService = revisionService;
        this.storage = storage;
        this.auditLogService = auditLogService;
        this.cleanupLedger = cleanupLedger;
    }

    @Transactional(readOnly = true)
    public List<ProcessArtifact> list(String formId, String revisionId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        revisionService.find(formId, revisionId);
        return jdbc.query("select * from experiment_process_artifact where process_revision_id = ? order by generated_at desc, id desc",
                (rs, row) -> map(rs), revisionId);
    }

    @Transactional(readOnly = true)
    public List<ProcessArtifactPublic> listReadyPublic(String formId, String revisionId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
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
                    ? formulaBytes(productName, revision, documentVersion, generatedAt, principal.name())
                    : sopBytes(productName, revision, documentVersion, generatedAt, principal.name());
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
        requireReadAccess(formId, principal);
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

    private byte[] formulaBytes(String productName, ProcessRevision revision, String documentVersion, LocalDateTime generatedAt, String generatedBy) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("标准配方");
            var header = workbook.createCellStyle();
            header.setAlignment(HorizontalAlignment.CENTER);
            var number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat("0.0000"));
            set(sheet.createRow(0), 0, "标准配方");
            set(sheet.createRow(1), 0, "产品", productName, "来源工艺版本", "V" + revision.revisionNo(), "文件版本", "V" + documentVersion);
            set(sheet.createRow(2), 0, "变更原因", value(revision.changeReason()), "生成信息", generatedBy + " / " + TIME.format(generatedAt), "成品得率", percent(calculationService.calculateBatch(revision.snapshot())));
            var titles = List.of("序号", "物料编码", "物料名称", "角色", "打样重量kg", "配方占比%", "100kg折算kg", "加入步骤");
            var title = sheet.createRow(4);
            for (int column = 0; column < titles.size(); column++) {
                var cell = title.createCell(column);
                cell.setCellValue(titles.get(column));
                cell.setCellStyle(header);
            }
            var lines = recipeService.aggregate(revision.snapshot());
            var totalWeight = lines.stream().map(ProcessRecipeService.RecipeLine::weightKg).map(this::safe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.signum() <= 0) throw new BusinessException("EXTERNAL_MATERIAL_WEIGHT_REQUIRED", "外部物料总重量必须大于0");
            var displayedPercentages = allocatePercent(lines.stream().map(ProcessRecipeService.RecipeLine::weightKg).map(this::safe).toList(), totalWeight);
            var rowIndex = 5;
            BigDecimal displayedHundredKg = BigDecimal.ZERO;
            for (int index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                var row = sheet.createRow(rowIndex++);
                setNumber(row, 0, index + 1, number);
                set(row, 1, value(line.materialCode()));
                set(row, 2, value(line.materialName()));
                set(row, 3, line.sources().stream().map(ProcessRecipeService.RecipeSource::materialRole).filter(role -> !blank(role)).distinct().collect(Collectors.joining("、")));
                setNumber(row, 4, line.weightKg(), number);
                var hundredKg = displayedPercentages.get(index);
                setNumber(row, 5, hundredKg, number);
                displayedHundredKg = displayedHundredKg.add(hundredKg);
                setNumber(row, 6, hundredKg, number);
                set(row, 7, joinSteps(revision.snapshot(), line.sources()));
            }
            var total = sheet.createRow(rowIndex);
            set(total, 0, "合计");
            setNumber(total, 4, lines.stream().map(ProcessRecipeService.RecipeLine::weightKg).reduce(BigDecimal.ZERO, BigDecimal::add), number);
            setNumber(total, 5, BigDecimal.valueOf(100), number);
            setNumber(total, 6, displayedHundredKg, number);
            for (int column = 0; column < titles.size(); column++) sheet.autoSizeColumn(column);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] sopBytes(String productName, ProcessRevision revision, String documentVersion, LocalDateTime generatedAt, String generatedBy) throws Exception {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            heading(document, "研发版生产 SOP：" + productName);
            paragraph(document, "来源工艺版本：V" + revision.revisionNo() + "；文件版本：V" + documentVersion);
            paragraph(document, "来源版本标识：" + value(revision.sourceRevisionId()) + "；版本变更：" + value(revision.changeReason()));
            paragraph(document, "变更原因：" + value(revision.changeReason()) + "；生成信息：" + generatedBy + " / " + TIME.format(generatedAt));
            paragraph(document, "成品得率：" + percent(calculationService.calculateBatch(revision.snapshot())));
            var externalTotal = recipeService.aggregate(revision.snapshot()).stream().map(ProcessRecipeService.RecipeLine::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
            paragraph(document, "适用批量：打样外部投入 " + kg(externalTotal) + "；100kg 标准配方（无独立批量字段，不推定实际生产批量）");
            for (var major : sorted(revision.snapshot().majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
                heading(document, "大工序 " + major.sequence() + "：" + value(major.processName()));
                paragraph(document, "工序说明：" + value(major.description()) + "；备注：" + value(major.remark()));
                var yield = calculationService.calculate(major);
                paragraph(document, "大工序得率：" + percent(yield.mainYieldPercent()) + "；主料首端投入：" + kg(yield.primaryInputWeightKg()) + "；末端产出：" + kg(yield.qualifiedOutputWeightKg()));
                var table = table(document, "步骤", "外部投料", "中间流转", "操作参数/设备工具", "操作要求", "产出状态/重量", "步骤得率");
                for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) {
                    var row = table.createRow();
                    cell(row, 0, step.sequence() + " / " + value(step.stepName()));
                    cell(row, 1, materials(step, "EXTERNAL"));
                    cell(row, 2, intermediateFlow(revision.snapshot(), step));
                    cell(row, 3, parameters(step));
                    cell(row, 4, value(step.instruction()));
                    cell(row, 5, outputs(step));
                    cell(row, 6, percent(calculationService.calculateStep(step).mainYieldPercent()));
                }
                var controls = major.steps() == null ? List.<ProcessPlan.ControlPoint>of() : major.steps().stream()
                        .flatMap(step -> values(step.controlPoints()).stream()).toList();
                if (!controls.isEmpty()) {
                    paragraph(document, "关键控制标准与偏差处理");
                    var controlTable = table(document, "大工序/步骤", "类型/重要性", "控制项目", "目标", "下限", "上限", "单位", "方法", "工具", "频次", "偏差处理", "依据或备注");
                    for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) for (var control : values(step.controlPoints())) {
                        var row = controlTable.createRow();
                        cell(row, 0, major.sequence() + "." + step.sequence() + " " + value(step.stepName()));
                        cell(row, 1, join(" / ", control.controlType(), control.importance()));
                        cell(row, 2, value(control.itemName())); cell(row, 3, value(control.targetValue()));
                        cell(row, 4, value(control.lowerLimit())); cell(row, 5, value(control.upperLimit())); cell(row, 6, value(control.unit()));
                        cell(row, 7, value(control.method())); cell(row, 8, value(control.measurementTool())); cell(row, 9, value(control.frequency()));
                        cell(row, 10, value(control.deviationAction())); cell(row, 11, value(control.basisOrRemark()));
                    }
                }
            }
            heading(document, "测量记录追溯附录（不作为生产指令标准）");
            var appendix = table(document, "大工序/步骤", "控制项目", "确认人/时间", "实测值", "测量时间", "结果", "复测/偏差处理", "工具/依据/备注");
            for (var major : sorted(revision.snapshot().majorProcesses(), ProcessPlan.MajorProcess::sequence)) {
                for (var step : sorted(major.steps(), ProcessPlan.MinorStep::sequence)) {
                    for (var control : values(step.controlPoints())) {
                        for (var measurement : values(control.measurements())) {
                            var row = appendix.createRow();
                            cell(row, 0, major.sequence() + "." + step.sequence() + " " + value(step.stepName()));
                            cell(row, 1, value(control.itemName()));
                            cell(row, 2, join(" / ", control.confirmedBy(), control.confirmedAt()));
                            cell(row, 3, value(measurement.measuredValue())); cell(row, 4, value(measurement.measuredAt()));
                            cell(row, 5, value(measurement.result())); cell(row, 6, join(" / ", measurement.retestResult(), measurement.deviationAction()));
                            cell(row, 7, join(" / ", control.measurementTool(), control.basisOrRemark(), measurement.remark()));
                        }
                    }
                }
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private void requireWriteAccess(String formId, SessionPrincipal principal) {
        requirePrincipal(principal);
        if ("RND_DIRECTOR".equals(principal.role())) return;
        if (!"RND_ENGINEER".equals(principal.role()) || !isOwner(formId, principal)) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权生成该工艺成果文件");
        }
    }

    private void requireReadAccess(String formId, SessionPrincipal principal) {
        requirePrincipal(principal);
        if ("RND_DIRECTOR".equals(principal.role()) || "TESTER".equals(principal.role()) || "QA_TESTER".equals(principal.role())) return;
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
            throw new BusinessException("PROCESS_ARTIFACT_TYPE_INVALID", "只支持生成标准配方或生产SOP");
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
        var label = ProcessArtifact.FORMULA_XLSX.equals(artifact.artifactType()) ? "标准配方" : "生产SOP";
        var ext = ProcessArtifact.FORMULA_XLSX.equals(artifact.artifactType()) ? ".xlsx" : ".docx";
        return productName + "-工艺V" + revisionNo + "-" + label + "V" + artifact.documentVersion() + ext;
    }

    private String contentType(String type) {
        return ProcessArtifact.FORMULA_XLSX.equals(type) ? XLSX_CONTENT_TYPE : DOCX_CONTENT_TYPE;
    }

    private void heading(XWPFDocument document, String text) {
        var paragraph = document.createParagraph();
        paragraph.setStyle("Heading1");
        paragraph.createRun().setText(text);
    }

    private void paragraph(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }

    private XWPFTable table(XWPFDocument document, String... headers) {
        var table = document.createTable(1, headers.length);
        for (int index = 0; index < headers.length; index++) cell(table.getRow(0), index, headers[index]);
        return table;
    }

    private void cell(org.apache.poi.xwpf.usermodel.XWPFTableRow row, int index, String text) {
        row.getCell(index).setText(value(text));
    }

    private void set(Row row, int start, String... values) {
        for (int index = 0; index < values.length; index++) set(row, start + index, values[index]);
    }

    private void set(Row row, int index, String value) {
        row.createCell(index).setCellValue(value(value));
    }

    private void setNumber(Row row, int index, Number value, CellStyle style) {
        var cell = row.createCell(index);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private String joinSteps(ProcessPlan plan, List<ProcessRecipeService.RecipeSource> sources) {
        var names = new LinkedHashMap<String, String>();
        for (var major : values(plan.majorProcesses())) for (var step : values(major.steps())) {
            names.put(major.sequence() + "." + step.sequence(), major.sequence() + "." + step.sequence() + " " + value(step.stepName()));
        }
        return sources.stream().map(source -> names.getOrDefault(source.majorSequence() + "." + source.stepSequence(),
                source.majorSequence() + "." + source.stepSequence())).distinct().collect(Collectors.joining("；"));
    }

    private String materials(ProcessPlan.MinorStep step, String sourceType) {
        return values(step.materials()).stream().filter(material -> sourceType.equals(material.sourceType()))
                .map(material -> "名称：" + value(material.materialName())
                        + "；编码：" + value(material.materialCode())
                        + "；角色：" + value(material.materialRole())
                        + "；状态：" + value(material.materialState())
                        + "；重量：" + kg(material.weightKg())
                        + (blank(material.remark()) ? "" : "；备注：" + material.remark()))
                .collect(Collectors.joining("；"));
    }

    private String intermediateFlow(ProcessPlan plan, ProcessPlan.MinorStep step) {
        var flows = new ArrayList<String>();
        for (var material : values(step.materials()).stream().filter(item -> "STEP_OUTPUT".equals(item.sourceType())).toList()) {
            var producer = findOutputProducer(plan, material.sourceStepOutputId());
            var consumer = findStepLabel(plan, step);
            flows.add("接收产出ID：" + value(material.sourceStepOutputId())
                    + (blank(producer) ? "" : "；流转：" + producer + " → " + consumer)
                    + "；名称：" + value(material.materialName())
                    + "；状态：" + value(material.materialState())
                    + "；重量：" + kg(material.weightKg())
                    + (blank(material.remark()) ? "" : "；备注：" + material.remark()));
        }
        for (var output : values(step.outputs()).stream().filter(item -> "INTERMEDIATE".equals(item.outputType()) || item.continueFlow()).toList()) {
            flows.add("产出ID：" + value(output.id())
                    + "；名称：" + value(output.outputName())
                    + "；状态：" + value(output.materialState())
                    + "；重量：" + kg(output.weightKg())
                    + (blank(output.remark()) ? "" : "；备注：" + output.remark()));
        }
        return String.join("；", flows);
    }

    private String findOutputProducer(ProcessPlan plan, String outputId) {
        for (var major : values(plan.majorProcesses())) for (var step : values(major.steps()))
            for (var output : values(step.outputs())) if (outputId != null && outputId.equals(output.id()))
                return major.sequence() + "." + step.sequence() + " " + value(step.stepName()) + " / " + value(output.outputName());
        return "";
    }

    private String findStepLabel(ProcessPlan plan, ProcessPlan.MinorStep target) {
        for (var major : values(plan.majorProcesses())) for (var step : values(major.steps()))
            if (step == target || (step.id() != null && step.id().equals(target.id())))
                return major.sequence() + "." + step.sequence() + " " + value(step.stepName());
        return value(target.stepName());
    }

    private String parameters(ProcessPlan.MinorStep step) {
        return join("；", parameter(step.parameter1Name(), step.parameter1Value(), step.parameter1Unit()),
                parameter(step.parameter2Name(), step.parameter2Value(), step.parameter2Unit()),
                blank(step.equipment()) ? null : "设备工具：" + step.equipment());
    }

    private String outputs(ProcessPlan.MinorStep step) {
        return values(step.outputs()).stream().map(output -> "名称：" + value(output.outputName())
                        + "；类型：" + value(output.outputType())
                        + "；状态：" + value(output.materialState())
                        + "；重量：" + kg(output.weightKg())
                        + (blank(output.remark()) ? "" : "；备注：" + output.remark()))
                .collect(Collectors.joining("；"));
    }

    private String limits(ProcessPlan.ControlPoint control) {
        if (control.lowerLimit() != null || control.upperLimit() != null) {
            return value(control.lowerLimit()) + "–" + value(control.upperLimit()) + value(control.unit());
        }
        return value(control.targetValue()) + value(control.unit());
    }

    private String parameter(String name, String val, String unit) {
        return blank(name) ? null : name + "=" + value(val) + value(unit);
    }

    private String kg(BigDecimal value) {
        return value == null ? "" : value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString() + "kg";
    }

    private String percent(BigDecimal value) {
        return value == null ? "" : value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString() + "%";
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
    private String join(String separator, String... values) { return java.util.Arrays.stream(values).filter(value -> !blank(value)).collect(Collectors.joining(separator)); }
    private <T> List<T> values(List<T> values) { return values == null ? List.of() : values; }
    private <T> List<T> sorted(List<T> values, java.util.function.ToIntFunction<T> sequence) { return values(values).stream().sorted(Comparator.comparingInt(sequence)).toList(); }

    public record ArtifactDownload(String fileName, String contentType, byte[] content) { }
}
