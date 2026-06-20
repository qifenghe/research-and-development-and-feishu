package com.lhr.rnd.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DashboardTaskItem(
        String taskId,
        String sampleNo,
        String productName,
        String versionCode,
        String status,
        String assigneeName,
        LocalDate dueDate,
        LocalDateTime createdAt
) {
}
