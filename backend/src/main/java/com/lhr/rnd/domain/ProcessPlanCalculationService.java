package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ProcessPlanCalculationService {
    private static final int WEIGHT_SCALE = 4;
    private static final int RATE_SCALE = 6;

    public ProcessPlan.ProcessYield calculate(ProcessPlan.MajorProcess process) {
        if (process != null) values(process.steps()).forEach(this::validateStep);
        if (hasStepFlow(process)) return calculateFromStepFlow(process);
        return calculateFromLegacyProcessTotals(process);
    }

    public ProcessPlan.ProcessYield calculateStep(ProcessPlan.MinorStep step) {
        validateStep(step);
        var materials = values(step == null ? null : step.materials());
        var outputs = values(step == null ? null : step.outputs());
        var primaryInput = materials.stream()
                .filter(item -> "PRIMARY".equals(item.materialRole()) && item.weightKg() != null)
                .findFirst().map(ProcessPlan.StepMaterial::weightKg).map(this::safe).orElse(BigDecimal.ZERO);
        var totalInput = materials.stream().map(ProcessPlan.StepMaterial::weightKg).map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var primaryOutput = outputs.stream()
                .filter(item -> item.primaryOutput() && item.weightKg() != null)
                .reduce((first, last) -> last).map(ProcessPlan.StepOutput::weightKg).orElse(null);
        var reusable = sumStepOutputs(outputs, "REUSABLE").add(sumStepOutputs(outputs, "TAILING"));
        var totalOutput = outputs.stream().map(ProcessPlan.StepOutput::weightKg).map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return result(primaryInput, totalInput, safe(primaryOutput), reusable, totalOutput, primaryOutput != null);
    }

    private ProcessPlan.ProcessYield calculateFromStepFlow(ProcessPlan.MajorProcess process) {
        var steps = values(process.steps());
        var materials = steps.stream().flatMap(step -> values(step.materials()).stream()).toList();
        var primaryInput = materials.stream()
                .filter(item -> "PRIMARY".equals(item.materialRole()) && item.weightKg() != null)
                .findFirst().map(ProcessPlan.StepMaterial::weightKg).map(this::safe).orElse(BigDecimal.ZERO);
        var externalInput = materials.stream()
                .filter(item -> !"STEP_OUTPUT".equals(item.sourceType()))
                .map(ProcessPlan.StepMaterial::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        var lastStepOutputs = steps.stream().map(ProcessPlan.MinorStep::outputs).filter(outputs -> outputs != null && !outputs.isEmpty())
                .reduce((first, last) -> last).orElse(List.of());
        var primaryOutput = steps.stream().flatMap(step -> values(step.outputs()).stream())
                .filter(item -> item.primaryOutput() && item.weightKg() != null)
                .reduce((first, last) -> last).map(ProcessPlan.StepOutput::weightKg).orElse(null);
        var reusable = sumStepOutputs(lastStepOutputs, "REUSABLE").add(sumStepOutputs(lastStepOutputs, "TAILING"));
        var totalOutput = lastStepOutputs.stream().map(ProcessPlan.StepOutput::weightKg).map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return result(primaryInput, externalInput, safe(primaryOutput), reusable, totalOutput, primaryOutput != null);
    }

    private ProcessPlan.ProcessYield calculateFromLegacyProcessTotals(ProcessPlan.MajorProcess process) {
        var primary = sumInputs(process, "PRIMARY");
        var totalInput = process.inputs() == null ? BigDecimal.ZERO : process.inputs().stream()
                .map(ProcessPlan.ProcessInput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        var qualified = sumOutputs(process, "QUALIFIED");
        var reusable = sumOutputs(process, "REUSABLE").add(sumOutputs(process, "TAILING"));
        var totalOutput = process.outputs() == null ? BigDecimal.ZERO : process.outputs().stream()
                .map(ProcessPlan.ProcessOutput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        var hasQualified = values(process.outputs()).stream()
                .anyMatch(item -> "QUALIFIED".equals(item.outputType()) && item.weightKg() != null);
        return result(primary, totalInput, qualified, reusable, totalOutput, hasQualified);
    }

    public BigDecimal calculateBatch(ProcessPlan plan) {
        if (plan == null || plan.majorProcesses() == null || plan.majorProcesses().isEmpty()) return null;
        var ratio = BigDecimal.ONE;
        var hasRate = false;
        for (var major : plan.majorProcesses()) {
            if ("NONE".equals(major.yieldBasis())) continue;
            var value = calculate(major).mainYieldPercent();
            if (value != null) {
                ratio = ratio.multiply(value.divide(BigDecimal.valueOf(100)));
                hasRate = true;
            }
        }
        return hasRate ? ratio.multiply(BigDecimal.valueOf(100)).setScale(RATE_SCALE, RoundingMode.HALF_UP) : null;
    }

    private ProcessPlan.ProcessYield result(BigDecimal primary, BigDecimal totalInput, BigDecimal qualified,
                                            BigDecimal reusable, BigDecimal totalOutput, boolean hasPrimaryOutput) {
        return new ProcessPlan.ProcessYield(
                weight(primary), weight(totalInput), weight(qualified), weight(reusable), weight(totalOutput),
                hasPrimaryOutput ? percent(qualified, primary) : null, percent(qualified.add(reusable), totalInput),
                weight(totalInput.subtract(totalOutput)));
    }

    private BigDecimal sumInputs(ProcessPlan.MajorProcess process, String role) {
        return process.inputs() == null ? BigDecimal.ZERO : process.inputs().stream()
                .filter(item -> role.equals(item.inputRole()))
                .map(ProcessPlan.ProcessInput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumOutputs(ProcessPlan.MajorProcess process, String type) {
        return process.outputs() == null ? BigDecimal.ZERO : process.outputs().stream()
                .filter(item -> type.equals(item.outputType()))
                .map(ProcessPlan.ProcessOutput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumStepOutputs(List<ProcessPlan.StepOutput> outputs, String type) {
        return values(outputs).stream().filter(item -> type.equals(item.outputType()))
                .map(ProcessPlan.StepOutput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean hasStepFlow(ProcessPlan.MajorProcess process) {
        return process != null && process.steps() != null && process.steps().stream()
                .flatMap(step -> values(step.materials()).stream())
                .anyMatch(material -> "PRIMARY".equals(material.materialRole()) && material.weightKg() != null)
                && process.steps().stream().flatMap(step -> values(step.outputs()).stream())
                .anyMatch(output -> output.primaryOutput() && output.weightKg() != null);
    }

    public void validateStep(ProcessPlan.MinorStep step) {
        if (step == null) return;
        var primaryMaterials = values(step.materials()).stream()
                .filter(material -> "PRIMARY".equals(material.materialRole())).count();
        if (primaryMaterials > 1) throw new IllegalArgumentException("a step may have at most one primary material input");
        var primaryOutputs = values(step.outputs()).stream().filter(ProcessPlan.StepOutput::primaryOutput).count();
        if (primaryOutputs > 1) throw new IllegalArgumentException("a step may have at most one primary output");
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }

    private BigDecimal safe(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0) throw new IllegalArgumentException("process weight must not be negative");
        return value;
    }

    private BigDecimal weight(BigDecimal value) {
        return value.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        return denominator == null || denominator.signum() == 0 ? null
                : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
