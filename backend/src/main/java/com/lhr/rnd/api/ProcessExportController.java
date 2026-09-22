package com.lhr.rnd.api;

import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.service.ProcessArtifactService;
import com.lhr.rnd.service.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/experiment-forms/{formId}")
public class ProcessExportController {
    private final ProcessArtifactService service;
    public ProcessExportController(ProcessArtifactService service) { this.service = service; }

    @GetMapping("/process-plan/revisions/{revisionId}/export-check")
    public ApiResponse<ProcessExportView.Check> revisionCheck(@PathVariable String formId, @PathVariable String revisionId,
            @RequestParam String artifactType, HttpServletRequest request) {
        return ApiResponse.success(service.check(service.revisionView(formId, revisionId, principal(request)), artifactType));
    }
    @GetMapping("/process-plan/export-check")
    public ApiResponse<ProcessExportView.Check> draftCheck(@PathVariable String formId, @RequestParam int versionNo,
            @RequestParam String artifactType, HttpServletRequest request) {
        return ApiResponse.success(service.check(service.draftView(formId, versionNo, principal(request)), artifactType));
    }
    @GetMapping("/trials/{trialId}/export-check")
    public ApiResponse<ProcessExportView.Check> trialCheck(@PathVariable String formId, @PathVariable String trialId,
            @RequestParam int versionNo, @RequestParam String artifactType, HttpServletRequest request) {
        return ApiResponse.success(service.check(service.trialView(formId, trialId, versionNo, principal(request)), artifactType));
    }
    @GetMapping("/process-plan/export-preview")
    public ResponseEntity<byte[]> draftPreview(@PathVariable String formId, @RequestParam int versionNo,
            @RequestParam String artifactType, HttpServletRequest request) {
        return download(service.preview(service.draftView(formId, versionNo, principal(request)), artifactType));
    }
    @GetMapping("/trials/{trialId}/export-preview")
    public ResponseEntity<byte[]> trialPreview(@PathVariable String formId, @PathVariable String trialId,
            @RequestParam int versionNo, @RequestParam String artifactType, HttpServletRequest request) {
        return download(service.preview(service.trialView(formId, trialId, versionNo, principal(request)), artifactType));
    }
    private ResponseEntity<byte[]> download(ProcessArtifactService.ArtifactDownload file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentLength(file.content().length).body(file.content());
    }
    private SessionPrincipal principal(HttpServletRequest request) {
        var value = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal principal)) throw new BusinessException("SESSION_PRINCIPAL_REQUIRED", "必须使用服务端会话身份");
        return principal;
    }
}
