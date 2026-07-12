package com.lhr.rnd.domain;

import java.util.EnumMap;
import java.util.Map;

public class SampleStatusMachine {
    private final Map<SampleStatus, Map<SampleAction, SampleStatus>> transitions = new EnumMap<>(SampleStatus.class);

    public SampleStatusMachine() {
        allow(SampleStatus.PENDING_REVIEW, SampleAction.APPROVE_REQUEST, SampleStatus.PENDING_ASSIGNMENT);
        allow(SampleStatus.PENDING_ASSIGNMENT, SampleAction.ASSIGN_TASK, SampleStatus.PENDING_ACCEPTANCE);
        allow(SampleStatus.PENDING_ACCEPTANCE, SampleAction.ACCEPT_TASK, SampleStatus.SAMPLING);
        allow(SampleStatus.SAMPLING, SampleAction.SUBMIT_EXPERIMENT, SampleStatus.PENDING_TEST);
        allow(SampleStatus.PENDING_TEST, SampleAction.TEST_PASS, SampleStatus.SAMPLE_COMPLETED);
        allow(SampleStatus.PENDING_TEST, SampleAction.TEST_FAIL_RESAMPLE, SampleStatus.RESAMPLING_REQUIRED);
        allow(SampleStatus.RESAMPLING_REQUIRED, SampleAction.CREATE_NEXT_VERSION, SampleStatus.PENDING_ACCEPTANCE);
        allow(SampleStatus.SAMPLE_COMPLETED, SampleAction.CUSTOMER_FEEDBACK_PASS, SampleStatus.SAMPLE_COMPLETED);
        allow(SampleStatus.SAMPLE_COMPLETED, SampleAction.CUSTOMER_FEEDBACK_RESAMPLE, SampleStatus.RESAMPLING_REQUIRED);
        allow(SampleStatus.SAMPLE_COMPLETED, SampleAction.CUSTOMER_FEEDBACK_STOP, SampleStatus.STOPPED);
        allow(SampleStatus.SAMPLE_COMPLETED, SampleAction.REQUEST_PRICING, SampleStatus.PENDING_PRICING_REVIEW);
        allow(SampleStatus.PENDING_PRICING_REVIEW, SampleAction.APPROVE_PRICING, SampleStatus.PRICING_APPROVED);
        allow(SampleStatus.PENDING_PRICING_REVIEW, SampleAction.REJECT_PRICING, SampleStatus.PRICING_REJECTED);
        allow(SampleStatus.PRICING_APPROVED, SampleAction.NOTIFY_FINANCE, SampleStatus.FINANCE_NOTIFIED);
        allow(SampleStatus.FINANCE_NOTIFIED, SampleAction.ARCHIVE, SampleStatus.ARCHIVED);
    }

    public SampleStatus transition(SampleStatus current, SampleAction action) {
        var next = transitions.getOrDefault(current, Map.of()).get(action);
        if (next == null) {
            throw new IllegalStateException("Illegal sample transition: " + current + " + " + action);
        }
        return next;
    }

    private void allow(SampleStatus current, SampleAction action, SampleStatus next) {
        transitions.computeIfAbsent(current, ignored -> new EnumMap<>(SampleAction.class)).put(action, next);
    }
}
