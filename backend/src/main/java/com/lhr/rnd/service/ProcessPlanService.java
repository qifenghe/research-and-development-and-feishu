package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.persistence.repository.ExperimentFormRepository;
import com.lhr.rnd.persistence.repository.ExperimentProcessRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessPlanService {
    private final JdbcTemplate jdbc;
    private final ExperimentFormRepository formRepository;
    private final ExperimentProcessRepository legacyRepository;
    private final AuditLogService auditLogService;
    private final ProcessPlanCalculationService calculations = new ProcessPlanCalculationService();

    public ProcessPlanService(
            JdbcTemplate jdbc,
            ExperimentFormRepository formRepository,
            ExperimentProcessRepository legacyRepository,
            AuditLogService auditLogService
    ) {
        this.jdbc = jdbc;
        this.formRepository = formRepository;
        this.legacyRepository = legacyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public ProcessPlan find(String formId) {
        requireForm(formId);
        var plans = jdbc.query("select * from experiment_process_plan where experiment_form_id = ?",
                (rs, row) -> new PlanHeader(rs.getString("id"), rs.getInt("version_no"), rs.getString("status"),
                        rs.getBigDecimal("balance_tolerance_kg"), rs.getString("source_revision_id"),
                        rs.getString("change_reason")), formId);
        if (plans.isEmpty()) return legacyPlan(formId);
        var header = plans.get(0);
        var majors = jdbc.query("select * from experiment_major_process where process_plan_id = ? order by sequence",
                (rs, row) -> mapMajor(rs), header.id());
        var calculated = majors.stream().map(this::withYield).toList();
        var plan = new ProcessPlan(header.id(), formId, header.versionNo(), header.status(), calculated, null,
                header.balanceToleranceKg(), false, header.sourceRevisionId(), header.changeReason());
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), plan.majorProcesses(),
                calculations.calculateBatch(plan), plan.balanceToleranceKg(), false, plan.sourceRevisionId(), plan.changeReason());
    }

    @Transactional
    public ProcessPlan save(String formId, ProcessPlan request) {
        return saveInternal(formId, request);
    }

    @Transactional
    public ProcessPlan save(String formId, ProcessPlan request, SessionPrincipal principal) {
        requireDraftAccess(formId, principal);
        return saveInternal(formId, sanitizeControlConfirmations(formId, request, principal));
    }

    private ProcessPlan saveInternal(String formId, ProcessPlan request) {
        return saveInternal(formId, request, false);
    }

    /** Package-only promotion path: IDs were freshly allocated by the trusted trial graph remapper. */
    @Transactional
    ProcessPlan savePromotedTrial(String formId, ProcessPlan request) {
        return saveInternal(formId, request, true);
    }

    private ProcessPlan saveInternal(String formId, ProcessPlan request, boolean preserveMaterialIds) {
        requireForm(formId);
        var balanceTolerance = requireBalanceTolerance(request.balanceToleranceKg());
        var current = jdbc.query("select id, version_no, status, balance_tolerance_kg, source_revision_id, change_reason from experiment_process_plan where experiment_form_id = ?",
                (rs, row) -> new PlanHeader(rs.getString("id"), rs.getInt("version_no"), rs.getString("status"),
                        rs.getBigDecimal("balance_tolerance_kg"), rs.getString("source_revision_id"), rs.getString("change_reason")), formId);
        String planId;
        int nextVersion;
        if (current.isEmpty()) {
            if (request.versionNo() > 1) throw conflict();
            planId = id("PLAN");
            nextVersion = 1;
            try {
                jdbc.update("insert into experiment_process_plan(id, experiment_form_id, version_no, status, calculation_mode, balance_tolerance_kg, created_at, updated_at) values (?,?,?,?,?,?,?,?)",
                        planId, formId, nextVersion, "DRAFT", "PRIMARY_INPUT", balanceTolerance, LocalDateTime.now(), LocalDateTime.now());
            } catch (DataIntegrityViolationException exception) {
                if (isProcessPlanFormUniqueViolation(exception)) throw conflict();
                throw exception;
            }
        } else {
            var stored = current.get(0);
            if (request.versionNo() != stored.versionNo()) throw conflict();
            if (!"DRAFT".equals(stored.status())) throw conflict();
            planId = stored.id();
            nextVersion = stored.versionNo() + 1;
            // Take the draft write lock before deleting the graph. A stale save must never be able to
            // replace children after another transaction has submitted or saved this plan.
            var updated = jdbc.update("update experiment_process_plan set version_no = ?, balance_tolerance_kg = ?, updated_at = ? where id = ? and version_no = ? and status = ?",
                    nextVersion, balanceTolerance, LocalDateTime.now(), planId, stored.versionNo(), "DRAFT");
            if (updated != 1) throw conflict();
            // Remove referencing materials before the cascaded step-output deletion so STEP_OUTPUT foreign keys stay valid.
            jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id where major.process_plan_id = ?)", planId);
            jdbc.update("delete from experiment_major_process where process_plan_id = ?", planId);
        }
        var majors = request.majorProcesses() == null ? List.<ProcessPlan.MajorProcess>of() : request.majorProcesses();
        for (int index = 0; index < majors.size(); index++) saveMajor(planId, index + 1, majors.get(index), preserveMaterialIds);
        return find(formId);
    }

    @Transactional
    public ProcessPlan confirmCriticalDeviation(String formId, String controlPointId, String resolutionNote,
                                                SessionPrincipal principal) {
        if (principal == null || !"RND_DIRECTOR".equals(principal.role()) || blank(principal.userId()) || blank(principal.name())) {
            throw new BusinessException("PROCESS_DEVIATION_CONFIRMATION_FORBIDDEN", "极重要偏差只能由研发负责人或总监确认");
        }
        var points = jdbc.query("""
                select cp.lower_limit, cp.upper_limit
                from experiment_control_point cp
                join experiment_minor_step step on step.id = cp.minor_step_id
                join experiment_major_process major on major.id = step.major_process_id
                join experiment_process_plan plan on plan.id = major.process_plan_id
                where cp.id = ? and plan.experiment_form_id = ? and plan.status = 'DRAFT' and cp.importance = 'CRITICAL'
                for update
                """, (rs, row) -> new ControlLimits(rs.getBigDecimal(1), rs.getBigDecimal(2)), controlPointId, formId);
        if (points.isEmpty()) {
            throw new BusinessException("PROCESS_CONTROL_POINT_NOT_CONFIRMABLE", "极重要偏差不存在或当前工艺不是可确认草稿");
        }
        var limits = points.get(0);
        var measurements = jdbc.query("select measured_value, result, deviation_action, retest_result from experiment_control_measurement where control_point_id = ? order by sequence",
                (rs, row) -> new DeviationMeasurement(rs.getBigDecimal(1), rs.getString(2), rs.getString(3), rs.getString(4)), controlPointId);
        var deviations = measurements.stream().filter(item -> item.isDeviation(limits)).toList();
        if (deviations.isEmpty() || deviations.stream().anyMatch(item -> blank(item.deviationAction()) || !"PASS".equals(item.retestResult()))) {
            throw new BusinessException("PROCESS_DEVIATION_NOT_RESOLVED", "偏差处理与复测合格记录完整后才能确认");
        }
        var confirmedAt = LocalDateTime.now();
        var updated = jdbc.update("update experiment_control_point set resolved = true, confirmed_by = ?, confirmed_at = ?, basis_or_remark = coalesce(?, basis_or_remark) where id = ?",
                principal.name(), confirmedAt, blank(resolutionNote) ? null : resolutionNote.trim(), controlPointId);
        if (updated != 1) throw new BusinessException("PROCESS_CONTROL_POINT_NOT_CONFIRMABLE", "极重要偏差确认失败");
        auditLogService.record("PROCESS_CONTROL_POINT", controlPointId, "CRITICAL_DEVIATION_CONFIRMED",
                principal.name(), principal.userId(), "formId=%s;confirmedAt=%s;note=%s".formatted(formId, confirmedAt, valueOr(resolutionNote, "")));
        return find(formId);
    }

    private ProcessPlan sanitizeControlConfirmations(String formId, ProcessPlan request, SessionPrincipal principal) {
        var majors = values(request.majorProcesses()).stream().map(major -> new ProcessPlan.MajorProcess(
                major.id(), major.sequence(), major.processCode(), major.processName(), major.description(), major.yieldBasis(), major.remark(),
                values(major.steps()).stream().map(step -> new ProcessPlan.MinorStep(step.id(), step.sequence(), step.stepCode(), step.stepName(),
                        step.stepType(), step.parameter1Name(), step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(),
                        step.parameter2Value(), step.parameter2Unit(), step.equipment(), step.instruction(), step.materials(), step.outputs(),
                        values(step.controlPoints()).stream().map(point -> sanitizeControlConfirmation(formId, major, step, point, principal)).toList())).toList(),
                major.inputs(), major.outputs(), major.yield())).toList();
        return new ProcessPlan(request.id(), request.experimentFormId(), request.versionNo(), request.status(), majors,
                request.batchYieldPercent(), request.balanceToleranceKg(), request.legacy(), request.sourceRevisionId(), request.changeReason());
    }

    private ProcessPlan.ControlPoint sanitizeControlConfirmation(String formId, ProcessPlan.MajorProcess major,
                                                                 ProcessPlan.MinorStep step, ProcessPlan.ControlPoint point, SessionPrincipal principal) {
        var persisted = persistedControlPoint(formId, point.id());
        var inheritConfirmation = persisted != null
                && (!hasDeviation(point) || persisted.point().resolved())
                && !blank(persisted.point().confirmedBy())
                && !blank(persisted.point().confirmedAt())
                && sameControlLocation(major, step, persisted)
                && sameControlDefinition(point, persisted.point())
                && sameMeasurements(point.measurements(), persisted.point().measurements());
        var confirmPassing = "CRITICAL".equals(point.importance()) && !hasDeviation(point)
                && "__SESSION_CONFIRMATION_REQUESTED__".equals(point.confirmedBy())
                && !values(point.measurements()).isEmpty()
                && values(point.measurements()).stream().allMatch(item -> item.measuredValue() != null && "PASS".equals(item.result()));
        var trustedBy = confirmPassing ? principal.name() : inheritConfirmation ? persisted.point().confirmedBy() : null;
        var trustedAt = confirmPassing ? LocalDateTime.now().toString() : inheritConfirmation ? persisted.point().confirmedAt() : null;
        if (confirmPassing) auditLogService.record("PROCESS_CONTROL_POINT", point.id(), "CRITICAL_PASS_CONFIRMED",
                principal.name(), principal.userId(), "formId=" + formId);
        return new ProcessPlan.ControlPoint(point.id(), point.sequence(), point.controlType(), point.importance(), point.itemName(),
                point.targetValue(), point.lowerLimit(), point.upperLimit(), point.unit(), point.method(), point.measurementTool(),
                point.frequency(), point.deviationAction(), inheritConfirmation && persisted.point().resolved(), trustedBy, trustedAt, point.basisOrRemark(), point.measurements());
    }

    private PersistedControlPoint persistedControlPoint(String formId, String controlPointId) {
        if (blank(controlPointId)) return null;
        var points = jdbc.query("""
                select cp.*, step.id as persisted_step_id, step.sequence as persisted_step_sequence,
                       step.step_code as persisted_step_code, step.step_name as persisted_step_name,
                       major.id as persisted_major_id, major.sequence as persisted_major_sequence,
                       major.process_code as persisted_process_code, major.process_name as persisted_process_name
                from experiment_control_point cp
                join experiment_minor_step step on step.id = cp.minor_step_id
                join experiment_major_process major on major.id = step.major_process_id
                join experiment_process_plan plan on plan.id = major.process_plan_id
                where plan.experiment_form_id = ? and cp.id = ?
                for update
                """, (rs, row) -> new PersistedControlPoint(mapControlPoint(rs), rs.getString("persisted_major_id"),
                rs.getInt("persisted_major_sequence"), rs.getString("persisted_process_code"), rs.getString("persisted_process_name"),
                rs.getString("persisted_step_id"), rs.getInt("persisted_step_sequence"), rs.getString("persisted_step_code"),
                rs.getString("persisted_step_name")), formId, controlPointId);
        return points.isEmpty() ? null : points.get(0);
    }

    private boolean sameControlLocation(ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step,
                                        PersistedControlPoint persisted) {
        return !blank(major.id())
                && !blank(step.id())
                && major.id().equals(persisted.majorId())
                && step.id().equals(persisted.stepId());
    }

    private boolean hasDeviation(ProcessPlan.ControlPoint point) {
        return values(point.measurements()).stream().anyMatch(item -> "FAIL".equals(item.result())
                || item.measuredValue() != null && (point.lowerLimit() != null && item.measuredValue().compareTo(point.lowerLimit()) < 0
                || point.upperLimit() != null && item.measuredValue().compareTo(point.upperLimit()) > 0));
    }

    private boolean sameControlDefinition(ProcessPlan.ControlPoint requested, ProcessPlan.ControlPoint persisted) {
        return equal(requested.controlType(), persisted.controlType())
                && equal(requested.importance(), persisted.importance())
                && equal(requested.itemName(), persisted.itemName())
                && equal(requested.targetValue(), persisted.targetValue())
                && equal(requested.lowerLimit(), persisted.lowerLimit())
                && equal(requested.upperLimit(), persisted.upperLimit())
                && equal(requested.unit(), persisted.unit())
                && equal(requested.method(), persisted.method())
                && equal(requested.measurementTool(), persisted.measurementTool())
                && equal(requested.frequency(), persisted.frequency())
                && equal(requested.deviationAction(), persisted.deviationAction())
                && equal(requested.basisOrRemark(), persisted.basisOrRemark());
    }

    private boolean sameMeasurements(List<ProcessPlan.ControlMeasurement> requested, List<ProcessPlan.ControlMeasurement> persisted) {
        var incoming = values(requested);
        var stored = values(persisted);
        if (incoming.size() != stored.size()) return false;
        for (var measurement : incoming) {
            if (blank(measurement.id())) return false;
            var previous = stored.stream().filter(item -> measurement.id().equals(item.id())).findFirst().orElse(null);
            if (previous == null || !sameMeasurement(measurement, previous)) return false;
        }
        return true;
    }

    private boolean sameMeasurement(ProcessPlan.ControlMeasurement requested, ProcessPlan.ControlMeasurement persisted) {
        return equal(requested.measuredValue(), persisted.measuredValue())
                && equal(requested.measuredAt(), persisted.measuredAt())
                && equal(requested.result(), persisted.result())
                && equal(requested.deviationAction(), persisted.deviationAction())
                && equal(requested.retestResult(), persisted.retestResult())
                && equal(requested.remark(), persisted.remark());
    }

    private boolean equal(Object left, Object right) {
        if (left instanceof BigDecimal leftNumber && right instanceof BigDecimal rightNumber) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        return java.util.Objects.equals(left, right);
    }

    @Transactional(readOnly = true)
    public void requireDraftWriteAccess(String formId, SessionPrincipal principal) {
        requireDraftAccess(formId, principal);
    }

    @Transactional(readOnly = true)
    public void requireDraftReadAccess(String formId, SessionPrincipal principal) {
        requireDraftAccess(formId, principal);
    }

    private void requireDraftAccess(String formId, SessionPrincipal principal) {
        if (principal == null || blank(principal.userId()) || blank(principal.role())) {
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "访问工艺草稿必须使用服务端会话身份");
        }
        if ("RND_DIRECTOR".equals(principal.role())) return;
        if (!"RND_ENGINEER".equals(principal.role())) {
            throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
        }
        var owners = jdbc.query("select task.assignee_user_id, task.assignee_name from experiment_form form join rnd_task task on form.task_id = task.id where form.id = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, formId);
        if (owners.isEmpty()) throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
        var owner = owners.get(0);
        var allowed = !blank(owner[0]) ? owner[0].equals(principal.userId()) : legacyOwnerMatches(owner[1], principal.userId());
        if (!allowed) throw new BusinessException("PROCESS_PLAN_FORM_FORBIDDEN", "当前用户无权操作该工艺单");
    }

    @Transactional(readOnly = true)
    public boolean canReadForComparison(String formId, SessionPrincipal principal) {
        try { requireDraftAccess(formId, principal); return true; }
        catch (BusinessException ex) {
            if ("PROCESS_PLAN_FORM_FORBIDDEN".equals(ex.code())) return false;
            throw ex;
        }
    }

    @Transactional
    public ProcessPlan restoreAsNewDraft(String formId, ProcessPlan snapshot, String sourceRevisionId, String changeReason) {
        requireForm(formId);
        if (sourceRevisionId == null || sourceRevisionId.isBlank() || changeReason == null || changeReason.isBlank()) {
            throw new BusinessException("PROCESS_CHANGE_REASON_REQUIRED", "从正式版本创建草稿必须填写变更原因");
        }
        var current = jdbc.query("select id, version_no, status, balance_tolerance_kg, source_revision_id, change_reason from experiment_process_plan where experiment_form_id = ?",
                (rs, row) -> new PlanHeader(rs.getString("id"), rs.getInt("version_no"), rs.getString("status"),
                        rs.getBigDecimal("balance_tolerance_kg"), rs.getString("source_revision_id"), rs.getString("change_reason")), formId);
        if (current.isEmpty()) throw conflict();
        var stored = current.get(0);
        if (!"SUBMITTED".equals(stored.status())) {
            throw conflict();
        }
        var nextVersion = stored.versionNo() + 1;
        var updated = jdbc.update("update experiment_process_plan set version_no = ?, status = ?, balance_tolerance_kg = ?, source_revision_id = ?, change_reason = ?, updated_at = ? where id = ? and version_no = ? and status = ?",
                nextVersion, "DRAFT", requireBalanceTolerance(snapshot.balanceToleranceKg()), sourceRevisionId, changeReason.trim(),
                LocalDateTime.now(), stored.id(), stored.versionNo(), "SUBMITTED");
        if (updated != 1) throw conflict();
        jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id where major.process_plan_id = ?)", stored.id());
        jdbc.update("delete from experiment_major_process where process_plan_id = ?", stored.id());
        var majors = snapshot.majorProcesses() == null ? List.<ProcessPlan.MajorProcess>of() : snapshot.majorProcesses();
        for (int index = 0; index < majors.size(); index++) saveMajor(stored.id(), index + 1, majors.get(index));
        return find(formId);
    }

    @Transactional
    public void markSubmitted(String formId, int versionNo, String changeReason) {
        requireForm(formId);
        var updated = jdbc.update("update experiment_process_plan set status = ?, change_reason = coalesce(change_reason, ?), updated_at = ? where experiment_form_id = ? and version_no = ? and status = ?",
                "SUBMITTED", changeReason, LocalDateTime.now(), formId, versionNo, "DRAFT");
        if (updated != 1) throw conflict();
    }

    public List<com.lhr.rnd.model.ExperimentProcessStep> legacySummaries(ProcessPlan plan) {
        if (plan == null || plan.majorProcesses() == null) return List.of();
        return plan.majorProcesses().stream().map(major -> {
            var yield = calculations.calculate(major);
            var reusable = yield.reusableOutputWeightKg();
            var loss = yield.balanceDifferenceKg().max(BigDecimal.ZERO);
            var lossRate = yield.totalInputWeightKg().signum() == 0 ? null
                    : loss.divide(yield.totalInputWeightKg(), 6, java.math.RoundingMode.HALF_UP);
            return new com.lhr.rnd.model.ExperimentProcessStep(major.sequence(), major.processName(),
                    yield.totalInputWeightKg(), yield.qualifiedOutputWeightKg(), reusable,
                    reusable.signum() > 0 ? "REUSE" : null, loss, lossRate, major.remark());
        }).toList();
    }

    private void saveMajor(String planId, int sequence, ProcessPlan.MajorProcess major) {
        saveMajor(planId, sequence, major, false);
    }

    private void saveMajor(String planId, int sequence, ProcessPlan.MajorProcess major, boolean preserveMaterialIds) {
        if (major.processName() == null || major.processName().isBlank())
            throw new BusinessException("PROCESS_NAME_REQUIRED", "大工序名称不能为空");
        var majorId = valueOr(major.id(), id("MP"));
        jdbc.update("insert into experiment_major_process(id, process_plan_id, sequence, process_code, process_name, description, yield_basis, remark) values (?,?,?,?,?,?,?,?)",
                majorId, planId, sequence, major.processCode(), major.processName(), major.description(),
                valueOr(major.yieldBasis(), "PRIMARY_INPUT"), major.remark());
        var steps = major.steps() == null ? List.<ProcessPlan.MinorStep>of() : major.steps();
        for (int index = 0; index < steps.size(); index++) saveStep(majorId, index + 1, steps.get(index), preserveMaterialIds);
        var inputs = major.inputs() == null ? List.<ProcessPlan.ProcessInput>of() : major.inputs();
        for (int index = 0; index < inputs.size(); index++) {
            var item = inputs.get(index);
            requireNonNegative(item.weightKg());
            jdbc.update("insert into experiment_process_input(id, major_process_id, sequence, input_role, material_code, material_name, weight_kg, source_step_material_id) values (?,?,?,?,?,?,?,?)",
                    id("IN"), majorId, index + 1, valueOr(item.inputRole(), "AUXILIARY"), item.materialCode(),
                    valueOr(item.materialName(), "未命名物料"), item.weightKg(), item.sourceStepMaterialId());
        }
        var outputs = major.outputs() == null ? List.<ProcessPlan.ProcessOutput>of() : major.outputs();
        for (int index = 0; index < outputs.size(); index++) {
            var item = outputs.get(index);
            requireNonNegative(item.weightKg());
            jdbc.update("insert into experiment_process_output(id, major_process_id, sequence, output_type, weight_kg, remark) values (?,?,?,?,?,?)",
                    id("OUT"), majorId, index + 1, valueOr(item.outputType(), "QUALIFIED"), item.weightKg(), item.remark());
        }
    }

    private boolean legacyOwnerMatches(String name, String userId) {
        if (blank(name) || blank(userId)) return false;
        var ids = jdbc.query("select id from user_account where name = ? and status = 'ACTIVE'", (rs, row) -> rs.getString(1), name);
        return ids.size() == 1 && userId.equals(ids.get(0));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void saveStep(String majorId, int sequence, ProcessPlan.MinorStep step, boolean preserveMaterialIds) {
        if (step.stepName() == null || step.stepName().isBlank())
            throw new BusinessException("STEP_NAME_REQUIRED", "小步骤名称不能为空");
        calculations.validateStep(step);
        var stepId = valueOr(step.id(), id("ST"));
        jdbc.update("insert into experiment_minor_step(id, major_process_id, sequence, step_code, step_name, step_type, parameter_1_name, parameter_1_value, parameter_1_unit, parameter_2_name, parameter_2_value, parameter_2_unit, equipment, instruction) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                stepId, majorId, sequence, step.stepCode(), step.stepName(), valueOr(step.stepType(), "NORMAL"),
                step.parameter1Name(), step.parameter1Value(), step.parameter1Unit(), step.parameter2Name(),
                step.parameter2Value(), step.parameter2Unit(), step.equipment(), step.instruction());
        var outputs = step.outputs() == null ? List.<ProcessPlan.StepOutput>of() : step.outputs();
        for (int index = 0; index < outputs.size(); index++) {
            var item = outputs.get(index);
            requireNonNegative(item.weightKg());
            jdbc.update("insert into experiment_step_output(id, minor_step_id, sequence, output_type, output_name, material_state, weight_kg, primary_output, continue_flow, remark) values (?,?,?,?,?,?,?,?,?,?)",
                    valueOr(item.id(), id("SOUT")), stepId, index + 1, valueOr(item.outputType(), "INTERMEDIATE"),
                    valueOr(item.outputName(), "未命名产出"), valueOr(item.materialState(), "SEMI_SOLID"), item.weightKg(),
                    item.primaryOutput(), item.continueFlow(), item.remark());
        }
        var materials = step.materials() == null ? List.<ProcessPlan.StepMaterial>of() : step.materials();
        for (int index = 0; index < materials.size(); index++) {
            var item = materials.get(index);
            requireNonNegative(item.weightKg());
            jdbc.update("insert into experiment_step_material(id, minor_step_id, sequence, material_role, material_code, material_name, material_state, weight_kg, formula_material_id, remark, source_type, source_step_output_id) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                    preserveMaterialIds ? valueOr(item.id(), id("MAT")) : id("MAT"), stepId, index + 1, valueOr(item.materialRole(), "AUXILIARY"), item.materialCode(),
                    valueOr(item.materialName(), "未命名物料"), valueOr(item.materialState(), "SOLID"), item.weightKg(),
                    item.formulaMaterialId(), item.remark(), valueOr(item.sourceType(), "EXTERNAL"), item.sourceStepOutputId());
        }
        var controlPoints = step.controlPoints() == null ? List.<ProcessPlan.ControlPoint>of() : step.controlPoints();
        for (int index = 0; index < controlPoints.size(); index++) {
            saveControlPoint(stepId, index + 1, controlPoints.get(index));
        }
    }

    private void saveControlPoint(String stepId, int sequence, ProcessPlan.ControlPoint point) {
        var pointId = valueOr(point.id(), id("CP"));
        jdbc.update("insert into experiment_control_point(id, minor_step_id, sequence, control_type, importance, item_name, target_value, lower_limit, upper_limit, unit, method, measurement_tool, frequency, deviation_action, resolved, confirmed_by, confirmed_at, basis_or_remark) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                pointId, stepId, sequence, valueOr(point.controlType(), "QUALITY"), valueOr(point.importance(), "NORMAL"),
                valueOr(point.itemName(), "未命名控制点"), point.targetValue(), point.lowerLimit(), point.upperLimit(), point.unit(),
                point.method(), point.measurementTool(), point.frequency(), point.deviationAction(), point.resolved(), point.confirmedBy(),
                timestamp(point.confirmedAt()), point.basisOrRemark());
        var measurements = point.measurements() == null ? List.<ProcessPlan.ControlMeasurement>of() : point.measurements();
        for (int index = 0; index < measurements.size(); index++) {
            var item = measurements.get(index);
            jdbc.update("insert into experiment_control_measurement(id, control_point_id, sequence, measured_value, measured_at, result, deviation_action, retest_result, remark) values (?,?,?,?,?,?,?,?,?)",
                    valueOr(item.id(), id("CM")), pointId, index + 1, item.measuredValue(), timestamp(item.measuredAt()),
                    valueOr(item.result(), "PENDING"), item.deviationAction(), item.retestResult(), item.remark());
        }
    }

    private ProcessPlan.MajorProcess mapMajor(ResultSet rs) throws SQLException {
        var majorId = rs.getString("id");
        var steps = jdbc.query("select * from experiment_minor_step where major_process_id = ? order by sequence",
                (stepRs, row) -> mapStep(stepRs), majorId);
        var inputs = jdbc.query("select * from experiment_process_input where major_process_id = ? order by sequence",
                (item, row) -> new ProcessPlan.ProcessInput(item.getString("id"), item.getInt("sequence"),
                        item.getString("input_role"), item.getString("material_code"), item.getString("material_name"),
                        item.getBigDecimal("weight_kg"), item.getString("source_step_material_id")), majorId);
        var outputs = jdbc.query("select * from experiment_process_output where major_process_id = ? order by sequence",
                (item, row) -> new ProcessPlan.ProcessOutput(item.getString("id"), item.getInt("sequence"),
                        item.getString("output_type"), item.getBigDecimal("weight_kg"), item.getString("remark")), majorId);
        return new ProcessPlan.MajorProcess(majorId, rs.getInt("sequence"), rs.getString("process_code"),
                rs.getString("process_name"), rs.getString("description"), rs.getString("yield_basis"),
                rs.getString("remark"), steps, inputs, outputs, null);
    }

    private ProcessPlan.MinorStep mapStep(ResultSet rs) throws SQLException {
        var stepId = rs.getString("id");
        var materials = jdbc.query("select * from experiment_step_material where minor_step_id = ? order by sequence",
                (item, row) -> new ProcessPlan.StepMaterial(item.getString("id"), item.getInt("sequence"),
                        item.getString("material_role"), item.getString("material_code"), item.getString("material_name"),
                        item.getString("material_state"), item.getBigDecimal("weight_kg"),
                        item.getString("formula_material_id"), item.getString("remark"), item.getString("source_type"),
                        item.getString("source_step_output_id")), stepId);
        var outputs = jdbc.query("select * from experiment_step_output where minor_step_id = ? order by sequence",
                (item, row) -> new ProcessPlan.StepOutput(item.getString("id"), item.getInt("sequence"),
                        item.getString("output_type"), item.getString("output_name"), item.getString("material_state"),
                        item.getBigDecimal("weight_kg"), item.getBoolean("primary_output"), item.getBoolean("continue_flow"),
                        item.getString("remark")), stepId);
        var controlPoints = jdbc.query("select * from experiment_control_point where minor_step_id = ? order by sequence",
                (item, row) -> mapControlPoint(item), stepId);
        return new ProcessPlan.MinorStep(stepId, rs.getInt("sequence"), rs.getString("step_code"), rs.getString("step_name"),
                rs.getString("step_type"), rs.getString("parameter_1_name"), rs.getString("parameter_1_value"),
                rs.getString("parameter_1_unit"), rs.getString("parameter_2_name"), rs.getString("parameter_2_value"),
                rs.getString("parameter_2_unit"), rs.getString("equipment"), rs.getString("instruction"), materials, outputs, controlPoints);
    }

    private ProcessPlan.ControlPoint mapControlPoint(ResultSet item) throws SQLException {
        var pointId = item.getString("id");
        var measurements = jdbc.query("select * from experiment_control_measurement where control_point_id = ? order by sequence",
                (measurement, measurementRow) -> new ProcessPlan.ControlMeasurement(measurement.getString("id"),
                        measurement.getInt("sequence"), measurement.getBigDecimal("measured_value"),
                        measurement.getTimestamp("measured_at") == null ? null : measurement.getTimestamp("measured_at").toLocalDateTime().toString(),
                        measurement.getString("result"), measurement.getString("deviation_action"),
                        measurement.getString("retest_result"), measurement.getString("remark")), pointId);
        return new ProcessPlan.ControlPoint(pointId, item.getInt("sequence"), item.getString("control_type"),
                item.getString("importance"), item.getString("item_name"), item.getBigDecimal("target_value"),
                item.getBigDecimal("lower_limit"), item.getBigDecimal("upper_limit"), item.getString("unit"),
                item.getString("method"), item.getString("measurement_tool"), item.getString("frequency"), item.getString("deviation_action"),
                item.getBoolean("resolved"), item.getString("confirmed_by"),
                item.getTimestamp("confirmed_at") == null ? null : item.getTimestamp("confirmed_at").toLocalDateTime().toString(),
                item.getString("basis_or_remark"), measurements);
    }

    private boolean isProcessPlanFormUniqueViolation(DataIntegrityViolationException exception) {
        var message = exception.getMessage();
        if (message == null || !message.toLowerCase(java.util.Locale.ROOT).contains("uk_process_plan_form")) return false;
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) return "23505".equals(sqlException.getSQLState());
            cause = cause.getCause();
        }
        return false;
    }

    private ProcessPlan.MajorProcess withYield(ProcessPlan.MajorProcess major) {
        return new ProcessPlan.MajorProcess(major.id(), major.sequence(), major.processCode(), major.processName(),
                major.description(), major.yieldBasis(), major.remark(), major.steps(), major.inputs(), major.outputs(),
                calculations.calculate(major));
    }

    private ProcessPlan legacyPlan(String formId) {
        var majors = new ArrayList<ProcessPlan.MajorProcess>();
        for (var legacy : legacyRepository.findByExperimentFormIdOrderBySequenceAsc(formId)) {
            var inputs = legacy.getBeforeWeightKg() == null ? List.<ProcessPlan.ProcessInput>of() : List.of(
                    new ProcessPlan.ProcessInput(null, 1, "PRIMARY", null, "历史工序投入", legacy.getBeforeWeightKg(), null));
            var outputs = new ArrayList<ProcessPlan.ProcessOutput>();
            if (legacy.getAfterWeightKg() != null) outputs.add(new ProcessPlan.ProcessOutput(null, 1, "QUALIFIED", legacy.getAfterWeightKg(), null));
            if (legacy.getRemainingWeightKg() != null) outputs.add(new ProcessPlan.ProcessOutput(null, 2, "REUSABLE", legacy.getRemainingWeightKg(), legacy.getRemainingDisposition()));
            if (legacy.getLossWeightKg() != null) outputs.add(new ProcessPlan.ProcessOutput(null, 3, "WASTE", legacy.getLossWeightKg(), null));
            var major = new ProcessPlan.MajorProcess(null, legacy.getSequence(), null, legacy.getProcessName(),
                    "由历史工序记录生成", "PRIMARY_INPUT", legacy.getRemark(), List.of(), inputs, outputs, null);
            majors.add(withYield(major));
        }
        var plan = new ProcessPlan(null, formId, 0, "DRAFT", majors, null, true);
        return new ProcessPlan(null, formId, 0, "DRAFT", majors, calculations.calculateBatch(plan), true);
    }

    private void requireForm(String formId) {
        if (!formRepository.existsById(formId)) throw new BusinessException("EXPERIMENT_FORM_NOT_FOUND", "实验单不存在");
    }

    private void requireNonNegative(BigDecimal value) {
        if (value != null && value.signum() < 0) throw new BusinessException("PROCESS_WEIGHT_INVALID", "工艺重量不能为负数");
    }

    private BigDecimal requireBalanceTolerance(BigDecimal value) {
        var tolerance = value == null ? ProcessPlan.DEFAULT_BALANCE_TOLERANCE_KG : value;
        if (tolerance.signum() < 0) throw new BusinessException("PROCESS_BALANCE_TOLERANCE_INVALID", "物料平衡允许差不能为负数");
        return tolerance;
    }

    private BusinessException conflict() {
        return new BusinessException("PROCESS_PLAN_VERSION_CONFLICT", "工艺方案已被更新，请刷新后重试");
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> List<T> values(List<T> items) {
        return items == null ? List.of() : items;
    }

    private Timestamp timestamp(String value) {
        return value == null || value.isBlank() ? null : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private record PlanHeader(String id, int versionNo, String status, BigDecimal balanceToleranceKg,
                              String sourceRevisionId, String changeReason) {
    }

    private record PersistedControlPoint(ProcessPlan.ControlPoint point, String majorId, int majorSequence,
                                         String processCode, String processName, String stepId, int stepSequence,
                                         String stepCode, String stepName) {
    }

    private record ControlLimits(BigDecimal lower, BigDecimal upper) {
    }

    private record DeviationMeasurement(BigDecimal measuredValue, String result, String deviationAction, String retestResult) {
        private boolean isDeviation(ControlLimits limits) {
            return "FAIL".equals(result) || measuredValue != null
                    && (limits.lower != null && measuredValue.compareTo(limits.lower) < 0
                    || limits.upper != null && measuredValue.compareTo(limits.upper) > 0);
        }
    }
}
