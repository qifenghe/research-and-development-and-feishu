package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record TestRecord(
        String id,
        String testAssignmentId,
        String experimentFormId,
        String testerName,
        TestAssignmentStatus result,
        String comment,
        LocalDateTime testedAt
) {
}
