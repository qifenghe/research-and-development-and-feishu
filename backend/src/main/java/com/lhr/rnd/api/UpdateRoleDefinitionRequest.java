package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleDefinitionRequest(
        @NotBlank String roleName,
        String description
) {
}
