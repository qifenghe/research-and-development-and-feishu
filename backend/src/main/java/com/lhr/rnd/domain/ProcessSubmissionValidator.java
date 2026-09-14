package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessSubmissionCheck;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProcessSubmissionValidator {
    private final ProcessPlanCalculationService calculations = new ProcessPlanCalculationService();

    public ProcessSubmissionCheck validate(ProcessPlan plan) {
        var errors = new ArrayList<ProcessSubmissionCheck.Issue>();
        var warnings = new ArrayList<ProcessSubmissionCheck.Issue>();
        var majors = values(plan == null ? null : plan.majorProcesses());
        if (majors.isEmpty()) {
            errors.add(error("MAJOR_PROCESS_REQUIRED", "至少需要一个大工序", null, null));
            return new ProcessSubmissionCheck(false, errors, warnings);
        }

        var balanceTolerance = plan.balanceToleranceKg();
        var validBalanceTolerance = balanceTolerance.signum() >= 0;
        if (!validBalanceTolerance) {
            errors.add(error("BALANCE_TOLERANCE_INVALID", "物料平衡允许差不能为负数", null, null));
        }

        var steps = flatten(majors);
        errors.addAll(flowIssues(plan));
        var hasExternalPrimary = false;
        for (var major : majors) {
            validateWeights(major, errors);
            validateMajorYield(major, errors);
            validateControls(major, errors);
            if (hasExternalPrimary(major)) hasExternalPrimary = true;
            if (validBalanceTolerance) validateMaterialBalance(major, balanceTolerance, errors, warnings);
        }
        if (!hasExternalPrimary) errors.add(error("EXTERNAL_PRIMARY_REQUIRED", "配方至少需要一项外部主料", null, null));
        var externalWeight = steps.stream().flatMap(step -> values(step.step.materials()).stream())
                .filter(material -> "EXTERNAL".equals(material.sourceType()))
                .map(material -> material.weightKg() == null ? BigDecimal.ZERO : material.weightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (externalWeight.signum() <= 0) {
            errors.add(error("EXTERNAL_MATERIAL_WEIGHT_REQUIRED", "外部物料总重量必须大于0", null, null));
        }
        return new ProcessSubmissionCheck(errors.isEmpty(), errors, warnings);
    }

    public List<ProcessSubmissionCheck.Issue> flowIssues(ProcessPlan plan) {
        var errors = new ArrayList<ProcessSubmissionCheck.Issue>();
        var majors = values(plan == null ? null : plan.majorProcesses());
        var steps = flatten(majors);
        validateFlow(steps, errors);
        validatePrimaryChains(majors, steps, errors);
        return errors;
    }

    private void validateFlow(List<StepRef> steps, List<ProcessSubmissionCheck.Issue> errors) {
        var outputs = new HashMap<String, OutputRef>();
        var duplicateOutputIds = new HashSet<String>();
        for (var step : steps) {
            for (var output : values(step.step.outputs())) {
                if (blank(output.id())) continue;
                var previous = outputs.putIfAbsent(output.id(), new OutputRef(step, output));
                if (previous != null) duplicateOutputIds.add(output.id());
            }
        }
        var graph = new HashMap<String, List<String>>();
        for (var step : steps) {
            for (var material : values(step.step.materials())) {
                if ("STEP_OUTPUT".equals(material.sourceType())) {
                    var source = material.sourceStepOutputId();
                    var output = blank(source) || duplicateOutputIds.contains(source) ? null : outputs.get(source);
                    if (output == null || !precedes(output.step, step)
                            || !output.output.continueFlow()
                            || ("PRIMARY".equals(material.materialRole()) && !output.output.primaryOutput())) {
                        errors.add(flowBroken(step));
                        continue;
                    }
                    if ("PRIMARY".equals(material.materialRole()) && (material.weightKg() == null
                            || output.output.weightKg() == null || material.weightKg().compareTo(output.output.weightKg()) != 0)) {
                        errors.add(error("FLOW_WEIGHT_MISMATCH", "主料承接重量必须与前序产出一致；取样和损耗请在前序步骤单独记录", step.major.sequence(), step.step.sequence()));
                    }
                    for (var target : values(step.step.outputs())) {
                        if (!blank(target.id())) graph.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target.id());
                    }
                } else if ("EXTERNAL".equals(material.sourceType()) && !blank(material.sourceStepOutputId())) {
                    errors.add(flowBroken(step));
                }
            }
        }
        if (hasCycle(graph)) {
            errors.add(error("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链或循环", null, null));
        }
    }

    private void validatePrimaryChains(List<ProcessPlan.MajorProcess> majors, List<StepRef> steps,
                                       List<ProcessSubmissionCheck.Issue> errors) {
        var outputs = new HashMap<String, OutputRef>();
        for (var step : steps) for (var output : values(step.step.outputs())) {
            if (!blank(output.id())) outputs.putIfAbsent(output.id(), new OutputRef(step, output));
        }
        var primaryConsumerCounts = new HashMap<String, Integer>();
        for (var step : steps) for (var material : values(step.step.materials())) {
            if (!"STEP_OUTPUT".equals(material.sourceType())) continue;
            var source = outputs.get(material.sourceStepOutputId());
            if (source != null && source.output.primaryOutput()) {
                if (!"PRIMARY".equals(material.materialRole())) errors.add(flowBroken(step));
                else primaryConsumerCounts.merge(material.sourceStepOutputId(), 1, Integer::sum);
            }
        }
        primaryConsumerCounts.forEach((id, count) -> {
            if (count > 1) {
                var producer = outputs.get(id);
                errors.add(producer == null ? error("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链或循环", null, null)
                        : flowBroken(producer.step));
            }
        });

        String currentTip = null;
        boolean started = false;
        for (var major : majors.stream().sorted(java.util.Comparator.comparingInt(ProcessPlan.MajorProcess::sequence)).toList()) {
            var ordered = values(major.steps()).stream().sorted(java.util.Comparator.comparingInt(ProcessPlan.MinorStep::sequence)).toList();
            if (!"NONE".equals(major.yieldBasis()) && ordered.stream().noneMatch(step ->
                    values(step.materials()).stream().anyMatch(item -> "PRIMARY".equals(item.materialRole())))) {
                errors.add(error("MAJOR_PRIMARY_CHAIN_REQUIRED", "参与得率的大工序必须在小步骤中记录连续主料流转，旧汇总数据请先补齐步骤", major.sequence(), null));
            }
            for (var step : ordered) {
                var primaryInputs = values(step.materials()).stream().filter(item -> "PRIMARY".equals(item.materialRole())).toList();
                var primaryOutputs = values(step.outputs()).stream().filter(ProcessPlan.StepOutput::primaryOutput).toList();
                if (primaryInputs.size() > 1 || primaryOutputs.size() > 1) {
                    errors.add(error("PRIMARY_FLOW_BROKEN", "每个小步骤只能有一个主料投入和一个主料产出", major.sequence(), step.sequence()));
                    continue;
                }
                var input = primaryInputs.isEmpty() ? null : primaryInputs.get(0);
                var output = primaryOutputs.isEmpty() ? null : primaryOutputs.get(0);
                if (input == null && output == null) continue;
                if (input == null || output == null) {
                    errors.add(error("PRIMARY_FLOW_BROKEN", "主料链步骤必须同时记录主料投入和主料产出", major.sequence(), step.sequence()));
                    continue;
                }
                if (!positive(input.weightKg()) || !positive(output.weightKg())) {
                    errors.add(error("PRIMARY_STEP_WEIGHT_REQUIRED", "每个主料步骤的投入和产出重量必须大于0", major.sequence(), step.sequence()));
                }
                if (!started) {
                    if ("STEP_OUTPUT".equals(input.sourceType())) {
                        var source = outputs.get(input.sourceStepOutputId());
                        if (source == null || source.step.major.sequence() >= major.sequence()) {
                            errors.add(error("PRIMARY_FLOW_BROKEN", "大工序主料起点必须来自外部主料或上一大工序产出", major.sequence(), step.sequence()));
                        }
                    } else if (!"EXTERNAL".equals(input.sourceType())) {
                        errors.add(error("PRIMARY_FLOW_BROKEN", "大工序主料起点来源无效", major.sequence(), step.sequence()));
                    }
                    started = true;
                } else if (!"STEP_OUTPUT".equals(input.sourceType()) || !java.util.Objects.equals(currentTip, input.sourceStepOutputId())) {
                    errors.add(error("BATCH_PRIMARY_CHAIN_REQUIRED", "成品得率需要同一条连续主料链，请承接上一主料产出；独立路线请分开打样", major.sequence(), step.sequence()));
                }
                if ("NONE".equals(major.yieldBasis()) && input.weightKg() != null && output.weightKg() != null
                        && input.weightKg().compareTo(output.weightKg()) != 0) {
                    errors.add(error("EXCLUDED_PRIMARY_WEIGHT_CHANGED", "主料重量发生变化的工序必须参与得率计算", major.sequence(), step.sequence()));
                }
                currentTip = output.id();
            }
        }
    }

    private boolean precedes(StepRef source, StepRef consumer) {
        if (source.major.sequence() != consumer.major.sequence()) return source.major.sequence() < consumer.major.sequence();
        return source.step.sequence() < consumer.step.sequence();
    }

    private boolean hasCycle(Map<String, List<String>> graph) {
        var visiting = new HashSet<String>();
        var visited = new HashSet<String>();
        for (var node : graph.keySet()) {
            if (hasCycle(node, graph, visiting, visited)) return true;
        }
        return false;
    }

    private boolean hasCycle(String node, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) return false;
        if (!visiting.add(node)) return true;
        for (var next : graph.getOrDefault(node, List.of())) {
            if (hasCycle(next, graph, visiting, visited)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    private void validateMajorYield(ProcessPlan.MajorProcess major, List<ProcessSubmissionCheck.Issue> errors) {
        if ("NONE".equals(major.yieldBasis())) {
            if (values(major.steps()).isEmpty()) {
                errors.add(error("MAJOR_STEP_REQUIRED", "不计算得率的大工序也必须记录操作步骤", major.sequence(), null));
            }
            if (blank(major.remark())) {
                errors.add(error("MAJOR_YIELD_EXCLUSION_REASON_REQUIRED", "不计算得率必须填写业务原因", major.sequence(), null));
            }
            return;
        }
        var steps = values(major.steps());
        if (steps.isEmpty()) {
            var hasInput = hasLegacyPrimaryInput(major);
            var hasOutput = hasLegacyQualifiedOutput(major);
            if (!hasInput || !hasLegacyPositivePrimaryInput(major)) {
                errors.add(error(hasInput ? "MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED" : "MAJOR_PRIMARY_INPUT_REQUIRED",
                        hasInput ? "大工序首端主料投入重量必须大于0" : "需计算得率的大工序缺少首端主料投入", major.sequence(), null));
            }
            if (!hasOutput || !hasLegacyPositiveQualifiedOutput(major)) {
                errors.add(error(hasOutput ? "MAJOR_PRIMARY_OUTPUT_WEIGHT_REQUIRED" : "MAJOR_PRIMARY_OUTPUT_REQUIRED",
                        hasOutput ? "大工序末端主料产出重量必须大于0" : "需计算得率的大工序缺少末端主料产出", major.sequence(), null));
            }
            return;
        }
        var firstInput = steps.stream().flatMap(step -> values(step.materials()).stream()
                        .filter(material -> "PRIMARY".equals(material.materialRole()) && positive(material.weightKg()))
                        .map(material -> new PrimaryInput(step, material)))
                .min(java.util.Comparator.comparingInt((PrimaryInput value) -> value.step().sequence())
                        .thenComparingInt(value -> value.material().sequence())).orElse(null);
        var lastOutput = steps.stream().flatMap(step -> values(step.outputs()).stream()
                        .filter(output -> output.primaryOutput() && positive(output.weightKg()))
                        .map(output -> new PrimaryOutput(step, output)))
                .max(java.util.Comparator.comparingInt((PrimaryOutput value) -> value.step().sequence())
                        .thenComparingInt(value -> value.output().sequence())).orElse(null);
        var hasAnyPrimaryInput = steps.stream().flatMap(step -> values(step.materials()).stream())
                .anyMatch(material -> "PRIMARY".equals(material.materialRole()) && material.weightKg() != null);
        var hasAnyPrimaryOutput = steps.stream().flatMap(step -> values(step.outputs()).stream())
                .anyMatch(output -> output.primaryOutput() && output.weightKg() != null);
        var hasPositivePrimaryOutput = steps.stream().flatMap(step -> values(step.outputs()).stream())
                .anyMatch(output -> output.primaryOutput() && positive(output.weightKg()));
        if (firstInput == null) errors.add(error(hasAnyPrimaryInput ? "MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED" : "MAJOR_PRIMARY_INPUT_REQUIRED",
                hasAnyPrimaryInput ? "大工序首端主料投入重量必须大于0" : "需计算得率的大工序缺少首端主料投入", major.sequence(), null));
        if (lastOutput == null || firstInput != null && lastOutput.step().sequence() < firstInput.step().sequence()) {
            errors.add(error(hasAnyPrimaryOutput && !hasPositivePrimaryOutput ? "MAJOR_PRIMARY_OUTPUT_WEIGHT_REQUIRED" : "MAJOR_PRIMARY_OUTPUT_REQUIRED",
                    hasAnyPrimaryOutput && !hasPositivePrimaryOutput ? "大工序末端主料产出重量必须大于0" : "需计算得率的大工序缺少末端主料产出", major.sequence(), null));
        }
        if (firstInput != null && lastOutput != null && lastOutput.step().sequence() < firstInput.step().sequence()) {
            errors.add(error("MAJOR_PRIMARY_FLOW_INVALID", "主料首端投入必须早于末端主产出", major.sequence(), null));
        }
    }

    private void validateControls(ProcessPlan.MajorProcess major, List<ProcessSubmissionCheck.Issue> errors) {
        for (var step : values(major.steps())) {
            for (var point : values(step.controlPoints())) {
                if (!"CRITICAL".equals(point.importance())) continue;
                var measurements = values(point.measurements());
                var hasMeasurement = measurements.stream().anyMatch(item -> item.measuredValue() != null);
                var outOfLimit = measurements.stream().anyMatch(item -> "FAIL".equals(item.result()) || outside(point, item));
                var hasUnresolvedDeviation = measurements.stream().anyMatch(item -> ("FAIL".equals(item.result()) || outside(point, item))
                        && (blank(item.deviationAction()) || blank(item.retestResult()) || "PENDING".equals(item.retestResult())
                        || "FAIL".equals(item.retestResult())));
                if (!hasMeasurement || blank(point.confirmedBy())
                        || outOfLimit && (!point.resolved() || hasUnresolvedDeviation)) {
                    errors.add(error("CRITICAL_CONTROL_UNRESOLVED", "极重要关键控制点未完成", major.sequence(), step.sequence()));
                }
            }
        }
    }

    private boolean outside(ProcessPlan.ControlPoint point, ProcessPlan.ControlMeasurement measurement) {
        if (measurement.measuredValue() == null) return false;
        return point.lowerLimit() != null && measurement.measuredValue().compareTo(point.lowerLimit()) < 0
                || point.upperLimit() != null && measurement.measuredValue().compareTo(point.upperLimit()) > 0;
    }

    private void validateMaterialBalance(ProcessPlan.MajorProcess major, BigDecimal balanceTolerance,
                                         List<ProcessSubmissionCheck.Issue> errors,
                                         List<ProcessSubmissionCheck.Issue> warnings) {
        try {
            var difference = calculations.calculate(major).balanceDifferenceKg();
            if (difference != null && difference.abs().compareTo(balanceTolerance) > 0) {
                warnings.add(warning("MATERIAL_BALANCE_EXCEEDED", "物料平衡差超过允许范围", major.sequence(), null));
                if (blank(major.remark())) {
                    errors.add(error("MATERIAL_BALANCE_UNEXPLAINED", "物料平衡差超限，请填写差异说明", major.sequence(), null));
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A separate formal-check error below identifies malformed primary inputs or weights.
        }
    }

    private void validateWeights(ProcessPlan.MajorProcess major, List<ProcessSubmissionCheck.Issue> errors) {
        for (var step : values(major.steps())) {
            if (values(step.materials()).stream().anyMatch(item -> negative(item.weightKg()))
                    || values(step.outputs()).stream().anyMatch(item -> negative(item.weightKg()))) {
                errors.add(error("PROCESS_WEIGHT_INVALID", "工艺重量不能为负数", major.sequence(), step.sequence()));
            }
        }
        if (values(major.inputs()).stream().anyMatch(item -> negative(item.weightKg()))
                || values(major.outputs()).stream().anyMatch(item -> negative(item.weightKg()))) {
            errors.add(error("PROCESS_WEIGHT_INVALID", "工艺重量不能为负数", major.sequence(), null));
        }
    }

    private boolean hasExternalPrimary(ProcessPlan.MajorProcess major) {
        return values(major.steps()).stream().flatMap(step -> values(step.materials()).stream())
                .anyMatch(material -> "PRIMARY".equals(material.materialRole()) && "EXTERNAL".equals(material.sourceType()));
    }

    private boolean hasLegacyPrimaryInput(ProcessPlan.MajorProcess major) {
        return values(major.inputs()).stream()
                .anyMatch(input -> "PRIMARY".equals(input.inputRole()) && input.weightKg() != null);
    }

    private boolean hasLegacyQualifiedOutput(ProcessPlan.MajorProcess major) {
        return values(major.outputs()).stream()
                .anyMatch(output -> "QUALIFIED".equals(output.outputType()) && output.weightKg() != null);
    }

    private boolean hasLegacyPositivePrimaryInput(ProcessPlan.MajorProcess major) {
        return values(major.inputs()).stream()
                .anyMatch(input -> "PRIMARY".equals(input.inputRole()) && positive(input.weightKg()));
    }

    private boolean hasLegacyPositiveQualifiedOutput(ProcessPlan.MajorProcess major) {
        return values(major.outputs()).stream()
                .anyMatch(output -> "QUALIFIED".equals(output.outputType()) && positive(output.weightKg()));
    }

    private boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private List<StepRef> flatten(List<ProcessPlan.MajorProcess> majors) {
        var result = new ArrayList<StepRef>();
        for (var major : majors) for (var step : values(major.steps())) result.add(new StepRef(major, step));
        return result;
    }

    private ProcessSubmissionCheck.Issue error(String code, String message, Integer majorSequence, Integer stepSequence) {
        return new ProcessSubmissionCheck.Issue(code, message, majorSequence, stepSequence);
    }

    private ProcessSubmissionCheck.Issue warning(String code, String message, Integer majorSequence, Integer stepSequence) {
        return new ProcessSubmissionCheck.Issue(code, message, majorSequence, stepSequence);
    }

    private ProcessSubmissionCheck.Issue flowBroken(StepRef step) {
        return error("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链或循环", step.major.sequence(), step.step.sequence());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record StepRef(ProcessPlan.MajorProcess major, ProcessPlan.MinorStep step) {
    }

    private record OutputRef(StepRef step, ProcessPlan.StepOutput output) {
    }

    private record PrimaryInput(ProcessPlan.MinorStep step, ProcessPlan.StepMaterial material) {
    }

    private record PrimaryOutput(ProcessPlan.MinorStep step, ProcessPlan.StepOutput output) {
    }
}
