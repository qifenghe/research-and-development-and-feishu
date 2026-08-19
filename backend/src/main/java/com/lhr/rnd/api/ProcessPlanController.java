package com.lhr.rnd.api;

import com.lhr.rnd.domain.ProcessSubmissionValidator;
import com.lhr.rnd.model.ExperimentProcessStep;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessRevision;
import com.lhr.rnd.model.ProcessSubmissionCheck;
import com.lhr.rnd.service.ProcessPlanService;
import com.lhr.rnd.service.ProcessRevisionService;
import com.lhr.rnd.service.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}/process-plan")
public class ProcessPlanController {
    private final ProcessPlanService service;
    private final ProcessRevisionService revisionService;
    private final ProcessSubmissionValidator submissionValidator = new ProcessSubmissionValidator();

    public ProcessPlanController(ProcessPlanService service, ProcessRevisionService revisionService) {
        this.service = service;
        this.revisionService = revisionService;
    }

    @GetMapping
    public ApiResponse<ProcessPlan> find(@PathVariable String formId) {
        return ApiResponse.success(service.find(formId));
    }

    @PutMapping
    public ApiResponse<ProcessPlan> save(@PathVariable String formId, @RequestBody ProcessPlan request) {
        return ApiResponse.success(service.save(formId, request));
    }

    @GetMapping("/submission-check")
    public ApiResponse<ProcessSubmissionCheck> submissionCheck(@PathVariable String formId) {
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
                request.versionNo(), request.confirmed(), request.changeReason(), principal.name())));
    }

    @GetMapping("/revisions")
    public ApiResponse<List<ProcessRevision.ProcessRevisionSummary>> revisions(@PathVariable String formId) {
        return ApiResponse.success(revisionService.list(formId));
    }

    @GetMapping("/revisions/{revisionId}")
    public ApiResponse<ProcessRevision> revision(@PathVariable String formId, @PathVariable String revisionId) {
        return ApiResponse.success(revisionService.find(formId, revisionId));
    }

    @PostMapping("/revisions/{revisionId}/new-draft")
    public ApiResponse<ProcessPlan> newDraft(
            @PathVariable String formId,
            @PathVariable String revisionId,
            @RequestBody CreateDraftFromRevisionRequest request
    ) {
        return ApiResponse.success(revisionService.createDraftFromRevision(formId, revisionId, request.changeReason()));
    }

    private SessionPrincipal requiredSessionPrincipal(HttpServletRequest request) {
        var principal = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        if (!(principal instanceof SessionPrincipal sessionPrincipal) || sessionPrincipal.name() == null || sessionPrincipal.name().isBlank()) {
            throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "正式提交必须使用服务端会话身份");
        }
        return sessionPrincipal;
    }

    public record SubmitProcessPlanRequest(int versionNo, boolean confirmed, String changeReason, String submittedBy) {
    }

    public record CreateDraftFromRevisionRequest(String changeReason) {
    }
}
