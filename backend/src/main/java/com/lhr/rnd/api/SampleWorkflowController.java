package com.lhr.rnd.api;

import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.SampleRequest;
import com.lhr.rnd.service.ApproveSampleRequestResult;
import com.lhr.rnd.service.SampleWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SampleWorkflowController {
    private final SampleWorkflowService workflowService;

    public SampleWorkflowController(SampleWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/sample-requests")
    public ApiResponse<SampleRequest> createSampleRequest(@Valid @RequestBody CreateSampleRequestRequest request) {
        var created = workflowService.createRequest(new SampleWorkflowService.CreateSampleRequestCommand(
                request.productName(),
                request.productType(),
                request.customerName(),
                request.specification(),
                request.creatorName()
        ));
        return ApiResponse.success(created);
    }

    @GetMapping("/sample-requests")
    public ApiResponse<List<SampleRequest>> sampleRequests() {
        return ApiResponse.success(workflowService.requests());
    }

    @PostMapping("/sample-requests/{id}/approve")
    public ApiResponse<ApproveSampleRequestResult> approveSampleRequest(
            @PathVariable String id,
            @Valid @RequestBody ApproveSampleRequestRequest request
    ) {
        return ApiResponse.success(workflowService.approveRequest(id, request.reviewerName()));
    }

    @GetMapping("/rnd-tasks/pool")
    public ApiResponse<List<RndTask>> taskPool() {
        return ApiResponse.success(workflowService.taskPool());
    }

    @PostMapping("/rnd-tasks/{id}/assign")
    public ApiResponse<RndTask> assignTask(
            @PathVariable String id,
            @Valid @RequestBody AssignRndTaskRequest request
    ) {
        return ApiResponse.success(workflowService.assignTask(id, request.assigneeName(), request.dueDate()));
    }
}
