package com.lhr.rnd.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleStatusMachineTest {

    @Test
    void allowsMainHappyPathTransitions() {
        var machine = new SampleStatusMachine();

        assertThat(machine.transition(SampleStatus.PENDING_REVIEW, SampleAction.APPROVE_REQUEST))
                .isEqualTo(SampleStatus.PENDING_ASSIGNMENT);
        assertThat(machine.transition(SampleStatus.PENDING_ASSIGNMENT, SampleAction.ASSIGN_TASK))
                .isEqualTo(SampleStatus.PENDING_ACCEPTANCE);
        assertThat(machine.transition(SampleStatus.PENDING_ACCEPTANCE, SampleAction.ACCEPT_TASK))
                .isEqualTo(SampleStatus.SAMPLING);
        assertThat(machine.transition(SampleStatus.SAMPLING, SampleAction.SUBMIT_EXPERIMENT))
                .isEqualTo(SampleStatus.PENDING_TEST);
        assertThat(machine.transition(SampleStatus.PENDING_TEST, SampleAction.TEST_PASS))
                .isEqualTo(SampleStatus.SAMPLE_COMPLETED);
        assertThat(machine.transition(SampleStatus.SAMPLE_COMPLETED, SampleAction.REQUEST_PRICING))
                .isEqualTo(SampleStatus.PRICING_FILE_GENERATED);
        assertThat(machine.transition(SampleStatus.PRICING_FILE_GENERATED, SampleAction.NOTIFY_FINANCE))
                .isEqualTo(SampleStatus.FINANCE_NOTIFIED);
        assertThat(machine.transition(SampleStatus.FINANCE_NOTIFIED, SampleAction.ARCHIVE))
                .isEqualTo(SampleStatus.ARCHIVED);
    }

    @Test
    void rejectsIllegalManualJump() {
        var machine = new SampleStatusMachine();

        assertThatThrownBy(() -> machine.transition(SampleStatus.PENDING_REVIEW, SampleAction.ACCEPT_TASK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_REVIEW")
                .hasMessageContaining("ACCEPT_TASK");
    }

    @Test
    void sendsFailedTestBackToResampling() {
        var machine = new SampleStatusMachine();

        assertThat(machine.transition(SampleStatus.PENDING_TEST, SampleAction.TEST_FAIL_RESAMPLE))
                .isEqualTo(SampleStatus.RESAMPLING_REQUIRED);
        assertThat(machine.transition(SampleStatus.RESAMPLING_REQUIRED, SampleAction.CREATE_NEXT_VERSION))
                .isEqualTo(SampleStatus.PENDING_ACCEPTANCE);
    }

    @Test
    void formatsUnboundedSampleVersionCodes() {
        assertThat(SampleVersionCode.fromNumber(0).code()).isEqualTo("A0");
        assertThat(SampleVersionCode.fromNumber(1).code()).isEqualTo("A1");
        assertThat(SampleVersionCode.fromNumber(3).code()).isEqualTo("A3");
        assertThat(SampleVersionCode.fromNumber(10).code()).isEqualTo("A10");
    }
}
