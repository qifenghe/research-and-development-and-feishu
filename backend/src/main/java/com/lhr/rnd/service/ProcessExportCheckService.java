package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.domain.ProcessPlanCalculationService;
import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.model.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProcessExportCheckService {
    private final ProcessPlanCalculationService calculations = new ProcessPlanCalculationService();

    public ProcessExportView view(String productName, String sourceLabel, ProcessPlan plan,
                                 ProcessExportView.FinishedQuantity finished, List<PricingPackagingItem> packaging) {
        var issues = new ArrayList<ProcessExportView.Issue>();
        var flow = new ProcessSubmissionValidator().previewFlowIssues(plan);
        flow.forEach(i -> issues.add(issue(i.code(), path(i.majorSequence(), i.stepSequence()), i.message(), i.majorSequence(), i.stepSequence())));
        var ingredients = new ArrayList<ProcessExportView.Ingredient>();
        var majors = new ArrayList<ProcessExportView.Major>();
        var external = BigDecimal.ZERO;
        boolean completeExternal = true;
        for (var major : values(plan.majorProcesses()).stream().sorted(Comparator.comparingInt(ProcessPlan.MajorProcess::sequence)).toList()) {
            boolean supported = "PRIMARY_INPUT".equals(major.yieldBasis()) || "NONE".equals(major.yieldBasis());
            if (!supported) issues.add(issue("UNSUPPORTED_YIELD_BASIS", path(major.sequence(), null) + ".yieldBasis", "旧版得率口径待确认：仅支持主料投入或不参与得率，不能将 TOTAL_INPUT 当作主料口径", major.sequence(), null));
            for (var step : values(major.steps())) for (var material : values(step.materials())) {
                if (!"EXTERNAL".equals(material.sourceType())) continue;
                ingredients.add(new ProcessExportView.Ingredient(blank(material.materialCode()) ? material.formulaMaterialId() : material.materialCode(), material.materialName(), material.materialRole(), material.weightKg(), major.sequence(), step.sequence()));
                if (material.weightKg() == null || material.weightKg().signum() < 0) {
                    completeExternal = false;
                    issues.add(issue("EXTERNAL_WEIGHT_REQUIRED", path(major.sequence(), step.sequence()) + ".materials", "外部投料实际重量待填写，不能用0代替缺失值", major.sequence(), step.sequence()));
                } else external = external.add(material.weightKg());
                if (blank(material.materialCode()) && blank(material.formulaMaterialId()) || blank(material.materialName()) || !List.of("PRIMARY", "AUXILIARY", "PROCESS_WATER").contains(material.materialRole() == null ? "" : material.materialRole())) {
                    issues.add(issue("EXTERNAL_IDENTITY_REQUIRED", path(major.sequence(), step.sequence()) + ".materials", "外部投料需明确物料编码、名称及角色", major.sequence(), step.sequence()));
                }
            }
            boolean validFlow = flow.stream().noneMatch(i -> i.majorSequence() == null || i.majorSequence() == major.sequence());
            BigDecimal input = null, output = null, rate = null;
            if (validFlow) {
                var result = calculations.calculate(major);
                boolean layered = values(major.steps()).stream().anyMatch(step ->
                        values(step.materials()).stream().anyMatch(m -> "PRIMARY".equals(m.materialRole()))
                                || values(step.outputs()).stream().anyMatch(ProcessPlan.StepOutput::primaryOutput));
                var legacyInputs = values(major.inputs()).stream().filter(i -> "PRIMARY".equals(i.inputRole())).toList();
                var legacyOutputs = values(major.outputs()).stream().filter(o -> "QUALIFIED".equals(o.outputType())).toList();
                // The legacy calculator uses zero for absent totals; export actuals must retain absence.
                if (layered || !legacyInputs.isEmpty() && legacyInputs.stream().allMatch(i -> i.weightKg() != null))
                    input = result.primaryInputWeightKg();
                if (layered || !legacyOutputs.isEmpty() && legacyOutputs.stream().allMatch(o -> o.weightKg() != null))
                    output = result.qualifiedOutputWeightKg();
                if (supported && !"NONE".equals(major.yieldBasis())) rate = result.mainYieldPercent();
            }
            majors.add(new ProcessExportView.Major(major.sequence(), major.processName(), major.yieldBasis(), input, output, rate));
        }
        if (ingredients.isEmpty() || completeExternal && external.signum() <= 0)
            issues.add(issue("EXTERNAL_MATERIAL_WEIGHT_REQUIRED", "materials", "外部物料总重量必须大于0", null, null));
        var batch = calculations.calculatePreviewBatch(plan);
        return new ProcessExportView(productName, sourceLabel, plan, completeExternal && !ingredients.isEmpty() ? external : null, batch,
                List.copyOf(ingredients), List.copyOf(majors), finished, List.copyOf(values(packaging)), List.copyOf(issues), null);
    }

    public ProcessExportView.Check check(ProcessExportView view, String type) {
        validateType(type);
        var issues = new ArrayList<>(view.issues());
        if (view.majors().isEmpty()) issues.add(issue("MAJOR_PROCESS_REQUIRED", "majorProcesses", "至少需要一个大工序", null, null));
        if ("SOP_DOCX".equals(type)) {
            for (var major : values(view.snapshot().majorProcesses())) for (var step : values(major.steps())) {
                if (blank(step.instruction())) issues.add(issue("STEP_INSTRUCTION_REQUIRED", path(major.sequence(), step.sequence()) + ".instruction", "SOP 操作要求待填写", major.sequence(), step.sequence()));
                parameterIssues(issues, major, step, 1, step.parameter1Name(), step.parameter1Value(), step.parameter1Unit());
                parameterIssues(issues, major, step, 2, step.parameter2Name(), step.parameter2Value(), step.parameter2Unit());
                for (var control : values(step.controlPoints())) {
                    var base = path(major.sequence(), step.sequence()) + ".controlPoints[sequence=" + control.sequence() + "]";
                    if (blank(control.itemName())) issues.add(issue("CONTROL_NAME_REQUIRED", base + ".itemName", "控制项目名称待填写", major.sequence(), step.sequence()));
                    if (control.targetValue() == null && control.lowerLimit() == null && control.upperLimit() == null && blank(control.basisOrRemark()))
                        issues.add(issue("CONTROL_REQUIREMENT_REQUIRED", base + ".basisOrRemark", "控制要求需填写目标、范围或明确的定性要求/依据", major.sequence(), step.sequence()));
                    if ((control.targetValue() != null || control.lowerLimit() != null || control.upperLimit() != null) && blank(control.unit()))
                        issues.add(issue("CONTROL_UNIT_REQUIRED", base + ".unit", "数值控制单位待填写；无量纲请明确填写无量纲", major.sequence(), step.sequence()));
                    for (var field : new String[][]{{"method", control.method()}, {"frequency", control.frequency()}, {"deviationAction", control.deviationAction()}})
                        if (blank(field[1])) issues.add(issue("CONTROL_STANDARD_REQUIRED", base + "." + field[0], "控制需填写检测方法、频次和偏差处理", major.sequence(), step.sequence()));
                }
            }
        }
        if ("PRICING_XLSX".equals(type)) {
            var f = view.finishedQuantity();
            if (f == null || f.weightKg() == null || f.weightKg().signum() < 0 || f.quantity() == null || f.quantity() < 0 || blank(f.unit()) || blank(f.sourceLabel()))
                issues.add(issue("FINISHED_QUANTITY_REQUIRED", "finishedQuantity", "独立实测成品净重、数量及单位待确认；不得由主料得率推算，试验方案未关联独立包装产量", null, null));
            if (view.packaging().isEmpty()) issues.add(issue("PACKAGING_REQUIRED", "packaging", "包装物料待确认", null, null));
            for (int i = 0; i < view.packaging().size(); i++) {
                var item = view.packaging().get(i); var path = "packaging[" + i + "]";
                if (blank(item.quantityUnit())) issues.add(issue("PACKAGING_UNIT_REQUIRED", path + ".quantityUnit", item.materialName() + "：数量单位待填写，不能从名称推断", null, null));
                if (item.quantity() == null || item.quantity().signum() < 0 || blank(item.materialName()) || item.confirmationStatus() != PricingPackagingStatus.CONFIRMED)
                    issues.add(issue("PACKAGING_CONFIRMATION_REQUIRED", path, "包装物料名称、实际数量需确认", null, null));
            }
        }
        return new ProcessExportView.Check(issues.isEmpty(), List.copyOf(issues));
    }

    public void requireReady(ProcessExportView view, String type) {
        var result = check(view, type);
        if (!result.ready()) throw new BusinessException("PROCESS_EXPORT_NOT_READY", result.issues().get(0).message());
    }
    public void validateType(String type) {
        if (!List.of("FORMULA_XLSX", "SOP_DOCX", "PRICING_XLSX").contains(type == null ? "" : type)) throw new BusinessException("PROCESS_ARTIFACT_TYPE_INVALID", "不支持的成果类型");
    }
    private static ProcessExportView.Issue issue(String code, String path, String message, Integer major, Integer step) { return new ProcessExportView.Issue(code, path, message, major, step); }
    private void parameterIssues(List<ProcessExportView.Issue> issues, ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step, int index, String name, String value, String unit) {
        if (blank(value)) return;
        var base = path(major.sequence(), step.sequence()) + ".parameter" + index;
        if (blank(name)) issues.add(issue("PARAMETER_NAME_REQUIRED", base + "Name", "已记录参数的含义待填写", major.sequence(), step.sequence()));
        if (value.trim().matches("[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)") && blank(unit))
            issues.add(issue("PARAMETER_UNIT_REQUIRED", base + "Unit", "数值参数单位待填写；无量纲请明确填写无量纲", major.sequence(), step.sequence()));
    }
    private static String path(Integer major, Integer step) { return major == null ? "majorProcesses" : "majorProcesses[sequence=" + major + "]" + (step == null ? "" : ".steps[sequence=" + step + "]"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static <T> List<T> values(List<T> list) { return list == null ? List.of() : list; }
}
