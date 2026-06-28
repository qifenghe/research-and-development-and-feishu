package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sample_project")
public class SampleProjectEntity {
    @Id
    private String id;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "sample_no", nullable = false, unique = true)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "specification", nullable = false)
    private String specification;

    @Column(name = "application_scenario")
    private String applicationScenario;

    @Column(name = "flavor_requirement")
    private String flavorRequirement;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SampleProjectEntity() {
    }

    public SampleProjectEntity(
            String id,
            String requestId,
            String sampleNo,
            String productName,
            String productType,
            String customerName,
            String specification,
            String applicationScenario,
            String flavorRequirement,
            String status,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.requestId = requestId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.productType = productType;
        this.customerName = customerName;
        this.specification = specification;
        this.applicationScenario = applicationScenario;
        this.flavorRequirement = flavorRequirement;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
