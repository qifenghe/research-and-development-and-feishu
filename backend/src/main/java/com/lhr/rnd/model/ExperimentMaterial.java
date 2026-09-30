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
        String inputUnit,
        String catalogMaterialId,
        String catalogSpecification,
        String catalogUnit,
        boolean refreshCatalogSnapshot,
        String id
) {
    public ExperimentMaterial(String stage,int sequence,String materialCode,String materialName,BigDecimal weightKg,
            BigDecimal utilizationRate,String remark,String materialCategory,boolean primaryMaterial,BigDecimal formulaRatio,String inputUnit,
            String catalogMaterialId,String catalogSpecification,String catalogUnit,boolean refreshCatalogSnapshot) {
        this(stage,sequence,materialCode,materialName,weightKg,utilizationRate,remark,materialCategory,primaryMaterial,formulaRatio,inputUnit,catalogMaterialId,catalogSpecification,catalogUnit,refreshCatalogSnapshot,null);
    }
    public ExperimentMaterial(String stage,int sequence,String materialCode,String materialName,BigDecimal weightKg,
            BigDecimal utilizationRate,String remark,String materialCategory,boolean primaryMaterial,BigDecimal formulaRatio,String inputUnit,
            String catalogMaterialId,String catalogSpecification,String catalogUnit) {
        this(stage,sequence,materialCode,materialName,weightKg,utilizationRate,remark,materialCategory,primaryMaterial,formulaRatio,inputUnit,catalogMaterialId,catalogSpecification,catalogUnit,false);
    }
    public ExperimentMaterial(String stage,int sequence,String materialCode,String materialName,BigDecimal weightKg,
            BigDecimal utilizationRate,String remark,String materialCategory,boolean primaryMaterial,BigDecimal formulaRatio,String inputUnit) {
        this(stage,sequence,materialCode,materialName,weightKg,utilizationRate,remark,materialCategory,primaryMaterial,formulaRatio,inputUnit,null,null,null);
    }
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
