package com.lhr.rnd.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_record")
public class TestRecordEntity {
    @Id
    private String id;

    @Column(name = "test_assignment_id", nullable = false)
    private String testAssignmentId;

    @Column(name = "experiment_form_id", nullable = false)
    private String experimentFormId;

    @Column(name = "tester_name", nullable = false)
    private String testerName;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "comment")
    private String comment;

    @Column(name = "tested_at", nullable = false)
    private LocalDateTime testedAt;

    protected TestRecordEntity() {
    }

    public TestRecordEntity(
            String id,
            String testAssignmentId,
            String experimentFormId,
            String testerName,
            String result,
            String comment,
            LocalDateTime testedAt
    ) {
        this.id = id;
        this.testAssignmentId = testAssignmentId;
        this.experimentFormId = experimentFormId;
        this.testerName = testerName;
        this.result = result;
        this.comment = comment;
        this.testedAt = testedAt;
    }

    public String getId() { return id; }
    public String getTestAssignmentId() { return testAssignmentId; }
    public String getExperimentFormId() { return experimentFormId; }
    public String getTesterName() { return testerName; }
    public String getResult() { return result; }
    public String getComment() { return comment; }
    public LocalDateTime getTestedAt() { return testedAt; }
}
