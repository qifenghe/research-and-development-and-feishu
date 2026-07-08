package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "experiment_material")
public class ExperimentMaterialEntity {
    @Id
    private String id;

    @Column(name = "experiment_form_id", nullable = false)
    private String experimentFormId;

    @Column(name = "stage")
    private String stage;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "material_code")
    private String materialCode;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "utilization_rate", nullable = false)
    private BigDecimal utilizationRate;

    @Column(name = "remark")
    private String remark;

    @Column(name = "material_category", nullable = false)
    private String materialCategory;

    @Column(name = "is_primary_material", nullable = false)
    private Boolean primaryMaterial;

    @Column(name = "formula_ratio")
    private BigDecimal formulaRatio;

    @Column(name = "input_unit", nullable = false)
    private String inputUnit;

    protected ExperimentMaterialEntity() {
    }

    public ExperimentMaterialEntity(
            String id,
            String experimentFormId,
            String stage,
            Integer sequence,
            String materialCode,
            String materialName,
            BigDecimal weightKg,
            BigDecimal utilizationRate,
            String remark,
            String materialCategory,
            Boolean primaryMaterial,
            BigDecimal formulaRatio,
            String inputUnit
    ) {
        this.id = id;
        this.experimentFormId = experimentFormId;
        this.stage = stage;
        this.sequence = sequence;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.weightKg = weightKg;
        this.utilizationRate = utilizationRate;
        this.remark = remark;
        this.materialCategory = materialCategory;
        this.primaryMaterial = primaryMaterial;
        this.formulaRatio = formulaRatio;
        this.inputUnit = inputUnit;
    }

    public String getStage() {
        return stage;
    }

    public Integer getSequence() {
        return sequence;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getUtilizationRate() {
        return utilizationRate;
    }

    public String getRemark() {
        return remark;
    }

    public String getMaterialCategory() {
        return materialCategory;
    }

    public Boolean getPrimaryMaterial() {
        return primaryMaterial;
    }

    public BigDecimal getFormulaRatio() {
        return formulaRatio;
    }

    public String getInputUnit() {
        return inputUnit;
    }
}
