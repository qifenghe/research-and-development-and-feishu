package com.lhr.rnd.service;

import com.lhr.rnd.domain.SampleAction;
import com.lhr.rnd.domain.SampleStatus;
import com.lhr.rnd.domain.SampleStatusMachine;
import com.lhr.rnd.persistence.entity.WorkflowRuleConfigEntity;
import com.lhr.rnd.persistence.repository.WorkflowRuleConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkflowDrivenSampleStatusMachine {
    private static final String DEFAULT_WORKFLOW_CODE = "SAMPLE_RND_FLOW";

    private final WorkflowRuleConfigRepository repository;
    private final SampleStatusMachine fallback = new SampleStatusMachine();

    public WorkflowDrivenSampleStatusMachine(WorkflowRuleConfigRepository repository) {
        this.repository = repository;
    }

    public SampleStatus transition(SampleStatus current, SampleAction action) {
        return repository.findByWorkflowCodeAndCurrentStatusAndActionCode(
                        DEFAULT_WORKFLOW_CODE,
                        current.name(),
                        action.name()
                )
                .map(rule -> transitionFromConfig(current, action, rule))
                .orElseGet(() -> fallback.transition(current, action));
    }

    private SampleStatus transitionFromConfig(
            SampleStatus current,
            SampleAction action,
            WorkflowRuleConfigEntity rule
    ) {
        if (!rule.isEnabled()) {
            throw new IllegalStateException("Disabled sample transition: " + current + " + " + action);
        }
        try {
            return SampleStatus.valueOf(rule.getNextStatus());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid sample transition config: " + current + " + " + action + " -> " + rule.getNextStatus(),
                    ex
            );
        }
    }
}
