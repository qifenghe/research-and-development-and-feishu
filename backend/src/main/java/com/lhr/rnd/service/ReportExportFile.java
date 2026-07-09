package com.lhr.rnd.service;

public record ReportExportFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
