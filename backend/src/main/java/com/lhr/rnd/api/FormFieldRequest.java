package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record FormFieldRequest(
        @NotBlank String fieldCode,
        @NotBlank String fieldLabel,
        @NotBlank String controlType,
        boolean required,
        boolean enabled,
        int sortOrder,
        String dictionaryCategory,
        String placeholder,
        String defaultValue,
        String remark
) {
}
