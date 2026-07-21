package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record SaveUserSettingsRequest(
        String username,
        @NotBlank String name,
        String feishuUserId,
        @NotBlank String role,
        String departmentName,
        String password
) {
}
