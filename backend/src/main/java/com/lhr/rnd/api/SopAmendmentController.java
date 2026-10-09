package com.lhr.rnd.api;
import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.SopAmendment;
import com.lhr.rnd.service.SessionPrincipal;
import com.lhr.rnd.service.SopAmendmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/sop-amendments")
public class SopAmendmentController {
 private final SopAmendmentService service;
 public SopAmendmentController(SopAmendmentService service){this.service=service;}
 public record Create(@NotBlank String formId,@NotBlank String sourceRevisionId,@NotBlank @Size(max=2000) String reason){}
 public record Save(@NotNull @PositiveOrZero Integer versionNo,@NotBlank @Size(max=2000) String reason,@Size(max=2000) String evidenceNote,@NotNull ProcessPlan snapshot){}
 public record Version(@NotNull @PositiveOrZero Integer versionNo){}
 public record Return(@NotNull @PositiveOrZero Integer versionNo,@NotBlank @Size(max=2000) String comment){}
 public record Approve(@NotNull @PositiveOrZero Integer versionNo,@NotNull Boolean confirmed){}
 @GetMapping public ApiResponse<List<SopAmendment>> list(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) String status,HttpServletRequest req){return ApiResponse.success(service.list(keyword,status,actor(req)));}
 @PostMapping public ApiResponse<SopAmendment> create(@Valid @RequestBody Create c,HttpServletRequest req){return ApiResponse.success(service.create(c.formId(),c.sourceRevisionId(),c.reason(),actor(req)));}
 @GetMapping("/{id}") public ApiResponse<SopAmendment> get(@PathVariable String id,HttpServletRequest req){return ApiResponse.success(service.get(id,actor(req)));}
 @PutMapping("/{id}") public ApiResponse<SopAmendment> save(@PathVariable String id,@Valid @RequestBody Save c,HttpServletRequest req){return ApiResponse.success(service.save(id,c.versionNo(),c.reason(),c.evidenceNote(),c.snapshot(),actor(req)));}
 @PostMapping("/{id}/submit") public ApiResponse<SopAmendment> submit(@PathVariable String id,@Valid @RequestBody Version c,HttpServletRequest req){return ApiResponse.success(service.submit(id,c.versionNo(),actor(req)));}
 @PostMapping("/{id}/cancel") public ApiResponse<SopAmendment> cancel(@PathVariable String id,@Valid @RequestBody Version c,HttpServletRequest req){return ApiResponse.success(service.cancel(id,c.versionNo(),actor(req)));}
 @PostMapping("/{id}/return") public ApiResponse<SopAmendment> returnForChanges(@PathVariable String id,@Valid @RequestBody Return c,HttpServletRequest req){return ApiResponse.success(service.returnForChanges(id,c.versionNo(),c.comment(),actor(req)));}
 @PostMapping("/{id}/approve") public ApiResponse<SopAmendmentService.Publication> approve(@PathVariable String id,@Valid @RequestBody Approve c,HttpServletRequest req){return ApiResponse.success(service.approve(id,c.versionNo(),c.confirmed(),actor(req)));}
 static SessionPrincipal actor(HttpServletRequest req){Object p=req.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE);return p instanceof SessionPrincipal identity?identity:null;}
}
