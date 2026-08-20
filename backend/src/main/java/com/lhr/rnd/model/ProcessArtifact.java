package com.lhr.rnd.model;

/** Immutable metadata for one generated file from a formal process revision. */
public record ProcessArtifact(
        String id,
        String processRevisionId,
        String artifactType,
        String documentVersion,
        String status,
        String generatedAt,
        String generatedBy,
        String storageKey,
        String contentSummary,
        String failureReason,
        String generatedByUserId,
        String contentSha256,
        Long byteSize
) {
    public static final String FORMULA_XLSX = "FORMULA_XLSX";
    public static final String SOP_DOCX = "SOP_DOCX";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";
}
