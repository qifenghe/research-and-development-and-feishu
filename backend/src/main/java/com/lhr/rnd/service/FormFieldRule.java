package com.lhr.rnd.service;

public record FormFieldRule(
        String fieldCode,
        String fieldLabel,
        String controlType,
        boolean required,
        boolean enabled,
        int sortOrder,
        String dictionaryCategory,
        String placeholder,
        String defaultValue,
        String remark
) {
}
