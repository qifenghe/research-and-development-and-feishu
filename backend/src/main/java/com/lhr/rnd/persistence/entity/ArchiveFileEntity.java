package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.lhr.rnd.model.ArchiveFileView;

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

    @Column(name = "category")
    private String category;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "remark")
    private String remark;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

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
        this(id, businessType, businessId, versionId, fileName, filePath, fileUrl,
                null, null, null, null, null, fileStatus, archivedAt);
    }

    public ArchiveFileEntity(
            String id,
            String businessType,
            String businessId,
            String versionId,
            String fileName,
            String filePath,
            String fileUrl,
            String category,
            String uploadedBy,
            String remark,
            String contentType,
            Long fileSize,
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
        this.category = category;
        this.uploadedBy = uploadedBy;
        this.remark = remark;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileStatus = fileStatus;
        this.archivedAt = archivedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public ArchiveFileView toView() {
        return new ArchiveFileView(
                id,
                businessType,
                businessId,
                versionId,
                fileName,
                filePath,
                fileUrl,
                category,
                uploadedBy,
                remark,
                contentType,
                fileSize,
                fileStatus,
                archivedAt
        );
    }
}
