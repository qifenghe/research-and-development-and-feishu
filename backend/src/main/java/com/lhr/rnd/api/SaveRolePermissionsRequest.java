package com.lhr.rnd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveRolePermissionsRequest(
        @NotNull List<@Valid RolePermissionRuleRequest> permissions
) {
}
