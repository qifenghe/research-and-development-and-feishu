package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.domain.TrialSchemeCopyService;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import com.lhr.rnd.model.ProcessSubmissionCheck;
import com.lhr.rnd.model.TrialScheme;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TrialPromotionService {
    private final JdbcTemplate jdbc;
    private final TrialSchemeService trials;
    private final ProcessPlanService plans;
    private final ProcessRevisionService revisions;
    private final TrialSchemeCopyService copies;
    private final TrialControlEvidenceService evidence;
    private final ObjectMapper mapper;
    private final ProcessSubmissionValidator validator = new ProcessSubmissionValidator();

    public TrialPromotionService(JdbcTemplate jdbc, TrialSchemeService trials, ProcessPlanService plans,
                                 ProcessRevisionService revisions, TrialSchemeCopyService copies,
                                 TrialControlEvidenceService evidence, ObjectMapper mapper) {
        this.jdbc = jdbc; this.trials = trials; this.plans = plans; this.revisions = revisions;
        this.copies = copies; this.evidence = evidence; this.mapper = mapper;
    }

    @Transactional
    public Preview preview(String formId, String trialId, PreviewCommand command, SessionPrincipal principal) {
        if (command == null) throw error("TRIAL_REQUEST_REQUIRED", "预览请求不能为空");
        lock(formId, trialId, principal, true);
        var current = current(formId, trialId, command.trialVersionNo(), command.expectedProcessVersionNo(), principal);
        var checks = validator.validate(current.trial().plan());
        var token = "TPRE-" + UUID.randomUUID();
        var hash = snapshotHash(current);
        jdbc.update("insert into experiment_trial_submission_preview(token,experiment_form_id,trial_id,trial_version_no,process_version_no,snapshot_hash,created_by_user_id,created_at) values (?,?,?,?,?,?,?,?)",
                token, formId, trialId, command.trialVersionNo(), command.expectedProcessVersionNo(), hash, principal.userId(), LocalDateTime.now());
        return new Preview(command.trialVersionNo(), command.expectedProcessVersionNo(), checks, differingDraft(current), token, hash);
    }

    @Transactional
    public ProcessRevision submit(String formId, String trialId, SubmitCommand command, SessionPrincipal principal) {
        if (command == null) throw error("TRIAL_REQUEST_REQUIRED", "提交请求不能为空");
        if (!command.confirmed()) throw error("PROCESS_SUBMISSION_CONFIRMATION_REQUIRED", "正式提交前必须明确确认");
        if (blank(command.idempotencyKey()) || command.idempotencyKey().length() > 120) throw error("TRIAL_IDEMPOTENCY_KEY_REQUIRED", "请提供不超过120字符的提交请求标识");
        if (command.changeReason() != null && command.changeReason().length() > 1000) throw error("PROCESS_CHANGE_REASON_INVALID", "变更原因不能超过1000字符");
        // Serialize requests for a form even when no normalized process plan exists yet.
        lock(formId, trialId, principal, false);
        var requestHash = hash(json(new BoundRequest(trialId, principal.userId(), command)));
        var existing = jdbc.query("select request_hash, revision_id from experiment_trial_promotion where experiment_form_id=? and idempotency_key=?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, formId, command.idempotencyKey());
        if (!existing.isEmpty()) {
            if (!equal(existing.get(0)[0], requestHash)) throw error("TRIAL_IDEMPOTENCY_CONFLICT", "请求标识已用于不同的提交内容");
            return revisions.find(formId, existing.get(0)[1], principal);
        }
        requireEditable(formId);
        var current = current(formId, trialId, command.trialVersionNo(), command.expectedProcessVersionNo(), principal);
        var previews = jdbc.query("select snapshot_hash from experiment_trial_submission_preview where token=? and experiment_form_id=? and trial_id=? and trial_version_no=? and process_version_no=? and created_by_user_id=?",
                (rs, row) -> rs.getString(1), command.previewToken(), formId, trialId, command.trialVersionNo(), command.expectedProcessVersionNo(), principal.userId());
        if (previews.isEmpty() || !equal(previews.get(0), snapshotHash(current))) throw error("TRIAL_PREVIEW_STALE", "预览已失效，请重新检查当前方案及正式草稿");
        var checks = validator.validate(current.trial().plan());
        if (!checks.ready()) throw error("PROCESS_PLAN_NOT_READY", "试验方案未达到正式提交条件");
        var displaced = differingDraft(current) ? json(current.active()) : null;
        // All client-supplied evidence was stripped at create/save. Only persisted confirmation survives remapping.
        var mapped = copies.normalizeNew(current.trial().plan(), current.trial().plannedData()).plan();
        mapped = evidence.transferRemapped(current.trial().plan(), mapped,
                trials.inheritedMeasurementFingerprints(formId, trialId, principal));
        var active = current.active();
        if ("SUBMITTED".equals(active.status())) {
            if (blank(command.changeReason())) throw error("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本提交新方案必须填写变更原因");
            var latest = revisions.latestOrNull(formId);
            if (latest == null) throw error("PROCESS_FORMAL_REVISION_REQUIRED", "当前正式版本不存在");
            active = revisions.createDraftFromRevision(formId, latest.id(), command.changeReason(), principal);
        }
        var request = new ProcessPlan(active.id(), formId, active.versionNo(), "DRAFT", mapped.majorProcesses(), null,
                mapped.balanceToleranceKg(), false, active.sourceRevisionId(), active.changeReason());
        var saved = plans.savePromotedTrial(formId, request);
        // The existing formal gate validates the persisted, recalculated graph and performs the revision transition.
        var revision = revisions.submit(formId, new ProcessRevisionService.SubmitCommand(saved.versionNo(), true, command.changeReason(), principal.name()), principal);
        var trialSnapshot = json(current.trial());
        jdbc.update("""
                insert into experiment_trial_promotion(id,experiment_form_id,trial_id,trial_version_no,trial_name,
                trial_snapshot_json,trial_snapshot_hash,displaced_draft_json,idempotency_key,request_hash,preview_token,
                revision_id,promoted_by,promoted_by_user_id,promoted_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, "TPROM-" + UUID.randomUUID(), formId, trialId, current.trial().versionNo(), current.trial().name(),
                trialSnapshot, hash(trialSnapshot), displaced, command.idempotencyKey(), requestHash, command.previewToken(),
                revision.id(), principal.name(), principal.userId(), LocalDateTime.now());
        return revision;
    }

    /** Formal authorization, including the exact revision pinned to an active tester assignment. No draft fields. */
    @Transactional(readOnly = true)
    public SourceMetadata source(String formId, String revisionId, SessionPrincipal principal) {
        revisions.find(formId, revisionId, principal);
        var rows = jdbc.query("select id,trial_id,trial_name,trial_version_no,trial_snapshot_hash,promoted_by,promoted_at from experiment_trial_promotion where experiment_form_id=? and revision_id=?",
                (rs, row) -> new SourceMetadata(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime().toString()), formId, revisionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Recovery is draft-authorized even when a caller can see the revision's public source metadata. */
    @Transactional(readOnly = true)
    public ProcessPlan displacedDraft(String formId, String revisionId, SessionPrincipal principal) {
        if (principal == null || blank(principal.userId()) || blank(principal.name())) throw error("SESSION_PRINCIPAL_REQUIRED", "必须使用服务端会话身份");
        plans.requireDraftReadAccess(formId, principal);
        revisions.find(formId, revisionId, principal);
        var rows = jdbc.query("select displaced_draft_json from experiment_trial_promotion where experiment_form_id=? and revision_id=?",
                (rs, row) -> rs.getString(1), formId, revisionId);
        if (rows.isEmpty() || rows.get(0) == null) return null;
        try { return mapper.readValue(rows.get(0), ProcessPlan.class); }
        catch (Exception e) { throw new IllegalStateException("晋升前草稿快照无法读取", e); }
    }

    private void lock(String formId, String trialId, SessionPrincipal principal, boolean editable) {
        trials.find(formId, trialId, principal);
        jdbc.query("select id from experiment_form where id=? for update", (rs, row) -> rs.getString(1), formId);
        if (editable) requireEditable(formId);
        jdbc.query("select id from experiment_process_plan where experiment_form_id=? for update", (rs, row) -> rs.getString(1), formId);
        jdbc.query("select id from experiment_trial_scheme where experiment_form_id=? and id=? for update", (rs, row) -> rs.getString(1), formId, trialId);
    }

    private void requireEditable(String formId) {
        if (!"DRAFT".equals(jdbc.queryForObject("select status from experiment_form where id=?", String.class, formId))) throw error("TRIAL_FORM_LOCKED", "送测或锁定后不能晋升试验方案");
    }
    private Current current(String form, String trialId, int trialVersion, int processVersion, SessionPrincipal principal) {
        var trial = trials.find(form, trialId, principal);
        if (trial.versionNo() != trialVersion) throw error("TRIAL_VERSION_CONFLICT", "试验方案已更新，请刷新后重试");
        if (trial.archived()) throw error("TRIAL_ARCHIVED", "归档方案不能确认或提交");
        var active = plans.find(form);
        if (active.versionNo() != processVersion) throw error("PROCESS_PLAN_VERSION_CONFLICT", "正式工艺已更新，请重新预览");
        return new Current(trial, active);
    }
    private boolean differingDraft(Current current) {
        return (!current.active().legacy() || !current.active().majorProcesses().isEmpty()) && "DRAFT".equals(current.active().status())
                && !evidence.graphContent(current.trial().plan()).equals(evidence.graphContent(current.active()));
    }
    private String snapshotHash(Current current) { return hash(json(current)); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("晋升快照序列化失败", e); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private boolean equal(String a, String b) { return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static BusinessException error(String code, String message) { return new BusinessException(code, message); }
    private record Current(TrialScheme trial, ProcessPlan active) {}
    private record BoundRequest(String trialId, String userId, SubmitCommand command) {}
    public record PreviewCommand(int trialVersionNo, int expectedProcessVersionNo) {}
    public record SubmitCommand(int trialVersionNo, int expectedProcessVersionNo, String previewToken, boolean confirmed, String changeReason, String idempotencyKey) {}
    public record Preview(int trialVersionNo, int expectedProcessVersionNo, ProcessSubmissionCheck checks, boolean differingDraft, String previewToken, String previewHash) {}
    public record SourceMetadata(String promotionId, String trialId, String trialName, int trialVersionNo, String trialSnapshotHash, String promotedBy, String promotedAt) {}
}
