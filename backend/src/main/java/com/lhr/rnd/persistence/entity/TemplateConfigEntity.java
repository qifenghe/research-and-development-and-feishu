package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_config")
public class TemplateConfigEntity {
    @Id
    private String id;

    @Column(name = "template_code", nullable = false, unique = true)
    private String templateCode;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "template_type", nullable = false)
    private String templateType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "version_no", nullable = false)
    private String versionNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TemplateConfigEntity() {
    }

    public TemplateConfigEntity(
            String id,
            String templateCode,
            String templateName,
            String templateType,
            String filePath,
            String versionNo,
            String status,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.templateCode = templateCode;
        this.templateName = templateName;
        this.templateType = templateType;
        this.filePath = filePath;
        this.versionNo = versionNo;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateType() {
        return templateType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String templateName, String filePath, String versionNo, String status, LocalDateTime updatedAt) {
        this.templateName = templateName;
        this.filePath = filePath;
        this.versionNo = versionNo;
        this.status = status;
        this.updatedAt = updatedAt;
    }
}
