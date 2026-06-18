package com.lhr.rnd.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RndTask(
        String id,
        String projectId,
        String versionId,
        String sampleNo,
        String productName,
        String versionCode,
        RndTaskStatus status,
        String assigneeName,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime assignedAt
) {
    public RndTask assign(String assigneeName, LocalDate dueDate, LocalDateTime assignedAt) {
        return new RndTask(
                id,
                projectId,
                versionId,
                sampleNo,
                productName,
                versionCode,
                RndTaskStatus.PENDING_ACCEPTANCE,
                assigneeName,
                dueDate,
                createdAt,
                assignedAt
        );
    }

    public RndTask accept(LocalDateTime acceptedAt) {
        return new RndTask(
                id,
                projectId,
                versionId,
                sampleNo,
                productName,
                versionCode,
                RndTaskStatus.SAMPLING,
                assigneeName,
                dueDate,
                createdAt,
                acceptedAt
        );
    }

    public RndTask withStatus(RndTaskStatus status) {
        return new RndTask(
                id,
                projectId,
                versionId,
                sampleNo,
                productName,
                versionCode,
                status,
                assigneeName,
                dueDate,
                createdAt,
                assignedAt
        );
    }
}
