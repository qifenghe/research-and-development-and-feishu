package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record TestAssignment(
        String id,
        String experimentFormId,
        String taskId,
        String versionId,
        String processRevisionId,
        String testerName,
        String testerUserId,
        TestAssignmentStatus status,
        LocalDateTime assignedAt
) {
    public TestAssignment withStatus(TestAssignmentStatus status) {
        return new TestAssignment(
                id,
                experimentFormId,
                taskId,
                versionId,
                processRevisionId,
                testerName,
                testerUserId,
                status,
                assignedAt
        );
    }

    public TestAssignment(String id, String experimentFormId, String taskId, String versionId,
                          String testerName, TestAssignmentStatus status, LocalDateTime assignedAt) {
        this(id, experimentFormId, taskId, versionId, null, testerName, null, status, assignedAt);
    }

    public TestAssignment(String id, String experimentFormId, String taskId, String versionId,
                          String processRevisionId, String testerName, TestAssignmentStatus status, LocalDateTime assignedAt) {
        this(id, experimentFormId, taskId, versionId, processRevisionId, testerName, null, status, assignedAt);
    }
}
