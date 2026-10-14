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
        var packagingLines = new ArrayList<>(values(packaging));
        boolean completeExternal = true;
        for (var major : values(plan.majorProcesses()).stream().sorted(Comparator.comparingInt(ProcessPlan.MajorProcess::sequence)).toList()) {
            boolean supported = "PRIMARY_INPUT".equals(major.yieldBasis()) || "NONE".equals(major.yieldBasis());
            if (!supported) issues.add(issue("UNSUPPORTED_YIELD_BASIS", path(major.sequence(), null) + ".yieldBasis", "旧版得率口径待确认：仅支持主料投入或不参与得率，不能将 TOTAL_INPUT 当作主料口径", major.sequence(), null));
            for (var step : values(major.steps())) for (var material : values(step.materials())) {
                if (!"EXTERNAL".equals(material.sourceType())) continue;
                if ("PACKAGING".equals(material.materialRole())) {
                    packagingLines.add(new PricingPackagingItem(material.id(), null, packagingLines.size()+1,
                            PricingPackagingSource.MANUAL, material.materialCode(), material.materialName(),
                            material.quantity(), material.catalogSpecification(), null, material.remark(),
                            material.quantity()!=null && !blank(material.quantityUnit()) ? PricingPackagingStatus.CONFIRMED : PricingPackagingStatus.PENDING_CONFIRMATION,
                            null, material.quantityUnit()));
                    continue;
                }
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
                List.copyOf(ingredients), List.copyOf(majors), finished, List.copyOf(packagingLines), List.copyOf(issues), null);
    }

    public ProcessExportView.Check check(ProcessExportView view, String type) {
        validateType(type);
        var issues = new ArrayList<>(view.issues());
        if (view.majors().isEmpty()) issues.add(issue("MAJOR_PROCESS_REQUIRED", "majorProcesses", "至少需要一个大工序", null, null));
        if ("SOP_DOCX".equals(type) || "SOP_XLSX".equals(type)) {
            for (var major : values(view.snapshot().majorProcesses())) for (var step : values(major.steps())) {
                if (blank(step.instruction())) issues.add(issue("STEP_INSTRUCTION_REQUIRED", path(major.sequence(), step.sequence()) + ".instruction", "SOP 操作要求待填写", major.sequence(), step.sequence()));
                for (var parameter : com.lhr.rnd.domain.StepParameterService.effective(step)) parameterIssues(issues, major, step, parameter);
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
            if (f == null || f.weightKg() == null || f.weightKg().signum() < 0 || f.quantity() == null || f.quantity().signum() < 0 || blank(f.unit()) || blank(f.sourceLabel()))
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
        if (!List.of("FORMULA_XLSX", "SOP_DOCX", "SOP_XLSX", "PRICING_XLSX").contains(type == null ? "" : type)) throw new BusinessException("PROCESS_ARTIFACT_TYPE_INVALID", "不支持的成果类型");
    }
    private static ProcessExportView.Issue issue(String code, String path, String message, Integer major, Integer step) { return new ProcessExportView.Issue(code, path, message, major, step); }
    private void parameterIssues(List<ProcessExportView.Issue> issues, ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step, ProcessPlan.StepParameter parameter) {
        var base = path(major.sequence(), step.sequence()) + ".parameters[" + parameter.key() + "]";
        var legacyBase = path(major.sequence(), step.sequence()) + ".parameter" + parameter.key().replace("legacy-", "");
        if (blank(parameter.name())) issues.add(issue("PARAMETER_NAME_REQUIRED", step.parameters() == null ? legacyBase + "Name" : base + ".name", "已记录参数的含义待填写", major.sequence(), step.sequence()));
        if (blank(parameter.actualValue())) issues.add(issue("PARAMETER_VALUE_REQUIRED", base + ".actualValue", "参数实际记录待填写：" + (blank(parameter.name()) ? "未命名参数" : parameter.name()), major.sequence(), step.sequence()));
        if ("NUMBER".equals(parameter.valueType()) && !blank(parameter.actualValue())) {
            if (!com.lhr.rnd.domain.StepParameterService.numeric(parameter.actualValue())) issues.add(issue("PARAMETER_NUMBER_INVALID", base + ".actualValue", "参数数值无效", major.sequence(), step.sequence()));
            if (blank(parameter.unit())) issues.add(issue("PARAMETER_UNIT_REQUIRED", step.parameters() == null ? legacyBase + "Unit" : base + ".unit", "数值参数单位待填写；无量纲请明确填写无量纲", major.sequence(), step.sequence()));
        }
        var pressure = (parameter.name() == null ? "" : parameter.name()).matches("(?i).*(压力|真空|pressure|vacuum).*")
                || (parameter.unit() == null ? "" : parameter.unit()).matches("(?i)(pa|kpa|mpa|bar|mbar)");
        if (pressure && !List.of("GAUGE", "ABSOLUTE", "DEVICE_PERCENT").contains(parameter.measurementBasis() == null ? "" : parameter.measurementBasis()))
            issues.add(issue("PARAMETER_BASIS_REQUIRED", base + ".measurementBasis", "压力参数需确认表压、绝对压力或设备显示百分比", major.sequence(), step.sequence()));
        if (pressure && ("DEVICE_PERCENT".equals(parameter.measurementBasis()) || "%".equals(parameter.unit())) && blank(parameter.remark()))
            issues.add(issue("PARAMETER_DEVICE_DEFINITION_REQUIRED", base + ".remark", "设备显示百分比需填写设备定义", major.sequence(), step.sequence()));
    }
    private static String path(Integer major, Integer step) { return major == null ? "majorProcesses" : "majorProcesses[sequence=" + major + "]" + (step == null ? "" : ".steps[sequence=" + step + "]"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static <T> List<T> values(List<T> list) { return list == null ? List.of() : list; }
}
