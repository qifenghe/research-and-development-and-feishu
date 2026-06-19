package com.lhr.rnd.api;

import com.lhr.rnd.service.WorkflowConfig;
import com.lhr.rnd.service.WorkflowRule;
import com.lhr.rnd.service.WorkflowSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/workflows")
public class WorkflowSettingsController {
    private final WorkflowSettingsService workflowSettingsService;

    public WorkflowSettingsController(WorkflowSettingsService workflowSettingsService) {
        this.workflowSettingsService = workflowSettingsService;
    }

    @GetMapping
    public ApiResponse<List<WorkflowConfig>> workflows() {
        return ApiResponse.success(workflowSettingsService.workflows());
    }

    @GetMapping("/{workflowCode}")
    public ApiResponse<WorkflowConfig> workflow(@PathVariable String workflowCode) {
        return ApiResponse.success(workflowSettingsService.workflow(workflowCode));
    }

    @PutMapping("/{workflowCode}")
    public ApiResponse<WorkflowConfig> saveWorkflow(
            @PathVariable String workflowCode,
            @Valid @RequestBody SaveWorkflowRulesRequest request
    ) {
        var rules = request.rules().stream()
                .map(rule -> new WorkflowRule(
                        rule.currentStatus(),
                        rule.actionCode(),
                        rule.actionLabel(),
                        rule.nextStatus(),
                        rule.enabled(),
                        rule.notifyFeishu(),
                        rule.notifyRole(),
                        rule.sortOrder(),
                        rule.remark()
                ))
                .toList();
        return ApiResponse.success(workflowSettingsService.replaceWorkflow(workflowCode, rules));
    }

    @PostMapping("/defaults/initialize")
    public ApiResponse<List<WorkflowConfig>> initializeDefaultWorkflows() {
        return ApiResponse.success(workflowSettingsService.initializeDefaultWorkflows());
    }
}
