package com.lhr.rnd.service;

import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.SampleVersion;
import com.lhr.rnd.model.TestAssignment;
import com.lhr.rnd.model.TestRecord;

public record FailInternalTestResult(
        TestAssignment testAssignment,
        TestRecord testRecord,
        SampleVersion nextVersion,
        RndTask nextTask
) {
}
