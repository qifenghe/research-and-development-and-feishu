package com.lhr.rnd.api;

import com.lhr.rnd.service.RolePermissionConfig;
import com.lhr.rnd.service.RolePermissionRule;
import com.lhr.rnd.service.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/role-permissions")
public class RolePermissionController {
    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping("/{roleCode}")
    public ApiResponse<RolePermissionConfig> rolePermissions(@PathVariable String roleCode) {
        return ApiResponse.success(rolePermissionService.rolePermissions(roleCode));
    }

    @PutMapping("/{roleCode}")
    public ApiResponse<RolePermissionConfig> saveRolePermissions(
            @PathVariable String roleCode,
            @Valid @RequestBody SaveRolePermissionsRequest request
    ) {
        var permissions = request.permissions().stream()
                .map(permission -> new RolePermissionRule(
                        null,
                        permission.httpMethod(),
                        permission.pathPattern(),
                        permission.enabled(),
                        permission.description(),
                        permission.sortOrder()
                ))
                .toList();
        return ApiResponse.success(rolePermissionService.replaceRolePermissions(roleCode, permissions));
    }

    @PostMapping("/defaults/initialize")
    public ApiResponse<List<RolePermissionConfig>> initializeDefaultPermissions() {
        return ApiResponse.success(rolePermissionService.initializeDefaultPermissions());
    }
}
