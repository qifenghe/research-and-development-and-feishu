package com.lhr.rnd.model;

import java.math.BigDecimal;

public record ExperimentProcessStep(
        int sequence,
        String processName,
        BigDecimal beforeWeightKg,
        BigDecimal afterWeightKg,
        BigDecimal lossRate,
        String remark
) {
}
