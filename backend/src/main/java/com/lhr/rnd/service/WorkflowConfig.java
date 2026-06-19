package com.lhr.rnd.service;

import com.lhr.rnd.model.WorkflowRuleConfigItem;

import java.util.List;

public record WorkflowConfig(
        String workflowCode,
        List<WorkflowRuleConfigItem> rules
) {
}
