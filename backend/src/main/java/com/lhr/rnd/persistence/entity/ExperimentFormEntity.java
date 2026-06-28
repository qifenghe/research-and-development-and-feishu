package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "experiment_form")
public class ExperimentFormEntity {
    @Id
    private String id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

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

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "summary")
    private String summary;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    protected ExperimentFormEntity() {
    }

    public ExperimentFormEntity(
            String id,
            String taskId,
            String projectId,
            String versionId,
            String sampleNo,
            String productName,
            String versionCode,
            String status,
            String operatorName,
            String summary,
            LocalDateTime savedAt,
            LocalDateTime submittedAt
    ) {
        this.id = id;
        this.taskId = taskId;
        this.projectId = projectId;
        this.versionId = versionId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.versionCode = versionCode;
        this.status = status;
        this.operatorName = operatorName;
        this.summary = summary;
        this.savedAt = savedAt;
        this.submittedAt = submittedAt;
    }

    public void submit(LocalDateTime submittedAt) {
        this.status = "SUBMITTED_FOR_TEST";
        this.submittedAt = submittedAt;
    }

    public void lock() {
        this.status = "LOCKED";
    }

    public String getId() {
        return id;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getSampleNo() {
        return sampleNo;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }
}
