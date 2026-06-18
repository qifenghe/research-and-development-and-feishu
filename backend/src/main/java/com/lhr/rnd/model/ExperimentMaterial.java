package com.lhr.rnd.model;

import java.math.BigDecimal;

public record ExperimentMaterial(
        String stage,
        int sequence,
        String materialCode,
        String materialName,
        BigDecimal weightKg,
        BigDecimal utilizationRate,
        String remark
) {
}
