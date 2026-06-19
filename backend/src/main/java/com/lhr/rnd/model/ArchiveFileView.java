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
        String fileStatus,
        LocalDateTime archivedAt
) {
}
