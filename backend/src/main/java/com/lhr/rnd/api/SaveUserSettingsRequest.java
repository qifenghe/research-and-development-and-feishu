package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record SaveUserSettingsRequest(
        @NotBlank String name,
        @NotBlank String role,
        String departmentName
) {
}
