package com.lhr.rnd.api;

import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.model.ExperimentProcessStep;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessArtifact;
import com.lhr.rnd.model.ProcessRevision;
import com.lhr.rnd.model.ProcessSubmissionCheck;
import com.lhr.rnd.service.ProcessPlanService;
import com.lhr.rnd.service.ProcessRevisionService;
import com.lhr.rnd.service.ProcessArtifactService;
import com.lhr.rnd.service.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}/process-plan")
public class ProcessPlanController {
    private final ProcessPlanService service;
    private final ProcessRevisionService revisionService;
    private final ProcessArtifactService artifactService;
    private final com.lhr.rnd.service.ProcessYieldComparisonService comparisons;
    private final ProcessSubmissionValidator submissionValidator = new ProcessSubmissionValidator();

    public ProcessPlanController(ProcessPlanService service, ProcessRevisionService revisionService, ProcessArtifactService artifactService,
                                 com.lhr.rnd.service.ProcessYieldComparisonService comparisons) {
        this.service = service;
        this.revisionService = revisionService;
        this.artifactService = artifactService;
        this.comparisons = comparisons;
    }

    @GetMapping
    public ApiResponse<ProcessPlan> find(@PathVariable String formId, HttpServletRequest servletRequest) {
        var principal = sessionPrincipal(servletRequest);
        if (principal != null) service.requireDraftReadAccess(formId, principal);
        return ApiResponse.success(service.find(formId));
    }

    @PutMapping
    public ApiResponse<ProcessPlan> save(@PathVariable String formId, @RequestBody ProcessPlan request, HttpServletRequest servletRequest) {
        return ApiResponse.success(service.save(formId, request, requiredSessionPrincipal(servletRequest)));
    }

    @PostMapping("/control-points/{pointId}/confirm-deviation")
    public ApiResponse<ProcessPlan> confirmDeviation(
            @PathVariable String formId,
            @PathVariable String pointId,
            @RequestBody ConfirmDeviationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(service.confirmCriticalDeviation(formId, pointId, request.resolutionNote(), requiredSessionPrincipal(servletRequest)));
    }

    @GetMapping("/submission-check")
    public ApiResponse<ProcessSubmissionCheck> submissionCheck(@PathVariable String formId, HttpServletRequest servletRequest) {
        service.requireDraftReadAccess(formId, requiredSessionPrincipal(servletRequest));
        return ApiResponse.success(submissionValidator.validate(service.find(formId)));
    }

    @GetMapping("/legacy-summary")
    public ApiResponse<List<ExperimentProcessStep>> legacySummary(@PathVariable String formId) {
        return ApiResponse.success(service.legacySummaries(service.find(formId)));
    }

    @PostMapping("/submit")
    public ApiResponse<ProcessRevision> submit(
            @PathVariable String formId,
            @RequestBody SubmitProcessPlanRequest request,
            HttpServletRequest servletRequest
    ) {
        var principal = requiredSessionPrincipal(servletRequest);
        return ApiResponse.success(revisionService.submit(formId, new ProcessRevisionService.SubmitCommand(
                request.versionNo(), request.confirmed(), request.changeReason(), principal.name()), principal));
    }

    @GetMapping("/revisions")
    public ApiResponse<List<ProcessRevision.ProcessRevisionSummary>> revisions(@PathVariable String formId, HttpServletRequest servletRequest) {
        return ApiResponse.success(revisionService.list(formId, requiredSessionPrincipal(servletRequest)));
    }

    @GetMapping("/revisions/comparison")
    public ApiResponse<?> comparison(@PathVariable String formId, HttpServletRequest request) {
        return ApiResponse.success(comparisons.compare(formId, requiredSessionPrincipal(request)));
    }

    @GetMapping("/revisions/{revisionId}")
    public ApiResponse<ProcessRevision> revision(@PathVariable String formId, @PathVariable String revisionId, HttpServletRequest servletRequest) {
        return ApiResponse.success(revisionService.find(formId, revisionId, requiredSessionPrincipal(servletRequest)));
    }

    @PostMapping("/revisions/{revisionId}/new-draft")
    public ApiResponse<ProcessPlan> newDraft(
            @PathVariable String formId,
            @PathVariable String revisionId,
            @RequestBody CreateDraftFromRevisionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(revisionService.createDraftFromRevision(formId, revisionId, request.changeReason(), requiredSessionPrincipal(servletRequest)));
    }

    @GetMapping("/revisions/{revisionId}/artifacts")
    public ApiResponse<?> artifacts(
            @PathVariable String formId,
            @PathVariable String revisionId,
            HttpServletRequest servletRequest
    ) {
        var principal = requiredSessionPrincipal(servletRequest);
        if ("TESTER".equals(principal.role()) || "QA_TESTER".equals(principal.role())) {
            return ApiResponse.success(artifactService.listReadyPublic(formId, revisionId, principal));
        }
        return ApiResponse.success(artifactService.list(formId, revisionId, principal));
    }

    @PostMapping("/revisions/{revisionId}/artifacts")
    public ApiResponse<ProcessArtifact> generateArtifact(
            @PathVariable String formId,
            @PathVariable String revisionId,
            @RequestBody GenerateProcessArtifactRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(artifactService.generate(formId, revisionId, request.artifactType(), requiredSessionPrincipal(servletRequest)));
    }

    @GetMapping("/revisions/{revisionId}/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> downloadArtifact(
            @PathVariable String formId,
            @PathVariable String revisionId,
            @PathVariable String artifactId,
            HttpServletRequest servletRequest
    ) {
        var file = artifactService.download(formId, revisionId, artifactId, requiredSessionPrincipal(servletRequest));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentLength(file.content().length)
                .body(file.content());
    }

    private SessionPrincipal requiredSessionPrincipal(HttpServletRequest request) {
        var principal = sessionPrincipal(request);
        if (principal == null || principal.name() == null || principal.name().isBlank()) {
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "工艺操作必须使用服务端会话身份");
        }
        return principal;
    }

    private SessionPrincipal sessionPrincipal(HttpServletRequest request) {
        var principal = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        return principal instanceof SessionPrincipal sessionPrincipal ? sessionPrincipal : null;
    }

    public record SubmitProcessPlanRequest(int versionNo, boolean confirmed, String changeReason, String submittedBy) {
    }

    public record CreateDraftFromRevisionRequest(String changeReason) {
    }

    public record GenerateProcessArtifactRequest(String artifactType) {
    }

    public record ConfirmDeviationRequest(String resolutionNote) {
    }
}
