package com.lhr.rnd.model;

import java.math.BigDecimal;

public record ExperimentProcessStep(
        int sequence,
        String processName,
        BigDecimal beforeWeightKg,
        BigDecimal afterWeightKg,
        BigDecimal remainingWeightKg,
        String remainingDisposition,
        BigDecimal lossWeightKg,
        BigDecimal lossRate,
        String remark
) {
    public ExperimentProcessStep(
            int sequence,
            String processName,
            BigDecimal beforeWeightKg,
            BigDecimal afterWeightKg,
            BigDecimal lossRate,
            String remark
    ) {
        this(sequence, processName, beforeWeightKg, afterWeightKg, null, null, null, lossRate, remark);
    }
}
