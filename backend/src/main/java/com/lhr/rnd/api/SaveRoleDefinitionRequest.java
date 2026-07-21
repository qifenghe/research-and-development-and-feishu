package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaveRoleDefinitionRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,63}", message = "角色编码只能使用字母、数字和下划线，并以字母开头")
        String roleCode,
        @NotBlank String roleName,
        String description
) {
}
