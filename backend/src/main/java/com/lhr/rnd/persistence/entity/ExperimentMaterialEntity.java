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
            String remark
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
    }
}
