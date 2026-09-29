package com.lhr.rnd.api;
import com.lhr.rnd.model.MaterialClassification;
import com.lhr.rnd.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/material-classifications")
public class MaterialClassificationController {
 private final MaterialClassificationService service;
 public MaterialClassificationController(MaterialClassificationService service){this.service=service;}
 @GetMapping public ApiResponse<List<MaterialClassification>> list(){return ApiResponse.success(service.list());}
 @PostMapping public ApiResponse<MaterialClassification> create(@RequestBody MaterialClassificationService.Write w,HttpServletRequest r){return ApiResponse.success(service.create(w,principal(r)));}
 @PutMapping("/{id}") public ApiResponse<MaterialClassification> update(@PathVariable String id,@RequestBody MaterialClassificationService.Write w,HttpServletRequest r){return ApiResponse.success(service.update(id,w,principal(r)));}
 public record Status(String status,Integer expectedVersion){}
 @PostMapping("/{id}/status") public ApiResponse<MaterialClassification> status(@PathVariable String id,@RequestBody Status w,HttpServletRequest r){return ApiResponse.success(service.status(id,w.status(),w.expectedVersion(),principal(r)));}
 private SessionPrincipal principal(HttpServletRequest r){if(r.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE) instanceof SessionPrincipal p)return p;throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","必须使用会话身份");}
}
