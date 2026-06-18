package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sample_version")
public class SampleVersionEntity {
    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "sample_no", nullable = false)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "specification", nullable = false)
    private String specification;

    @Column(name = "version_no", nullable = false)
    private String versionNo;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "version_code", nullable = false)
    private String versionCode;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "reference_output_kg")
    private BigDecimal referenceOutputKg;

    @Column(name = "unit_weight_kg")
    private BigDecimal unitWeightKg;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SampleVersionEntity() {
    }

    public SampleVersionEntity(
            String id,
            String projectId,
            String sampleNo,
            String productName,
            String productType,
            String specification,
            String versionNo,
            Integer versionNumber,
            String versionCode,
            String ownerName,
            String authorName,
            LocalDate effectiveDate,
            BigDecimal referenceOutputKg,
            BigDecimal unitWeightKg,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.productType = productType;
        this.specification = specification;
        this.versionNo = versionNo;
        this.versionNumber = versionNumber;
        this.versionCode = versionCode;
        this.ownerName = ownerName;
        this.authorName = authorName;
        this.effectiveDate = effectiveDate;
        this.referenceOutputKg = referenceOutputKg;
        this.unitWeightKg = unitWeightKg;
        this.createdAt = createdAt;
    }
}
