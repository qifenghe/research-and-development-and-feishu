package com.lhr.rnd.api;

import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.SopCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sops")
public class SopCatalogController {
    private final SopCatalogService service;
    private final com.lhr.rnd.service.SopAccessService access;
    private final com.lhr.rnd.service.ProcessArtifactService artifacts;
    public SopCatalogController(SopCatalogService service,com.lhr.rnd.service.SopAccessService access,com.lhr.rnd.service.ProcessArtifactService artifacts) { this.service = service;this.access=access;this.artifacts=artifacts; }

    @GetMapping
    public ApiResponse<SopCatalogService.Page> list(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        var identity = request.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);
        return ApiResponse.success(service.list(keyword, page, size, identity instanceof SessionPrincipal p ? p : null));
    }
    @GetMapping("/{formId}/revisions/{revisionId}")
    public ApiResponse<?> revision(@PathVariable String formId,@PathVariable String revisionId,HttpServletRequest req){return ApiResponse.success(access.require(formId,revisionId,"GET","",SopAmendmentController.actor(req)));}
    @GetMapping("/{formId}/revisions/{revisionId}/artifacts")
    public ApiResponse<?> artifacts(@PathVariable String formId,@PathVariable String revisionId,HttpServletRequest req){return ApiResponse.success(artifacts.listForSop(formId,revisionId,SopAmendmentController.actor(req)));}
    @PostMapping("/{formId}/revisions/{revisionId}/artifacts")
    public ApiResponse<?> generate(@PathVariable String formId,@PathVariable String revisionId,@jakarta.validation.Valid @RequestBody GenerateRequest body,HttpServletRequest req){return ApiResponse.success(artifacts.generateForSop(formId,revisionId,body.artifactType(),SopAmendmentController.actor(req)));}
    public record GenerateRequest(@jakarta.validation.constraints.NotBlank String artifactType) {}
    @GetMapping("/{formId}/revisions/{revisionId}/artifacts/{artifactId}/download")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String formId,@PathVariable String revisionId,@PathVariable String artifactId,HttpServletRequest req){
        var file=artifacts.downloadForSop(formId,revisionId,artifactId,SopAmendmentController.actor(req));
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition",org.springframework.http.ContentDisposition.attachment().filename(file.fileName(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(org.springframework.http.MediaType.parseMediaType(file.contentType())).body(file.content());
    }
}
