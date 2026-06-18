package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rnd_task")
public class RndTaskEntity {
    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "version_id", nullable = false)
    private String versionId;

    @Column(name = "sample_no", nullable = false)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "version_code", nullable = false)
    private String versionCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    protected RndTaskEntity() {
    }

    public RndTaskEntity(
            String id,
            String projectId,
            String versionId,
            String sampleNo,
            String productName,
            String versionCode,
            String status,
            String assigneeName,
            LocalDate dueDate,
            LocalDateTime createdAt,
            LocalDateTime assignedAt,
            LocalDateTime acceptedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.versionId = versionId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.versionCode = versionCode;
        this.status = status;
        this.assigneeName = assigneeName;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.assignedAt = assignedAt;
        this.acceptedAt = acceptedAt;
    }

    public void assign(String assigneeName, LocalDate dueDate, LocalDateTime assignedAt) {
        this.status = "PENDING_ACCEPTANCE";
        this.assigneeName = assigneeName;
        this.dueDate = dueDate;
        this.assignedAt = assignedAt;
        this.acceptedAt = null;
    }

    public void accept(LocalDateTime acceptedAt) {
        this.status = "SAMPLING";
        this.acceptedAt = acceptedAt;
    }

    public void markPendingTest() {
        this.status = "PENDING_TEST";
    }

    public void complete() {
        this.status = "COMPLETED";
    }
}
