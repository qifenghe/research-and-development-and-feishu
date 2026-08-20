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
    private final AuditLogService auditLogService;
    private final ProcessSubmissionValidator validator = new ProcessSubmissionValidator();

    public ProcessRevisionService(
            JdbcTemplate jdbc,
            ProcessPlanService planService,
            ObjectMapper objectMapper,
            AuditLogService auditLogService
    ) {
        this.jdbc = jdbc;
        this.planService = planService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProcessRevision submit(String formId, SubmitCommand command) {
        return submitInternal(formId, command, null);
    }

    @Transactional
    public ProcessRevision submit(String formId, SubmitCommand command, SessionPrincipal principal) {
        requireFormalWriteAccess(formId, principal);
        return submitInternal(formId, new SubmitCommand(command.versionNo(), command.confirmed(), command.changeReason(), principal.name()), principal.name(), principal.userId());
    }

    private ProcessRevision submitInternal(String formId, SubmitCommand command, String trustedOperator) { return submitInternal(formId, command, trustedOperator, null); }
    private ProcessRevision submitInternal(String formId, SubmitCommand command, String trustedOperator, String trustedUserId) {
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
        var sourceRevisionId = blank(plan.sourceRevisionId()) ? null : plan.sourceRevisionId().trim();
        var persistedChangeReason = blank(plan.changeReason()) ? null : plan.changeReason().trim();
        var requestedChangeReason = blank(command.changeReason()) ? null : command.changeReason().trim();
        if (persistedChangeReason != null && requestedChangeReason != null && !persistedChangeReason.equals(requestedChangeReason)) {
            throw new BusinessException("PROCESS_CHANGE_REASON_CONFLICT", "变更原因已在草稿中确认，不能在提交时覆盖");
        }
        var changeReason = persistedChangeReason != null ? persistedChangeReason : requestedChangeReason;
        if (sourceRevisionId != null && blank(changeReason)) {
            throw new BusinessException("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本创建草稿必须填写变更原因");
        }

        // This conditional transition is the submission lock: only one caller can submit a draft version.
        planService.markSubmitted(formId, command.versionNo(), changeReason);
        var normalized = normalize(new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), "SUBMITTED",
                plan.majorProcesses(), plan.batchYieldPercent(), plan.balanceToleranceKg(), plan.legacy(),
                sourceRevisionId, changeReason));
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
        auditLogService.record("PROCESS_PLAN", formId, "PROCESS_PLAN_SUBMITTED", trustedOperator == null ? command.submittedBy().trim() : trustedOperator, trustedUserId,
                "revisionId=%s;revisionNo=%d;changeReason=%s".formatted(id, revisionNo, valueOrEmpty(changeReason)));
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

    @Transactional(readOnly = true)
    public ProcessPlan latestSnapshot(String formId) {
        planService.find(formId);
        var rows = jdbc.query("select * from experiment_process_revision where experiment_form_id = ? order by revision_no desc limit 1",
                (rs, row) -> map(rs), formId);
        if (rows.isEmpty()) {
            throw new BusinessException("PROCESS_FORMAL_REVISION_REQUIRED", "当前没有可供查看的正式工艺版本");
        }
        return rows.get(0).snapshot();
    }

    @Transactional
    public ProcessPlan createDraftFromRevision(String formId, String revisionId, String changeReason) {
        return createDraftInternal(formId, revisionId, changeReason, null);
    }

    @Transactional
    public ProcessPlan createDraftFromRevision(String formId, String revisionId, String changeReason, SessionPrincipal principal) {
        requireFormalWriteAccess(formId, principal);
        return createDraftInternal(formId, revisionId, changeReason, principal.name(), principal.userId());
    }

    private ProcessPlan createDraftInternal(String formId, String revisionId, String changeReason, String trustedOperator) { return createDraftInternal(formId, revisionId, changeReason, trustedOperator, null); }
    private ProcessPlan createDraftInternal(String formId, String revisionId, String changeReason, String trustedOperator, String trustedUserId) {
        if (blank(changeReason)) {
            throw new BusinessException("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本创建草稿必须填写变更原因");
        }
        var revision = find(formId, revisionId);
        var draft = planService.restoreAsNewDraft(formId, revision.snapshot(), revision.id(), changeReason.trim());
        auditLogService.record("PROCESS_PLAN", formId, "PROCESS_PLAN_DRAFT_CREATED", trustedOperator == null ? "SYSTEM" : trustedOperator, trustedUserId,
                "sourceRevisionId=%s;changeReason=%s".formatted(revision.id(), changeReason.trim()));
        return draft;
    }

    private ProcessRevision map(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            var snapshotJson = rs.getString("snapshot_json");
            if (!constantTimeEquals(sha256(snapshotJson), rs.getString("snapshot_hash"))) {
                throw new BusinessException("PROCESS_REVISION_SNAPSHOT_INTEGRITY_ERROR", "正式工艺版本快照完整性校验失败");
            }
            return new ProcessRevision(rs.getString("id"), rs.getString("process_plan_id"), rs.getString("experiment_form_id"),
                    rs.getInt("revision_no"), rs.getString("source_revision_id"), rs.getString("change_reason"),
                    rs.getString("submitted_by"), rs.getTimestamp("submitted_at").toLocalDateTime().toString(),
                    rs.getString("snapshot_hash"), objectMapper.readValue(snapshotJson, ProcessPlan.class));
        } catch (BusinessException exception) {
            throw exception;
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

    private void requireFormalWriteAccess(String formId, SessionPrincipal principal) {
        if (principal == null || blank(principal.userId()) || blank(principal.name()) || blank(principal.role())) {
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "正式工艺操作必须使用服务端会话身份");
        }
        if ("RND_DIRECTOR".equals(principal.role())) return;
        if (!"RND_ENGINEER".equals(principal.role())) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
        }
        var owners = jdbc.query("select task.assignee_user_id, task.assignee_name from experiment_form form join rnd_task task on form.task_id = task.id where form.id = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, formId);
        if (owners.isEmpty()) {
            planService.find(formId);
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
        }
        var owner = owners.get(0);
        var allowed = !blank(owner[0]) ? owner[0].equals(principal.userId()) : legacyOwnerMatches(owner[1], principal.userId());
        if (!allowed) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
        }
    }

    private boolean legacyOwnerMatches(String name, String userId) {
        if (blank(name) || blank(userId)) return false;
        var ids = jdbc.query("select id from user_account where name = ? and status = 'ACTIVE'", (rs, row) -> rs.getString(1), name);
        return ids.size() == 1 && userId.equals(ids.get(0));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record SubmitCommand(int versionNo, boolean confirmed, String changeReason, String submittedBy) {
    }
}
