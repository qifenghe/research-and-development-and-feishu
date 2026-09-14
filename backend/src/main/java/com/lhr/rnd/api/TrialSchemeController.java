package com.lhr.rnd.api;

import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.TrialScheme;
import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.TrialSchemeService;
import com.lhr.rnd.service.TrialPromotionService;
import com.lhr.rnd.model.ProcessRevision;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}/trials")
public class TrialSchemeController {
    private final TrialSchemeService service;
    private final TrialPromotionService promotion;

    public TrialSchemeController(TrialSchemeService service, TrialPromotionService promotion) {
        this.service = service;
        this.promotion = promotion;
    }

    @GetMapping
    public ApiResponse<List<TrialScheme>> list(@PathVariable String formId, HttpServletRequest request) {
        return ApiResponse.success(service.list(formId, principal(request)));
    }

    @PostMapping
    public ApiResponse<TrialScheme> create(@PathVariable String formId, @RequestBody CreateTrialRequest body,
                                           HttpServletRequest request) {
        return ApiResponse.success(service.create(formId, new TrialSchemeService.CreateCommand(
                body.name(), body.purpose(), body.variables(), body.plan(), body.plannedData()), principal(request)));
    }

    @GetMapping("/{trialId}")
    public ApiResponse<TrialScheme> find(@PathVariable String formId, @PathVariable String trialId,
                                         HttpServletRequest request) {
        return ApiResponse.success(service.find(formId, trialId, principal(request)));
    }

    @PutMapping("/{trialId}")
    public ApiResponse<TrialScheme> save(@PathVariable String formId, @PathVariable String trialId,
                                         @RequestBody SaveTrialRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.save(formId, trialId, new TrialSchemeService.SaveCommand(
                body.versionNo(), body.name(), body.purpose(), body.variables(), body.conclusion(), body.recommendationReason(),
                body.qualityScore(), body.qualityNotes(), body.difficulty(), body.plan(), body.plannedData()), principal(request)));
    }

    @PostMapping("/{trialId}/copy")
    public ApiResponse<TrialScheme> copy(@PathVariable String formId, @PathVariable String trialId,
                                         @RequestBody CopyTrialRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.copy(formId, trialId,
                new TrialSchemeService.CopyCommand(body.versionNo(), body.name(), body.includeActuals()), principal(request)));
    }

    @PostMapping("/{trialId}/archive")
    public ApiResponse<TrialScheme> archive(@PathVariable String formId, @PathVariable String trialId,
                                            @RequestBody ArchiveTrialRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.archive(formId, trialId,
                new TrialSchemeService.ArchiveCommand(body.versionNo(), body.archived()), principal(request)));
    }

    @PostMapping("/{trialId}/control-points/{pointId}/confirm")
    public ApiResponse<TrialScheme> confirm(@PathVariable String formId, @PathVariable String trialId, @PathVariable String pointId,
                                            @RequestBody TrialSchemeService.ConfirmCommand body, HttpServletRequest request) {
        return ApiResponse.success(service.confirm(formId, trialId, pointId, body, principal(request)));
    }

    @PostMapping("/{trialId}/control-points/{pointId}/confirm-deviation")
    public ApiResponse<TrialScheme> confirmDeviation(@PathVariable String formId, @PathVariable String trialId, @PathVariable String pointId,
                                                     @RequestBody TrialSchemeService.ConfirmDeviationCommand body, HttpServletRequest request) {
        return ApiResponse.success(service.confirmDeviation(formId, trialId, pointId, body, principal(request)));
    }

    @PostMapping("/{trialId}/submission-preview")
    public ApiResponse<TrialPromotionService.Preview> preview(@PathVariable String formId, @PathVariable String trialId,
                                                             @RequestBody TrialPromotionService.PreviewCommand body, HttpServletRequest request) {
        return ApiResponse.success(promotion.preview(formId, trialId, body, principal(request)));
    }

    @PostMapping("/{trialId}/submit")
    public ApiResponse<ProcessRevision> submit(@PathVariable String formId, @PathVariable String trialId,
                                               @RequestBody TrialPromotionService.SubmitCommand body, HttpServletRequest request) {
        return ApiResponse.success(promotion.submit(formId, trialId, body, principal(request)));
    }

    private SessionPrincipal principal(HttpServletRequest request) {
        var value = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        if (value instanceof SessionPrincipal principal) return principal;
        throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "试验方案必须使用服务端会话身份");
    }

    public record CreateTrialRequest(String name, String purpose, String variables, ProcessPlan plan,
                                     TrialScheme.PlannedData plannedData) {}
    public record SaveTrialRequest(int versionNo, String name, String purpose, String variables, TrialScheme.Conclusion conclusion,
                                   String recommendationReason, BigDecimal qualityScore, String qualityNotes,
                                   TrialScheme.Difficulty difficulty, ProcessPlan plan, TrialScheme.PlannedData plannedData) {}
    public record CopyTrialRequest(int versionNo, String name, boolean includeActuals) {}
    public record ArchiveTrialRequest(int versionNo, boolean archived) {}
}
