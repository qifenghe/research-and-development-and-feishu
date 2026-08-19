package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.persistence.repository.ExperimentFormRepository;
import com.lhr.rnd.persistence.repository.ExperimentProcessRepository;
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
    private final ProcessPlanCalculationService calculations = new ProcessPlanCalculationService();

    public ProcessPlanService(
            JdbcTemplate jdbc,
            ExperimentFormRepository formRepository,
            ExperimentProcessRepository legacyRepository
    ) {
        this.jdbc = jdbc;
        this.formRepository = formRepository;
        this.legacyRepository = legacyRepository;
    }

    @Transactional(readOnly = true)
    public ProcessPlan find(String formId) {
        requireForm(formId);
        var plans = jdbc.query("select * from experiment_process_plan where experiment_form_id = ?",
                (rs, row) -> new PlanHeader(rs.getString("id"), rs.getInt("version_no"), rs.getString("status")), formId);
        if (plans.isEmpty()) return legacyPlan(formId);
        var header = plans.get(0);
        var majors = jdbc.query("select * from experiment_major_process where process_plan_id = ? order by sequence",
                (rs, row) -> mapMajor(rs), header.id());
        var calculated = majors.stream().map(this::withYield).toList();
        var plan = new ProcessPlan(header.id(), formId, header.versionNo(), header.status(), calculated, null, false);
        return new ProcessPlan(plan.id(), plan.experimentFormId(), plan.versionNo(), plan.status(), plan.majorProcesses(),
                calculations.calculateBatch(plan), false);
    }

    @Transactional
    public ProcessPlan save(String formId, ProcessPlan request) {
        requireForm(formId);
        var current = jdbc.query("select id, version_no from experiment_process_plan where experiment_form_id = ?",
                (rs, row) -> new PlanHeader(rs.getString("id"), rs.getInt("version_no"), "DRAFT"), formId);
        String planId;
        int nextVersion;
        if (current.isEmpty()) {
            if (request.versionNo() > 1) throw conflict();
            planId = id("PLAN");
            nextVersion = 1;
            jdbc.update("insert into experiment_process_plan(id, experiment_form_id, version_no, status, calculation_mode, created_at, updated_at) values (?,?,?,?,?,?,?)",
                    planId, formId, nextVersion, "DRAFT", "PRIMARY_INPUT", LocalDateTime.now(), LocalDateTime.now());
        } else {
            var stored = current.get(0);
            if (request.versionNo() != stored.versionNo()) throw conflict();
            planId = stored.id();
            nextVersion = stored.versionNo() + 1;
            // Remove referencing materials before the cascaded step-output deletion so STEP_OUTPUT foreign keys stay valid.
            jdbc.update("delete from experiment_step_material where minor_step_id in (select step.id from experiment_minor_step step join experiment_major_process major on step.major_process_id = major.id where major.process_plan_id = ?)", planId);
            jdbc.update("delete from experiment_major_process where process_plan_id = ?", planId);
            jdbc.update("update experiment_process_plan set version_no = ?, updated_at = ? where id = ?",
                    nextVersion, LocalDateTime.now(), planId);
        }
        var majors = request.majorProcesses() == null ? List.<ProcessPlan.MajorProcess>of() : request.majorProcesses();
        for (int index = 0; index < majors.size(); index++) saveMajor(planId, index + 1, majors.get(index));
        return find(formId);
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
        if (major.processName() == null || major.processName().isBlank())
            throw new BusinessException("PROCESS_NAME_REQUIRED", "大工序名称不能为空");
        var majorId = id("MP");
        jdbc.update("insert into experiment_major_process(id, process_plan_id, sequence, process_code, process_name, description, yield_basis, remark) values (?,?,?,?,?,?,?,?)",
                majorId, planId, sequence, major.processCode(), major.processName(), major.description(),
                valueOr(major.yieldBasis(), "PRIMARY_INPUT"), major.remark());
        var steps = major.steps() == null ? List.<ProcessPlan.MinorStep>of() : major.steps();
        for (int index = 0; index < steps.size(); index++) saveStep(majorId, index + 1, steps.get(index));
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

    private void saveStep(String majorId, int sequence, ProcessPlan.MinorStep step) {
        if (step.stepName() == null || step.stepName().isBlank())
            throw new BusinessException("STEP_NAME_REQUIRED", "小步骤名称不能为空");
        calculations.validateStep(step);
        var stepId = id("ST");
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
                    id("MAT"), stepId, index + 1, valueOr(item.materialRole(), "AUXILIARY"), item.materialCode(),
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
        jdbc.update("insert into experiment_control_point(id, minor_step_id, sequence, control_type, importance, item_name, target_value, lower_limit, upper_limit, unit, method, frequency, deviation_action, resolved, confirmed_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                pointId, stepId, sequence, valueOr(point.controlType(), "QUALITY"), valueOr(point.importance(), "NORMAL"),
                valueOr(point.itemName(), "未命名控制点"), point.targetValue(), point.lowerLimit(), point.upperLimit(), point.unit(),
                point.method(), point.frequency(), point.deviationAction(), point.resolved(), point.confirmedBy());
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
                (item, row) -> {
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
                            item.getString("method"), item.getString("frequency"), item.getString("deviation_action"),
                            item.getBoolean("resolved"), item.getString("confirmed_by"), measurements);
                }, stepId);
        return new ProcessPlan.MinorStep(stepId, rs.getInt("sequence"), rs.getString("step_code"), rs.getString("step_name"),
                rs.getString("step_type"), rs.getString("parameter_1_name"), rs.getString("parameter_1_value"),
                rs.getString("parameter_1_unit"), rs.getString("parameter_2_name"), rs.getString("parameter_2_value"),
                rs.getString("parameter_2_unit"), rs.getString("equipment"), rs.getString("instruction"), materials, outputs, controlPoints);
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

    private BusinessException conflict() {
        return new BusinessException("PROCESS_PLAN_VERSION_CONFLICT", "工艺方案已被更新，请刷新后重试");
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Timestamp timestamp(String value) {
        return value == null || value.isBlank() ? null : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private record PlanHeader(String id, int versionNo, String status) {
    }
}
