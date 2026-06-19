package com.lhr.rnd.service;

import com.lhr.rnd.domain.SampleAction;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.persistence.entity.WorkflowRuleConfigEntity;
import com.lhr.rnd.persistence.repository.WorkflowRuleConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class WorkflowDrivenSampleStatusMachineTest {

    @Autowired
    private WorkflowRuleConfigRepository repository;

    @Autowired
    private WorkflowDrivenSampleStatusMachine machine;

    @BeforeEach
    void cleanWorkflowRules() {
        repository.deleteAll();
    }

    @Test
    void enabledWorkflowConfigOverridesDefaultTransition() {
        repository.save(new WorkflowRuleConfigEntity(
                "FLOW-CONFIG-001",
                "SAMPLE_RND_FLOW",
                "PENDING_TEST",
                "TEST_PASS",
                "测试通过但直接归档",
                "ARCHIVED",
                true,
                false,
                null,
                10,
                "配置优先生效",
                LocalDateTime.now()
        ));

        assertThat(machine.transition(SampleStatus.PENDING_TEST, SampleAction.TEST_PASS))
                .isEqualTo(SampleStatus.ARCHIVED);
    }

    @Test
    void disabledWorkflowConfigRejectsTransitionInsteadOfFallingBackToDefault() {
        repository.save(new WorkflowRuleConfigEntity(
                "FLOW-CONFIG-002",
                "SAMPLE_RND_FLOW",
                "PENDING_TEST",
                "TEST_PASS",
                "测试通过",
                "SAMPLE_COMPLETED",
                false,
                false,
                null,
                10,
                "关闭该按钮",
                LocalDateTime.now()
        ));

        assertThatThrownBy(() -> machine.transition(SampleStatus.PENDING_TEST, SampleAction.TEST_PASS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Disabled sample transition");
    }

    @Test
    void fallsBackToDefaultStatusMachineWhenWorkflowConfigIsMissing() {
        assertThat(machine.transition(SampleStatus.PENDING_REVIEW, SampleAction.APPROVE_REQUEST))
                .isEqualTo(SampleStatus.PENDING_ASSIGNMENT);
    }
}
