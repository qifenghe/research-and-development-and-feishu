package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.TestAssignment;
import com.lhr.rnd.model.TestRecord;

public record PassInternalTestResult(
        ExperimentForm experimentForm,
        TestAssignment testAssignment,
        TestRecord testRecord,
        RndTask task
) {
}
