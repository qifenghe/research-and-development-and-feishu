package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record ArchiveFileView(
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
}
