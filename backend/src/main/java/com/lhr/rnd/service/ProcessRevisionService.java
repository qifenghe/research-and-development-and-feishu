package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessRevisionService {
    private final JdbcTemplate jdbc;
    private final ProcessPlanService planService;
    private final ObjectMapper objectMapper;
    private final ProcessSubmissionValidator validator = new ProcessSubmissionValidator();

    public ProcessRevisionService(JdbcTemplate jdbc, ProcessPlanService planService, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessRevision submit(String formId, SubmitCommand command) {
        if (command == null || !command.confirmed()) {
            throw new BusinessException("PROCESS_SUBMISSION_CONFIRMATION_REQUIRED", "正式提交前必须明确确认");
        }
        if (blank(command.submittedBy())) {
            throw new BusinessException("PROCESS_SUBMITTED_BY_REQUIRED", "正式提交必须使用可信会话身份");
        }
        var plan = planService.find(formId);
        if (plan.versionNo() != command.versionNo()) {
            throw new BusinessException("PROCESS_PLAN_VERSION_CONFLICT", "工艺方案已被更新，请刷新后重试");
        }
        var check = validator.validate(plan);
        if (!check.ready()) {
            throw new BusinessException("PROCESS_PLAN_NOT_READY", "工艺方案未达到正式提交条件");
        }
        var sourceRevisionId = blank(plan.sourceRevisionId()) ? null : plan.sourceRevisionId();
        var changeReason = firstNonBlank(command.changeReason(), plan.changeReason());
        if (sourceRevisionId != null && blank(changeReason)) {
            throw new BusinessException("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本创建草稿必须填写变更原因");
        }

        // This conditional transition is the submission lock: only one caller can submit a draft version.
        planService.markSubmitted(formId, command.versionNo());
        var normalized = normalize(new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), "SUBMITTED",
                plan.majorProcesses(), plan.batchYieldPercent(), plan.balanceToleranceKg(), plan.legacy(),
                plan.sourceRevisionId(), plan.changeReason()));
        var snapshotJson = serialize(normalized);
        var now = LocalDateTime.now();
        var revisionNo = jdbc.queryForObject(
                "select coalesce(max(revision_no), 0) + 1 from experiment_process_revision where experiment_form_id = ?",
                Integer.class, formId);
        var id = "PREV-" + UUID.randomUUID();
        try {
            jdbc.update("insert into experiment_process_revision(id, process_plan_id, experiment_form_id, revision_no, source_revision_id, change_reason, submitted_by, submitted_at, snapshot_json, snapshot_hash) values (?,?,?,?,?,?,?,?,?,?)",
                    id, normalized.id(), formId, revisionNo, sourceRevisionId, changeReason, command.submittedBy().trim(), now,
                    snapshotJson, sha256(snapshotJson));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("PROCESS_PLAN_VERSION_CONFLICT", "工艺方案已被更新，请刷新后重试");
        }
        return find(formId, id);
    }

    @Transactional(readOnly = true)
    public List<ProcessRevision.ProcessRevisionSummary> list(String formId) {
        planService.find(formId);
        return jdbc.query("select * from experiment_process_revision where experiment_form_id = ? order by revision_no desc",
                (rs, row) -> new ProcessRevision.ProcessRevisionSummary(rs.getString("id"), rs.getInt("revision_no"),
                        rs.getString("source_revision_id"), rs.getString("change_reason"), rs.getString("submitted_by"),
                        rs.getTimestamp("submitted_at").toLocalDateTime().toString(), rs.getString("snapshot_hash")), formId);
    }

    @Transactional(readOnly = true)
    public ProcessRevision find(String formId, String revisionId) {
        planService.find(formId);
        var rows = jdbc.query("select * from experiment_process_revision where id = ? and experiment_form_id = ?", (rs, row) -> map(rs), revisionId, formId);
        if (rows.isEmpty()) throw new BusinessException("PROCESS_REVISION_NOT_FOUND", "正式工艺版本不存在");
        return rows.get(0);
    }

    @Transactional
    public ProcessPlan createDraftFromRevision(String formId, String revisionId, String changeReason) {
        if (blank(changeReason)) {
            throw new BusinessException("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本创建草稿必须填写变更原因");
        }
        var revision = find(formId, revisionId);
        return planService.restoreAsNewDraft(formId, revision.snapshot(), revision.id(), changeReason.trim());
    }

    private ProcessRevision map(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return new ProcessRevision(rs.getString("id"), rs.getString("process_plan_id"), rs.getString("experiment_form_id"),
                    rs.getInt("revision_no"), rs.getString("source_revision_id"), rs.getString("change_reason"),
                    rs.getString("submitted_by"), rs.getTimestamp("submitted_at").toLocalDateTime().toString(),
                    rs.getString("snapshot_hash"), objectMapper.readValue(rs.getString("snapshot_json"), ProcessPlan.class));
        } catch (Exception exception) {
            throw new IllegalStateException("正式工艺版本快照无法读取", exception);
        }
    }

    private ProcessPlan normalize(ProcessPlan plan) {
        try {
            // A database reload already orders all nested lists by sequence. Round-tripping through the project mapper
            // makes absent optional collections explicit before the bytes become the immutable snapshot source.
            return objectMapper.readValue(objectMapper.writeValueAsBytes(plan), ProcessPlan.class);
        } catch (Exception exception) {
            throw new IllegalStateException("工艺方案快照标准化失败", exception);
        }
    }

    private String serialize(ProcessPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception exception) {
            throw new IllegalStateException("工艺方案快照序列化失败", exception);
        }
    }

    private String sha256(String json) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(digest.length * 2);
            for (var value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("工艺方案快照摘要失败", exception);
        }
    }

    private String firstNonBlank(String first, String second) {
        return blank(first) ? (blank(second) ? null : second.trim()) : first.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record SubmitCommand(int versionNo, boolean confirmed, String changeReason, String submittedBy) {
    }
}
