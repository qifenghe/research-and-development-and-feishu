package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sample_request")
public class SampleRequestEntity {
    @Id
    private String id;

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

    @Column(name = "creator_name", nullable = false)
    private String creatorName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SampleRequestEntity() {
    }

    public SampleRequestEntity(
            String id,
            String sampleNo,
            String productName,
            String productType,
            String customerName,
            String specification,
            String creatorName,
            String status,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.productType = productType;
        this.customerName = customerName;
        this.specification = specification;
        this.creatorName = creatorName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String sampleNo() {
        return sampleNo;
    }

    public String productName() {
        return productName;
    }

    public String productType() {
        return productType;
    }

    public String customerName() {
        return customerName;
    }

    public String specification() {
        return specification;
    }

    public String creatorName() {
        return creatorName;
    }

    public String status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
