package com.lhr.rnd.model;

import java.time.LocalDateTime;
import java.util.List;

public record ExperimentForm(
        String id,
        String taskId,
        String projectId,
        String versionId,
        String sampleNo,
        String productName,
        String versionCode,
        ExperimentFormStatus status,
        String operatorName,
        String summary,
        List<ExperimentMaterial> materials,
        LocalDateTime savedAt,
        LocalDateTime submittedAt
) {
    public ExperimentForm submit(LocalDateTime submittedAt) {
        return new ExperimentForm(
                id,
                taskId,
                projectId,
                versionId,
                sampleNo,
                productName,
                versionCode,
                ExperimentFormStatus.SUBMITTED_FOR_TEST,
                operatorName,
                summary,
                materials,
                savedAt,
                submittedAt
        );
    }

    public ExperimentForm lock() {
        return new ExperimentForm(
                id,
                taskId,
                projectId,
                versionId,
                sampleNo,
                productName,
                versionCode,
                ExperimentFormStatus.LOCKED,
                operatorName,
                summary,
                materials,
                savedAt,
                submittedAt
        );
    }
}
