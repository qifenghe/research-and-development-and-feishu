package com.lhr.rnd.domain;

import com.lhr.rnd.model.ProcessPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProcessPlanCalculationService {
    private static final int WEIGHT_SCALE = 4;
    private static final int RATE_SCALE = 6;

    public ProcessPlan.ProcessYield calculate(ProcessPlan.MajorProcess process) {
        var primary = sumInputs(process, "PRIMARY");
        var totalInput = process.inputs() == null ? BigDecimal.ZERO : process.inputs().stream()
                .map(ProcessPlan.ProcessInput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        var qualified = sumOutputs(process, "QUALIFIED");
        var reusable = sumOutputs(process, "REUSABLE").add(sumOutputs(process, "TAILING"));
        var totalOutput = process.outputs() == null ? BigDecimal.ZERO : process.outputs().stream()
                .map(ProcessPlan.ProcessOutput::weightKg).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProcessPlan.ProcessYield(
                weight(primary), weight(totalInput), weight(qualified), weight(reusable), weight(totalOutput),
                percent(qualified, primary), percent(qualified.add(reusable), totalInput),
                weight(totalInput.subtract(totalOutput)));
    }

    public BigDecimal calculateBatch(ProcessPlan plan) {
        if (plan == null || plan.majorProcesses() == null || plan.majorProcesses().isEmpty()) return null;
        var firstYield = calculate(plan.majorProcesses().get(0));
        var lastYield = calculate(plan.majorProcesses().get(plan.majorProcesses().size() - 1));
        return percent(lastYield.qualifiedOutputWeightKg(), firstYield.primaryInputWeightKg());
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
