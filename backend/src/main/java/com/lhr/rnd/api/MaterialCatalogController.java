package com.lhr.rnd.api;
import com.lhr.rnd.service.*;
import com.lhr.rnd.model.MaterialCatalogItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/materials")
public class MaterialCatalogController {
 private final MaterialCatalogService service;
 private final RolePermissionService permissions;
 public MaterialCatalogController(MaterialCatalogService service,RolePermissionService permissions){this.service=service;this.permissions=permissions;}
 @GetMapping public ApiResponse<MaterialCatalogService.Page> list(@RequestParam(required=false) String query,@RequestParam(required=false) String category,@RequestParam(defaultValue="ACTIVE") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String classificationId,@RequestParam(defaultValue="false") boolean ingredientsOnly,HttpServletRequest r){
  var result=service.list(query,category,status,page,size,classificationId,ingredientsOnly);
  var role=r.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE) instanceof SessionPrincipal p?p.role():null;
  var access=new MaterialCatalogService.Access(permissions.hasPermission(role,"POST","/api/v1/materials"),permissions.hasPermission(role,"PUT","/api/v1/materials/example"),permissions.hasPermission(role,"POST","/api/v1/materials/example/status"),"SYSTEM_ADMIN".equals(role));
  return ApiResponse.success(new MaterialCatalogService.Page(result.items(),result.total(),result.page(),result.size(),access));
 }
 @PostMapping public ApiResponse<MaterialCatalogItem> create(@RequestBody MaterialCatalogService.Write w,HttpServletRequest r){return ApiResponse.success(service.create(w,principal(r)));}
 @PutMapping("/{id}") public ApiResponse<MaterialCatalogItem> update(@PathVariable String id,@RequestBody MaterialCatalogService.Write w,HttpServletRequest r){return ApiResponse.success(service.update(id,w,principal(r)));}
 public record Status(String status,Integer expectedVersion){}
 @PostMapping("/{id}/status") public ApiResponse<MaterialCatalogItem> status(@PathVariable String id,@RequestBody Status w,HttpServletRequest r){return ApiResponse.success(service.status(id,w.status(),w.expectedVersion(),principal(r)));}
 private SessionPrincipal principal(HttpServletRequest r){if(r.getAttribute(SessionAuthenticationInterceptor.SESSION_PRINCIPAL_ATTRIBUTE) instanceof SessionPrincipal p)return p;throw new BusinessException("SESSION_PRINCIPAL_REQUIRED","必须使用服务端会话身份");}
}
