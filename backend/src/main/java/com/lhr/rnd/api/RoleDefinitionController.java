package com.lhr.rnd.api;

import com.lhr.rnd.model.RoleDefinition;
import com.lhr.rnd.service.RoleDefinitionService;
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
@RequestMapping("/api/v1/settings/roles")
public class RoleDefinitionController {
    private final RoleDefinitionService roleDefinitionService;

    public RoleDefinitionController(RoleDefinitionService roleDefinitionService) {
        this.roleDefinitionService = roleDefinitionService;
    }

    @GetMapping
    public ApiResponse<List<RoleDefinition>> roles() {
        return ApiResponse.success(roleDefinitionService.roles());
    }

    @PostMapping
    public ApiResponse<RoleDefinition> create(@Valid @RequestBody SaveRoleDefinitionRequest request) {
        return ApiResponse.success(roleDefinitionService.create(request.roleCode(), request.roleName(), request.description()));
    }

    @PutMapping("/{roleCode}")
    public ApiResponse<RoleDefinition> update(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRoleDefinitionRequest request
    ) {
        return ApiResponse.success(roleDefinitionService.update(roleCode, request.roleName(), request.description()));
    }

    @PostMapping("/{roleCode}/enable")
    public ApiResponse<RoleDefinition> enable(@PathVariable String roleCode) {
        return ApiResponse.success(roleDefinitionService.enable(roleCode));
    }

    @PostMapping("/{roleCode}/disable")
    public ApiResponse<RoleDefinition> disable(@PathVariable String roleCode) {
        return ApiResponse.success(roleDefinitionService.disable(roleCode));
    }
}
