package com.lhr.rnd.api;

import com.lhr.rnd.model.ArchiveFileView;
import com.lhr.rnd.model.DashboardOverview;
import com.lhr.rnd.model.RndTask;
import com.lhr.rnd.model.ExperimentForm;
import com.lhr.rnd.model.PricingFileRecord;
import com.lhr.rnd.model.SampleRequest;
import com.lhr.rnd.model.ShipmentRecord;
import com.lhr.rnd.model.StoppedSampleProjectView;
import com.lhr.rnd.service.ApproveSampleRequestResult;
import com.lhr.rnd.service.FailInternalTestResult;
import com.lhr.rnd.service.NotifyFinanceResult;
import com.lhr.rnd.service.PassInternalTestResult;
import com.lhr.rnd.service.SampleWorkflowService;
import com.lhr.rnd.service.ShipmentFeedbackResult;
import com.lhr.rnd.service.SubmitExperimentForTestResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @GetMapping("/dashboard/overview")
    public ApiResponse<DashboardOverview> dashboardOverview() {
        return ApiResponse.success(workflowService.dashboardOverview());
    }

    @GetMapping("/sample-projects/stopped")
    public ApiResponse<List<StoppedSampleProjectView>> stoppedSampleProjects() {
        return ApiResponse.success(workflowService.stoppedProjects());
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

    @PostMapping("/rnd-tasks/{id}/accept")
    public ApiResponse<RndTask> acceptTask(
            @PathVariable String id,
            @Valid @RequestBody AcceptRndTaskRequest request
    ) {
        return ApiResponse.success(workflowService.acceptTask(id, request.acceptedBy()));
    }

    @PostMapping("/rnd-tasks/{id}/experiment-form/draft")
    public ApiResponse<ExperimentForm> saveExperimentDraft(
            @PathVariable String id,
            @Valid @RequestBody SaveExperimentDraftRequest request
    ) {
        return ApiResponse.success(workflowService.saveExperimentDraft(new SampleWorkflowService.SaveExperimentDraftCommand(
                id,
                request.operatorName(),
                request.summary(),
                request.materials()
        )));
    }

    @PostMapping("/experiment-forms/{id}/submit-test")
    public ApiResponse<SubmitExperimentForTestResult> submitExperimentForTest(
            @PathVariable String id,
            @Valid @RequestBody SubmitExperimentForTestRequest request
    ) {
        return ApiResponse.success(workflowService.submitExperimentForTest(id, request.testerName()));
    }

    @PostMapping(value = "/experiment-forms/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArchiveFileView> uploadExperimentAttachment(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String uploadedBy,
            @RequestParam(required = false) String remark
    ) {
        var resolvedFileName = fileName == null || fileName.isBlank() ? file.getOriginalFilename() : fileName;
        try {
            return ApiResponse.success(workflowService.archiveExperimentAttachment(
                    id,
                    resolvedFileName,
                    file.getBytes(),
                    category,
                    uploadedBy,
                    remark,
                    file.getContentType()
            ));
        } catch (IOException exception) {
            throw new BusinessException("ARCHIVE_FILE_READ_FAILED", "上传文件读取失败");
        }
    }

    @PostMapping("/test-assignments/{id}/pass")
    public ApiResponse<PassInternalTestResult> passInternalTest(
            @PathVariable String id,
            @Valid @RequestBody InternalTestDecisionRequest request
    ) {
        return ApiResponse.success(workflowService.passInternalTest(id, request.testerName(), request.comment()));
    }

    @PostMapping("/test-assignments/{id}/fail-resample")
    public ApiResponse<FailInternalTestResult> failInternalTestForResample(
            @PathVariable String id,
            @Valid @RequestBody InternalTestDecisionRequest request
    ) {
        return ApiResponse.success(workflowService.failInternalTestForResample(id, request.testerName(), request.comment()));
    }

    @PostMapping("/sample-versions/{id}/shipments")
    public ApiResponse<ShipmentRecord> createShipment(
            @PathVariable String id,
            @Valid @RequestBody CreateShipmentRequest request
    ) {
        return ApiResponse.success(workflowService.createShipment(new SampleWorkflowService.CreateShipmentCommand(
                id,
                request.quantity(),
                request.receiverName(),
                request.trackingNo(),
                request.remark()
        )));
    }

    @PostMapping("/shipments/{id}/feedback")
    public ApiResponse<ShipmentFeedbackResult> submitCustomerFeedback(
            @PathVariable String id,
            @Valid @RequestBody SubmitCustomerFeedbackRequest request
    ) {
        return ApiResponse.success(workflowService.submitCustomerFeedback(new SampleWorkflowService.SubmitCustomerFeedbackCommand(
                id,
                request.feedbackBy(),
                request.result(),
                request.comment()
        )));
    }

    @PostMapping("/sample-versions/{id}/pricing-files")
    public ApiResponse<PricingFileRecord> generatePricingFile(@PathVariable String id) {
        return ApiResponse.success(workflowService.generatePricingFile(id));
    }

    @PostMapping("/pricing-files/{id}/notify-finance")
    public ApiResponse<NotifyFinanceResult> notifyFinance(
            @PathVariable String id,
            @Valid @RequestBody NotifyFinanceRequest request
    ) {
        return ApiResponse.success(workflowService.notifyFinance(id, request.recipientName(), request.remark()));
    }

    @GetMapping("/sample-versions/{id}/archive-files")
    public ApiResponse<List<ArchiveFileView>> archiveFiles(@PathVariable String id) {
        return ApiResponse.success(workflowService.archiveFiles(id));
    }

    @GetMapping("/archive-files/{id}/download")
    public ResponseEntity<byte[]> downloadArchiveFile(@PathVariable String id) {
        var file = workflowService.downloadArchiveFile(id);
        var encodedFileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(file.content());
    }
}
