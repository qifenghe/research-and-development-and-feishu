package com.lhr.rnd.model;

public record ProcessRevision(
        String id,
        String processPlanId,
        String experimentFormId,
        int revisionNo,
        String sourceRevisionId,
        String changeReason,
        String submittedBy,
        String submittedAt,
        String snapshotHash,
        ProcessPlan snapshot
) {
    public ProcessRevisionSummary summary() {
        return new ProcessRevisionSummary(id, revisionNo, sourceRevisionId, changeReason, submittedBy, submittedAt, snapshotHash);
    }

    public record ProcessRevisionSummary(
            String id,
            int revisionNo,
            String sourceRevisionId,
            String changeReason,
            String submittedBy,
            String submittedAt,
            String snapshotHash
    ) {
    }
}
