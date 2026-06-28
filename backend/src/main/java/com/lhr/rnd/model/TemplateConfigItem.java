package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record TemplateConfigItem(
        String id,
        String templateCode,
        String templateName,
        String templateType,
        String filePath,
        String versionNo,
        String status,
        LocalDateTime updatedAt
) {
}
