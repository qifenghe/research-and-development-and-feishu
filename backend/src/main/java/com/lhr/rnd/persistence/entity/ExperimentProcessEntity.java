package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "experiment_process")
public class ExperimentProcessEntity {
    @Id
    private String id;

    @Column(name = "experiment_form_id", nullable = false)
    private String experimentFormId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "process_name", nullable = false)
    private String processName;

    @Column(name = "before_weight_kg")
    private BigDecimal beforeWeightKg;

    @Column(name = "after_weight_kg")
    private BigDecimal afterWeightKg;

    @Column(name = "loss_rate")
    private BigDecimal lossRate;

    @Column(length = 500)
    private String remark;

    protected ExperimentProcessEntity() {
    }

    public ExperimentProcessEntity(
            String id,
            String experimentFormId,
            Integer sequence,
            String processName,
            BigDecimal beforeWeightKg,
            BigDecimal afterWeightKg,
            BigDecimal lossRate,
            String remark
    ) {
        this.id = id;
        this.experimentFormId = experimentFormId;
        this.sequence = sequence;
        this.processName = processName;
        this.beforeWeightKg = beforeWeightKg;
        this.afterWeightKg = afterWeightKg;
        this.lossRate = lossRate;
        this.remark = remark;
    }

    public Integer getSequence() {
        return sequence;
    }

    public String getProcessName() {
        return processName;
    }

    public java.math.BigDecimal getBeforeWeightKg() {
        return beforeWeightKg;
    }

    public java.math.BigDecimal getAfterWeightKg() {
        return afterWeightKg;
    }

    public java.math.BigDecimal getLossRate() {
        return lossRate;
    }

    public String getRemark() {
        return remark;
    }
}
