package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record TestAssignment(
        String id,
        String experimentFormId,
        String taskId,
        String versionId,
        String testerName,
        TestAssignmentStatus status,
        LocalDateTime assignedAt
) {
}
