package com.lhr.rnd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveWorkflowRulesRequest(
        @NotNull List<@Valid WorkflowRuleRequest> rules
) {
}
