package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "archive_file")
public class ArchiveFileEntity {
    @Id
    private String id;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "version_id")
    private String versionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_status", nullable = false)
    private String fileStatus;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    protected ArchiveFileEntity() {
    }

    public ArchiveFileEntity(
            String id,
            String businessType,
            String businessId,
            String versionId,
            String fileName,
            String filePath,
            String fileUrl,
            String fileStatus,
            LocalDateTime archivedAt
    ) {
        this.id = id;
        this.businessType = businessType;
        this.businessId = businessId;
        this.versionId = versionId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileUrl = fileUrl;
        this.fileStatus = fileStatus;
        this.archivedAt = archivedAt;
    }
}
