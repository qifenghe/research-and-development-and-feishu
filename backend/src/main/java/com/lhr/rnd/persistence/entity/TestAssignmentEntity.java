package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_assignment")
public class TestAssignmentEntity {
    @Id
    private String id;

    @Column(name = "experiment_form_id", nullable = false, length = 64)
    private String experimentFormId;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "version_id", nullable = false)
    private String versionId;

    @Column(name = "process_revision_id", length = 64)
    private String processRevisionId;

    @Column(name = "tester_name", nullable = false)
    private String testerName;

    @Column(name = "tester_user_id", length = 64)
    private String testerUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    protected TestAssignmentEntity() {
    }

    public TestAssignmentEntity(
            String id,
            String experimentFormId,
            String taskId,
            String versionId,
            String processRevisionId,
            String testerName,
            String testerUserId,
            String status,
            LocalDateTime assignedAt
    ) {
        this.id = id;
        this.experimentFormId = experimentFormId;
        this.taskId = taskId;
        this.versionId = versionId;
        this.processRevisionId = processRevisionId;
        this.testerName = testerName;
        this.testerUserId = testerUserId;
        this.status = status;
        this.assignedAt = assignedAt;
    }

    public TestAssignmentEntity(String id, String experimentFormId, String taskId, String versionId,
                                String testerName, String status, LocalDateTime assignedAt) {
        this(id, experimentFormId, taskId, versionId, null, testerName, null, status, assignedAt);
    }

    public TestAssignmentEntity(String id, String experimentFormId, String taskId, String versionId,
                                String processRevisionId, String testerName, String status, LocalDateTime assignedAt) {
        this(id, experimentFormId, taskId, versionId, processRevisionId, testerName, null, status, assignedAt);
    }

    public void pass() {
        this.status = "PASSED";
    }

    public void failForResample() {
        this.status = "FAILED_RESAMPLE";
    }

    public String getId() {
        return id;
    }

    public String getExperimentFormId() {
        return experimentFormId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getProcessRevisionId() {
        return processRevisionId;
    }

    public String getTesterName() {
        return testerName;
    }

    public String getTesterUserId() {
        return testerUserId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
}
