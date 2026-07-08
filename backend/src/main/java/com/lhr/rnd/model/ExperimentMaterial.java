package com.lhr.rnd.model;

import java.math.BigDecimal;

public record ExperimentMaterial(
        String stage,
        int sequence,
        String materialCode,
        String materialName,
        BigDecimal weightKg,
        BigDecimal utilizationRate,
        String remark,
        String materialCategory,
        boolean primaryMaterial,
        BigDecimal formulaRatio,
        String inputUnit
) {
    public ExperimentMaterial(
            String stage,
            int sequence,
            String materialCode,
            String materialName,
            BigDecimal weightKg,
            BigDecimal utilizationRate,
            String remark
    ) {
        this(stage, sequence, materialCode, materialName, weightKg, utilizationRate, remark,
                null, false, null, null);
    }
}
