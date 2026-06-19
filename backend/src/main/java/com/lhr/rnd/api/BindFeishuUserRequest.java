package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record BindFeishuUserRequest(
        @NotBlank String name,
        @NotBlank String feishuUserId,
        @NotBlank String role,
        String departmentName
) {
}
