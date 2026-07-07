package com.lhr.rnd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ExperimentCalculationService {
    private static final int WEIGHT_SCALE = 4;
    private static final int RATE_SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public ProcessLoss processLoss(BigDecimal inputWeight, BigDecimal outputWeight, BigDecimal residualWeight) {
        requireNonNegative(inputWeight, "inputWeight");
        requireNonNegative(outputWeight, "outputWeight");
        requireNonNegative(residualWeight, "residualWeight");
        var normalizedInputWeight = normalizeWeight(inputWeight);
        var normalizedOutputWeight = normalizeWeight(outputWeight);
        var normalizedResidualWeight = normalizeWeight(residualWeight);

        if (normalizedInputWeight == null || normalizedOutputWeight == null) {
            return new ProcessLoss(null, null);
        }

        var safeResidualWeight = normalizedResidualWeight == null
                ? BigDecimal.ZERO.setScale(WEIGHT_SCALE, ROUNDING_MODE)
                : normalizedResidualWeight;
        var lossWeightKg = normalizedInputWeight.subtract(normalizedOutputWeight).subtract(safeResidualWeight);
        if (lossWeightKg.signum() < 0) {
            throw new IllegalArgumentException("Output plus residual weight cannot exceed input weight");
        }

        var lossRate = normalizedInputWeight.signum() == 0
                ? null
                : lossWeightKg.divide(normalizedInputWeight, RATE_SCALE, ROUNDING_MODE);
        return new ProcessLoss(lossWeightKg.setScale(WEIGHT_SCALE, ROUNDING_MODE), lossRate);
    }

    public BigDecimal finishedYield(BigDecimal finishedProductOutput, BigDecimal primaryRawMaterialInput) {
        requireNonNegative(finishedProductOutput, "finishedProductOutput");
        requireNonNegative(primaryRawMaterialInput, "primaryRawMaterialInput");
        var normalizedFinishedProductOutput = normalizeWeight(finishedProductOutput);
        var normalizedPrimaryRawMaterialInput = normalizeWeight(primaryRawMaterialInput);

        if (normalizedFinishedProductOutput == null
                || normalizedPrimaryRawMaterialInput == null
                || normalizedPrimaryRawMaterialInput.signum() == 0) {
            return null;
        }
        return normalizedFinishedProductOutput.divide(normalizedPrimaryRawMaterialInput, RATE_SCALE, ROUNDING_MODE);
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        return weight == null ? null : weight.setScale(WEIGHT_SCALE, ROUNDING_MODE);
    }

    private void requireNonNegative(BigDecimal weight, String fieldName) {
        if (weight != null && weight.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    public record ProcessLoss(BigDecimal lossWeightKg, BigDecimal lossRate) {
    }
}
