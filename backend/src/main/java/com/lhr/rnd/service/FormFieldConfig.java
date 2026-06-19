package com.lhr.rnd.service;

import com.lhr.rnd.model.FormFieldConfigItem;

import java.util.List;

public record FormFieldConfig(
        String formCode,
        List<FormFieldConfigItem> fields
) {
}
