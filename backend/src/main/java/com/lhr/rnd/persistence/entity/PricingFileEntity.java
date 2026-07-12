package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_file")
public class PricingFileEntity {
    @Id
    private String id;

    @Column(name = "version_id", nullable = false)
    private String versionId;

    @Column(name = "sample_no", nullable = false)
    private String sampleNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "version_code", nullable = false)
    private String versionCode;

    @Column(name = "pricing_version", nullable = false)
    private String pricingVersion;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "content_length", nullable = false)
    private Long contentLength;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    protected PricingFileEntity() {
    }

    public PricingFileEntity(
            String id,
            String versionId,
            String sampleNo,
            String productName,
            String versionCode,
            String pricingVersion,
            String fileName,
            String status,
            Long contentLength,
            LocalDateTime generatedAt
    ) {
        this(
                id,
                versionId,
                sampleNo,
                productName,
                versionCode,
                pricingVersion,
                fileName,
                status,
                contentLength,
                generatedAt,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public PricingFileEntity(
            String id,
            String versionId,
            String sampleNo,
            String productName,
            String versionCode,
            String pricingVersion,
            String fileName,
            String status,
            Long contentLength,
            LocalDateTime generatedAt,
            String receivedBy,
            LocalDateTime receivedAt,
            String reviewedBy,
            LocalDateTime reviewedAt,
            String reviewComment,
            String rejectionReason
    ) {
        this.id = id;
        this.versionId = versionId;
        this.sampleNo = sampleNo;
        this.productName = productName;
        this.versionCode = versionCode;
        this.pricingVersion = pricingVersion;
        this.fileName = fileName;
        this.status = status;
        this.contentLength = contentLength;
        this.generatedAt = generatedAt;
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
        this.rejectionReason = rejectionReason;
    }

    public void markFinanceNotified() {
        this.status = "FINANCE_NOTIFIED";
    }

    public void markFinanceReceived(String receivedBy, LocalDateTime receivedAt) {
        if ("FINANCE_RECEIVED".equals(status)) {
            return;
        }
        this.status = "FINANCE_RECEIVED";
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
    }

    public void markReviewed(String status, String reviewerName, LocalDateTime reviewedAt, String comment, String rejectionReason) {
        this.status = status;
        this.reviewedBy = reviewerName;
        this.reviewedAt = reviewedAt;
        this.reviewComment = comment;
        this.rejectionReason = rejectionReason;
    }

    public String getId() { return id; }
    public String getVersionId() { return versionId; }
    public String getSampleNo() { return sampleNo; }
    public String getProductName() { return productName; }
    public String getVersionCode() { return versionCode; }
    public String getPricingVersion() { return pricingVersion; }
    public String getFileName() { return fileName; }
    public String getStatus() { return status; }
    public Long getContentLength() { return contentLength; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getReceivedBy() { return receivedBy; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public String getRejectionReason() { return rejectionReason; }
}
