package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record FormFieldConfigItem(
        String id,
        String formCode,
        String fieldCode,
        String fieldLabel,
        String controlType,
        boolean required,
        boolean enabled,
        int sortOrder,
        String dictionaryCategory,
        String placeholder,
        String defaultValue,
        String remark,
        LocalDateTime updatedAt
) {
}
