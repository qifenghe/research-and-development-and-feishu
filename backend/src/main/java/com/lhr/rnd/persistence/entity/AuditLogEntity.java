package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {
    @Id
    private String id;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(nullable = false)
    private String action;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(
            String id,
            String businessType,
            String businessId,
            String action,
            String operatorName,
            String detail,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.businessType = businessType;
        this.businessId = businessId;
        this.action = action;
        this.operatorName = operatorName;
        this.detail = detail;
        this.createdAt = createdAt;
    }
}
