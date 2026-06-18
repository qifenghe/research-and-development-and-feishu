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
    public TestAssignment withStatus(TestAssignmentStatus status) {
        return new TestAssignment(
                id,
                experimentFormId,
                taskId,
                versionId,
                testerName,
                status,
                assignedAt
        );
    }
}
