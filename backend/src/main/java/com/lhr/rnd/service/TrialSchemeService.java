package com.lhr.rnd.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.TrialSchemeCopyService;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.TrialScheme;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TrialSchemeService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProcessPlanService processPlanService;
    private final TrialSchemeCopyService copyService;
    private final TrialControlEvidenceService evidence;
    private final AuditLogService audit;

    public TrialSchemeService(JdbcTemplate jdbc, ObjectMapper objectMapper, ProcessPlanService processPlanService,
                              TrialSchemeCopyService copyService, TrialControlEvidenceService evidence, AuditLogService audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.processPlanService = processPlanService;
        this.copyService = copyService;
        this.evidence = evidence;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TrialScheme> list(String formId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, false);
        return jdbc.query("select * from experiment_trial_scheme where experiment_form_id = ? order by archived, updated_at desc, id",
                this::map, formId);
    }

    @Transactional(readOnly = true)
    public TrialScheme find(String formId, String trialId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, false);
        return findInternal(formId, trialId, false).trial();
    }

    /** Server-side provenance for promotion/confirmation checks; intentionally not accepted from or exposed by draft writes. */
    @Transactional(readOnly = true)
    public Set<String> inheritedMeasurementIds(String formId, String trialId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, false);
        return findInternal(formId, trialId, false).inheritedMeasurementIds();
    }

    /** Immutable observation fingerprints used by promotion to recognize inherited actuals even after client ID re-keying. */
    @Transactional(readOnly = true)
    public Set<String> inheritedMeasurementFingerprints(String formId, String trialId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, false);
        return findInternal(formId, trialId, false).inheritedMeasurementFingerprints();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> classifyInheritedMeasurements(String formId, String trialId, ProcessPlan plan,
                                                               SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, false);
        var inherited = findInternal(formId, trialId, false).inheritedMeasurementFingerprints();
        var result = new LinkedHashMap<String, Boolean>();
        if (plan == null || plan.majorProcesses() == null) return result;
        for (var major : plan.majorProcesses()) for (var step : values(major.steps()))
            for (var point : values(step.controlPoints())) for (var measurement : values(point.measurements())) {
                if (!blank(measurement.id())) {
                    result.put(measurement.id(), inherited.contains(copyService.measurementFingerprint(measurement)));
                }
            }
        return Map.copyOf(result);
    }

    @Transactional
    public TrialScheme create(String formId, CreateCommand command, SessionPrincipal principal) {
        requireMutationAccess(formId, principal);
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "试验方案请求不能为空");
        var normalized = copyService.normalizeNew(command.plan(), command.plannedData());
        var id = "TRIAL-" + UUID.randomUUID();
        var now = LocalDateTime.now();
        var plan = trialPlan(normalized.plan(), formId);
        validateName(command.name());
        validateLength(command.purpose(), 1000, "TRIAL_PURPOSE_INVALID", "试验目的不能超过1000个字符");
        jdbc.update("""
                insert into experiment_trial_scheme(id, experiment_form_id, version_no, name, source_trial_id, archived,
                purpose, variables, conclusion, recommendation_reason, quality_score, quality_notes, difficulty,
                plan_json, planned_data_json, inherited_actuals, inherited_measurement_ids_json, inherited_measurement_fingerprints_json, major_origins_json,
                created_by, created_by_user_id, created_at, updated_by, updated_by_user_id, updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, formId, 1, clean(command.name()), null, false, cleanNullable(command.purpose()), cleanNullable(command.variables()),
                TrialScheme.Conclusion.PENDING.name(), null, null, null, null, json(plan), json(normalized.plannedData()), false,
                json(Set.of()), json(Set.of()), json(normalized.majorOrigins()), principal.name(), principal.userId(), now, principal.name(), principal.userId(), now);
        return findInternal(formId, id, false).trial();
    }

    @Transactional
    public TrialScheme save(String formId, String trialId, SaveCommand command, SessionPrincipal principal) {
        requireMutationAccess(formId, principal);
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "试验方案请求不能为空");
        validateSave(command);
        var stored = findInternal(formId, trialId, true);
        if (stored.trial().versionNo() != command.versionNo()) throw conflict();
        var normalized = copyService.normalizeForSave(command.plan(), command.plannedData(), stored.trial().plan(),
                stored.trial().majorOrigins(), stored.inheritedMeasurementIds(), stored.inheritedMeasurementFingerprints());
        var nextVersion = command.versionNo() + 1;
        var plan = trialPlan(evidence.preserveUnchanged(normalized.plan(), stored.trial().plan()), formId);
        var updated = jdbc.update("""
                update experiment_trial_scheme set version_no=?, name=?, purpose=?, variables=?, conclusion=?, recommendation_reason=?,
                quality_score=?, quality_notes=?, difficulty=?, plan_json=?, planned_data_json=?, major_origins_json=?, updated_by=?,
                updated_by_user_id=?, updated_at=? where id=? and experiment_form_id=? and version_no=?
                """, nextVersion, clean(command.name()), cleanNullable(command.purpose()), cleanNullable(command.variables()), command.conclusion().name(),
                cleanNullable(command.recommendationReason()), command.qualityScore(), cleanNullable(command.qualityNotes()),
                command.difficulty() == null ? null : command.difficulty().name(), json(plan), json(normalized.plannedData()),
                json(normalized.majorOrigins()), principal.name(), principal.userId(), LocalDateTime.now(), trialId, formId, command.versionNo());
        if (updated != 1) throw conflict();
        return findInternal(formId, trialId, false).trial();
    }

    @Transactional
    public TrialScheme copy(String formId, String trialId, CopyCommand command, SessionPrincipal principal) {
        requireMutationAccess(formId, principal);
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "复制请求不能为空");
        validateName(command.name());
        var source = findInternal(formId, trialId, true);
        if (source.trial().versionNo() != command.versionNo()) throw conflict();
        var copied = copyService.copy(source.trial().plan(), source.trial().plannedData(), command.includeActuals(), source.trial().majorOrigins());
        var id = "TRIAL-" + UUID.randomUUID();
        var now = LocalDateTime.now();
        var plan = trialPlan(copied.plan(), formId);
        jdbc.update("""
                insert into experiment_trial_scheme(id, experiment_form_id, version_no, name, source_trial_id, archived,
                purpose, variables, conclusion, recommendation_reason, quality_score, quality_notes, difficulty,
                plan_json, planned_data_json, inherited_actuals, inherited_measurement_ids_json, inherited_measurement_fingerprints_json, major_origins_json,
                created_by, created_by_user_id, created_at, updated_by, updated_by_user_id, updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, formId, 1, clean(command.name()), trialId, false, source.trial().purpose(), source.trial().variables(),
                TrialScheme.Conclusion.PENDING.name(), null, null, null, null, json(plan), json(copied.plannedData()), command.includeActuals(),
                json(copied.inheritedMeasurementIds()), json(copied.inheritedMeasurementFingerprints()), json(copied.majorOrigins()), principal.name(), principal.userId(), now,
                principal.name(), principal.userId(), now);
        return findInternal(formId, id, false).trial();
    }

    @Transactional
    public TrialScheme archive(String formId, String trialId, ArchiveCommand command, SessionPrincipal principal) {
        requireMutationAccess(formId, principal);
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "归档请求不能为空");
        var current = findInternal(formId, trialId, true);
        if (current.trial().versionNo() != command.versionNo()) throw conflict();
        var updated = jdbc.update("update experiment_trial_scheme set archived=?, version_no=version_no+1, updated_by=?, updated_by_user_id=?, updated_at=? where id=? and experiment_form_id=? and version_no=?",
                command.archived(), principal.name(), principal.userId(), LocalDateTime.now(), trialId, formId, command.versionNo());
        if (updated != 1) throw conflict();
        return findInternal(formId, trialId, false).trial();
    }

    private StoredTrial findInternal(String formId, String trialId, boolean lock) {
        var sql = "select * from experiment_trial_scheme where id = ? and experiment_form_id = ?" + (lock ? " for update" : "");
        var rows = jdbc.query(sql, (rs, row) -> new StoredTrial(map(rs, row),
                read(rs.getString("inherited_measurement_ids_json"), new TypeReference<Set<String>>() {}),
                read(rs.getString("inherited_measurement_fingerprints_json"), new TypeReference<Set<String>>() {})), trialId, formId);
        if (rows.isEmpty()) throw invalid("TRIAL_NOT_FOUND", "试验方案不存在");
        return rows.get(0);
    }

    @Transactional
    public TrialScheme confirm(String formId, String trialId, String pointId, ConfirmCommand command, SessionPrincipal principal) {
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "确认请求不能为空");
        return confirmInternal(formId, trialId, pointId, command.trialVersionNo(), principal, false, null);
    }

    @Transactional
    public TrialScheme confirmDeviation(String formId, String trialId, String pointId, ConfirmDeviationCommand command, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        if (!"RND_DIRECTOR".equals(principal.role())) throw invalid("TRIAL_DEVIATION_CONFIRMATION_FORBIDDEN", "偏差确认仅限研发总监");
        if (command == null) throw invalid("TRIAL_REQUEST_REQUIRED", "确认请求不能为空");
        return confirmInternal(formId, trialId, pointId, command.trialVersionNo(), principal, true, command.resolutionNote());
    }

    private TrialScheme confirmInternal(String formId, String trialId, String pointId, int version, SessionPrincipal principal, boolean deviation, String note) {
        requireMutationAccess(formId, principal);
        var current = findInternal(formId, trialId, true);
        if (current.trial().versionNo() != version) throw conflict();
        if (current.trial().archived()) throw invalid("TRIAL_ARCHIVED", "归档方案不能确认或提交");
        var confirmed = evidence.confirm(current.trial().plan(), pointId, current.inheritedMeasurementFingerprints(), principal, deviation, note);
        var updated = jdbc.update("update experiment_trial_scheme set version_no=version_no+1, plan_json=?, updated_by=?, updated_by_user_id=?, updated_at=? where id=? and experiment_form_id=? and version_no=?",
                json(confirmed), principal.name(), principal.userId(), LocalDateTime.now(), trialId, formId, version);
        if (updated != 1) throw conflict();
        audit.record("TRIAL_CONTROL_POINT", trialId, deviation ? "TRIAL_CONTROL_DEVIATION_CONFIRMED" : "TRIAL_CONTROL_PASS_CONFIRMED",
                principal.name(), principal.userId(), "formId=%s;pointId=%s;trialVersionNo=%d;resolutionNote=%s".formatted(formId, pointId, version + 1, note == null ? "" : note.trim()));
        return findInternal(formId, trialId, false).trial();
    }

    private TrialScheme map(ResultSet rs, int row) throws SQLException {
        return new TrialScheme(rs.getString("id"), rs.getString("experiment_form_id"), rs.getInt("version_no"), rs.getString("name"),
                rs.getString("source_trial_id"), rs.getBoolean("archived"), rs.getString("purpose"), rs.getString("variables"),
                TrialScheme.Conclusion.valueOf(rs.getString("conclusion")), rs.getString("recommendation_reason"), rs.getBigDecimal("quality_score"),
                rs.getString("quality_notes"), enumValue(TrialScheme.Difficulty.class, rs.getString("difficulty")),
                read(rs.getString("plan_json"), ProcessPlan.class), read(rs.getString("planned_data_json"), TrialScheme.PlannedData.class),
                rs.getBoolean("inherited_actuals"), read(rs.getString("major_origins_json"), new TypeReference<Map<String, String>>() {}),
                rs.getString("created_by"), rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("updated_by"),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private void requireReadAccess(String formId, SessionPrincipal principal) {
        if (principal == null || blank(principal.userId()) || blank(principal.name()) || blank(principal.role())) {
            throw invalid("SESSION_PRINCIPAL_REQUIRED", "试验方案必须使用服务端会话身份");
        }
        processPlanService.requireDraftReadAccess(formId, principal);
    }

    private void requireMutationAccess(String formId, SessionPrincipal principal) {
        requireReadAccess(formId, principal);
        requireForm(formId, true);
    }

    private void requireForm(String formId, boolean mutable) {
        var statuses = jdbc.query("select status from experiment_form where id = ?" + (mutable ? " for update" : ""),
                (rs, row) -> rs.getString(1), formId);
        if (statuses.isEmpty()) throw invalid("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
        if (mutable && !"DRAFT".equals(statuses.get(0))) throw invalid("TRIAL_FORM_LOCKED", "实验单送测或锁定后不能修改试验方案");
    }

    private void validateSave(SaveCommand command) {
        validateName(command.name());
        validateLength(command.purpose(), 1000, "TRIAL_PURPOSE_INVALID", "试验目的不能超过1000个字符");
        validateLength(command.recommendationReason(), 1000, "TRIAL_RECOMMENDATION_REASON_INVALID", "推荐理由不能超过1000个字符");
        validateLength(command.qualityNotes(), 2000, "TRIAL_QUALITY_NOTES_INVALID", "品质说明不能超过2000个字符");
        if (command.conclusion() == null) throw invalid("TRIAL_CONCLUSION_INVALID", "试验结论不合法");
        if (command.qualityScore() != null && (command.qualityScore().compareTo(BigDecimal.ZERO) < 0
                || command.qualityScore().compareTo(BigDecimal.TEN) > 0)) {
            throw invalid("TRIAL_QUALITY_SCORE_INVALID", "品质评分必须介于0到10之间");
        }
    }

    private void validateName(String name) {
        if (blank(name) || clean(name).length() > 120) throw invalid("TRIAL_NAME_INVALID", "方案名称不能为空且不能超过120个字符");
    }

    private void validateLength(String value, int maximum, String code, String message) {
        if (value != null && value.length() > maximum) throw invalid(code, message);
    }

    private ProcessPlan trialPlan(ProcessPlan plan, String formId) {
        return new ProcessPlan(plan.id(), formId, 0, "DRAFT", plan.majorProcesses(), plan.batchYieldPercent(),
                plan.balanceToleranceKg(), false, null, null);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("试验方案JSON序列化失败", exception); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("试验方案JSON无法读取", exception); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("试验方案JSON无法读取", exception); }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) { return value == null ? null : Enum.valueOf(type, value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String clean(String value) { return value.trim(); }
    private static String cleanNullable(String value) { return blank(value) ? null : value.trim(); }
    private static BusinessException invalid(String code, String message) { return new BusinessException(code, message); }
    private static BusinessException conflict() { return invalid("TRIAL_VERSION_CONFLICT", "试验方案已被更新，请刷新后重试"); }

    private static <T> List<T> values(List<T> values) { return values == null ? List.of() : values; }

    private record StoredTrial(TrialScheme trial, Set<String> inheritedMeasurementIds,
                               Set<String> inheritedMeasurementFingerprints) {}

    public record CreateCommand(String name, String purpose, String variables, ProcessPlan plan, TrialScheme.PlannedData plannedData) {}
    public record SaveCommand(int versionNo, String name, String purpose, String variables, TrialScheme.Conclusion conclusion,
                              String recommendationReason, BigDecimal qualityScore, String qualityNotes, TrialScheme.Difficulty difficulty,
                              ProcessPlan plan, TrialScheme.PlannedData plannedData) {}
    public record CopyCommand(int versionNo, String name, boolean includeActuals) {}
    public record ArchiveCommand(int versionNo, boolean archived) {}
    public record ConfirmCommand(int trialVersionNo) {}
    public record ConfirmDeviationCommand(int trialVersionNo, String resolutionNote) {}
}
