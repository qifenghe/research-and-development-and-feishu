package com.lhr.rnd.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ExperimentCalculationService {
    private static final int WEIGHT_SCALE = 4;
    private static final int RATE_SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public ProcessLoss processLoss(BigDecimal inputWeight, BigDecimal outputWeight, BigDecimal residualWeight) {
        var safeResidualWeight = residualWeight == null ? BigDecimal.ZERO : residualWeight;
        var lossWeight = inputWeight.subtract(outputWeight).subtract(safeResidualWeight);
        if (lossWeight.signum() < 0) {
            throw new IllegalArgumentException("Output plus residual weight cannot exceed input weight");
        }

        var lossRate = inputWeight.signum() == 0
                ? null
                : lossWeight.divide(inputWeight, RATE_SCALE, ROUNDING_MODE);
        return new ProcessLoss(
                lossWeight.setScale(WEIGHT_SCALE, ROUNDING_MODE),
                lossRate);
    }

    public BigDecimal finishedYield(BigDecimal finishedProductOutput, BigDecimal primaryRawMaterialInput) {
        if (primaryRawMaterialInput == null || primaryRawMaterialInput.signum() <= 0) {
            return null;
        }
        return finishedProductOutput.divide(primaryRawMaterialInput, RATE_SCALE, ROUNDING_MODE);
    }

    public record ProcessLoss(BigDecimal lossWeight, BigDecimal lossRate) {
    }
}
