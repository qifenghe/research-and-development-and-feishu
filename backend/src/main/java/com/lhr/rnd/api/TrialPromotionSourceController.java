package com.lhr.rnd.api;

import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.TrialPromotionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Formal source metadata must not be routed through the trial draft read policy. */
@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}/process-plan/revisions/{revisionId}")
public class TrialPromotionSourceController {
    private final TrialPromotionService promotion;
    public TrialPromotionSourceController(TrialPromotionService promotion) { this.promotion = promotion; }

    @GetMapping("/source")
    public ApiResponse<TrialPromotionService.SourceMetadata> source(@PathVariable String formId, @PathVariable String revisionId,
                                                                   HttpServletRequest request) {
        return ApiResponse.success(promotion.source(formId, revisionId, principal(request)));
    }

    @GetMapping("/displaced-draft")
    public ApiResponse<ProcessPlan> displacedDraft(@PathVariable String formId, @PathVariable String revisionId,
                                                 HttpServletRequest request) {
        return ApiResponse.success(promotion.displacedDraft(formId, revisionId, principal(request)));
    }

    private SessionPrincipal principal(HttpServletRequest request) {
        var value = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        if (value instanceof SessionPrincipal principal) return principal;
        throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "必须使用服务端会话身份");
    }
}
