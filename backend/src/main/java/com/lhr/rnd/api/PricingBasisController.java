package com.lhr.rnd.api;

import com.lhr.rnd.model.PricingBasisDraft;
import com.lhr.rnd.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing-files/{id}/basis")
public class PricingBasisController {
    private final SampleWorkflowService workflow;
    public PricingBasisController(SampleWorkflowService workflow) { this.workflow=workflow; }
    public record SaveRequest(int expectedVersion,PricingBasisDraft draft) {}
    public record ConfirmRequest(int expectedVersion) {}
    @GetMapping public ApiResponse<PricingBasisService.View> load(@PathVariable String id,HttpServletRequest request) {
        return ApiResponse.success(workflow.pricingBasis(id,principal(request)));
    }
    @PutMapping public ApiResponse<PricingBasisService.View> save(@PathVariable String id,@RequestBody SaveRequest body,HttpServletRequest request) {
        return ApiResponse.success(workflow.savePricingBasis(id,body.expectedVersion(),body.draft(),principal(request)));
    }
    @PostMapping("/confirm") public ApiResponse<PricingBasisService.View> confirm(@PathVariable String id,@RequestBody ConfirmRequest body,HttpServletRequest request) {
        return ApiResponse.success(workflow.confirmPricingBasis(id,body.expectedVersion(),principal(request)));
    }
    @PostMapping("/preview") public org.springframework.http.ResponseEntity<byte[]> preview(@PathVariable String id,@RequestBody ConfirmRequest body,HttpServletRequest request) {
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition","attachment; filename=pricing-preview.xlsx").body(workflow.previewPricingBasis(id,body.expectedVersion(),principal(request)));
    }
    @PostMapping("/generate") public ApiResponse<com.lhr.rnd.model.PricingFileRecord> generate(@PathVariable String id,@RequestBody ConfirmRequest body,HttpServletRequest request) {
        return ApiResponse.success(workflow.generateConfirmedPricingBasis(id,body.expectedVersion(),principal(request)));
    }
    private SessionPrincipal principal(HttpServletRequest r) {
        if(r.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE) instanceof SessionPrincipal p) return p;
        throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","必须使用服务端会话身份");
    }
}
