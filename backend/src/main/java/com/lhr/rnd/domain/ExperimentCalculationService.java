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

    public PricingPreview pricingPreview(
            BigDecimal primaryMaterialWeight,
            BigDecimal ingredientWeight,
            BigDecimal finishedOutputWeight,
            BigDecimal finishedOutputQuantity) {
        requireNonNegative(primaryMaterialWeight, "primaryMaterialWeight");
        requireNonNegative(ingredientWeight, "ingredientWeight");
        requireNonNegative(finishedOutputWeight, "finishedOutputWeight");
        requireNonNegative(finishedOutputQuantity, "finishedOutputQuantity");

        var primaryWeight = zeroWhenMissing(normalizeWeight(primaryMaterialWeight));
        var ingredientTotal = zeroWhenMissing(normalizeWeight(ingredientWeight));
        var outputWeight = zeroWhenMissing(normalizeWeight(finishedOutputWeight));
        var outputQuantity = zeroWhenMissing(normalizeWeight(finishedOutputQuantity));

        return new PricingPreview(
                primaryWeight.add(ingredientTotal).setScale(WEIGHT_SCALE, ROUNDING_MODE),
                primaryWeight.signum() > 0
                        ? outputWeight.divide(primaryWeight, RATE_SCALE, ROUNDING_MODE)
                        : BigDecimal.ZERO.setScale(RATE_SCALE, ROUNDING_MODE),
                averageUnitWeightKg(outputWeight, outputQuantity),
                referenceQuantity(outputQuantity));
    }

    public BigDecimal averageUnitWeightKg(BigDecimal finishedOutputWeight, BigDecimal finishedOutputQuantity) {
        requireNonNegative(finishedOutputWeight, "finishedOutputWeight");
        requireNonNegative(finishedOutputQuantity, "finishedOutputQuantity");
        var outputWeight = zeroWhenMissing(normalizeWeight(finishedOutputWeight));
        var outputQuantity = zeroWhenMissing(normalizeWeight(finishedOutputQuantity));
        if (outputQuantity.signum() <= 0) {
            return BigDecimal.ZERO.setScale(WEIGHT_SCALE, ROUNDING_MODE);
        }
        return outputWeight.divide(outputQuantity, WEIGHT_SCALE, ROUNDING_MODE);
    }

    public BigDecimal referenceQuantity(BigDecimal finishedOutputQuantity) {
        requireNonNegative(finishedOutputQuantity, "finishedOutputQuantity");
        return zeroWhenMissing(normalizeWeight(finishedOutputQuantity));
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        return weight == null ? null : weight.setScale(WEIGHT_SCALE, ROUNDING_MODE);
    }

    private BigDecimal zeroWhenMissing(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(WEIGHT_SCALE, ROUNDING_MODE) : value;
    }

    private void requireNonNegative(BigDecimal weight, String fieldName) {
        if (weight != null && weight.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    public record ProcessLoss(BigDecimal lossWeightKg, BigDecimal lossRate) {
    }

    public record PricingPreview(
            BigDecimal totalInputWeightKg,
            BigDecimal primaryMaterialYield,
            BigDecimal averageUnitWeightKg,
            BigDecimal referenceQuantity) {
    }
}
