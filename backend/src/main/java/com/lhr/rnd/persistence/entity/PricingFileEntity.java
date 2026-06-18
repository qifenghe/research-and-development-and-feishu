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
    }

    public void markFinanceNotified() {
        this.status = "FINANCE_NOTIFIED";
    }
}
