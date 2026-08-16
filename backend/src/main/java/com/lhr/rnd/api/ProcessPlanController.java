package com.lhr.rnd.api;

import com.lhr.rnd.model.ExperimentProcessStep;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.service.ProcessPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}/process-plan")
public class ProcessPlanController {
    private final ProcessPlanService service;

    public ProcessPlanController(ProcessPlanService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ProcessPlan> find(@PathVariable String formId) {
        return ApiResponse.success(service.find(formId));
    }

    @PutMapping
    public ApiResponse<ProcessPlan> save(@PathVariable String formId, @RequestBody ProcessPlan request) {
        return ApiResponse.success(service.save(formId, request));
    }

    @GetMapping("/legacy-summary")
    public ApiResponse<List<ExperimentProcessStep>> legacySummary(@PathVariable String formId) {
        return ApiResponse.success(service.legacySummaries(service.find(formId)));
    }
}
