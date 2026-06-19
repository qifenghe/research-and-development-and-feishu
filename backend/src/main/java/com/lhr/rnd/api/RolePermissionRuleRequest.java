package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record RolePermissionRuleRequest(
        @NotBlank String httpMethod,
        @NotBlank String pathPattern,
        boolean enabled,
        String description,
        int sortOrder
) {
}
