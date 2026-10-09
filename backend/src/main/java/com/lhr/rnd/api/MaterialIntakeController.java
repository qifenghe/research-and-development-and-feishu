package com.lhr.rnd.api;
import com.lhr.rnd.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/material-intakes")
public class MaterialIntakeController {
 private final MaterialIntakeService service;public MaterialIntakeController(MaterialIntakeService s){service=s;}
 private SessionPrincipal actor(HttpServletRequest r){if(r.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE) instanceof SessionPrincipal p)return p;throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","请登录后操作");}
 public record Version(Integer expectedVersion,String comment){}
 @GetMapping public ApiResponse<List<MaterialIntakeService.Request>> list(@RequestParam(required=false)String query,@RequestParam(required=false)String status,HttpServletRequest r){return ApiResponse.success(service.list(query,status,actor(r)));}
 @GetMapping("/{id}") public ApiResponse<MaterialIntakeService.Request> find(@PathVariable String id,HttpServletRequest r){return ApiResponse.success(service.find(id,actor(r)));}
 @PostMapping public ApiResponse<MaterialIntakeService.Request> create(@RequestBody MaterialIntakeService.Write w,HttpServletRequest r){return ApiResponse.success(service.create(w,actor(r)));}
 @PutMapping("/{id}") public ApiResponse<MaterialIntakeService.Request> update(@PathVariable String id,@RequestBody MaterialIntakeService.Write w,HttpServletRequest r){return ApiResponse.success(service.update(id,w,actor(r)));}
 @PostMapping("/{id}/submit") public ApiResponse<MaterialIntakeService.Request> submit(@PathVariable String id,@RequestBody Version v,HttpServletRequest r){return ApiResponse.success(service.submit(id,v.expectedVersion(),actor(r)));}
 @PostMapping("/{id}/approve") public ApiResponse<MaterialIntakeService.Request> approve(@PathVariable String id,@RequestBody MaterialIntakeService.Review v,HttpServletRequest r){return ApiResponse.success(service.approve(id,v,actor(r)));}
 @PostMapping("/{id}/return") public ApiResponse<MaterialIntakeService.Request> reject(@PathVariable String id,@RequestBody Version v,HttpServletRequest r){return ApiResponse.success(service.reject(id,v.expectedVersion(),v.comment(),actor(r)));}
}
