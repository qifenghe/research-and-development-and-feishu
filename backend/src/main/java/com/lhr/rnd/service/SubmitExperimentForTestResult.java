package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.TestAssignment;

public record SubmitExperimentForTestResult(ExperimentForm experimentForm, TestAssignment testAssignment) {
}
