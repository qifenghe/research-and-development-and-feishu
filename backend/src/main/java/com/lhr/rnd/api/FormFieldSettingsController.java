package com.lhr.rnd.api;

import com.lhr.rnd.service.FormFieldConfig;
import com.lhr.rnd.service.FormFieldRule;
import com.lhr.rnd.service.FormFieldSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/form-fields")
public class FormFieldSettingsController {
    private final FormFieldSettingsService formFieldSettingsService;

    public FormFieldSettingsController(FormFieldSettingsService formFieldSettingsService) {
        this.formFieldSettingsService = formFieldSettingsService;
    }

    @GetMapping
    public ApiResponse<List<FormFieldConfig>> formFields() {
        return ApiResponse.success(formFieldSettingsService.formFields());
    }

    @GetMapping("/{formCode}")
    public ApiResponse<FormFieldConfig> formFields(@PathVariable String formCode) {
        return ApiResponse.success(formFieldSettingsService.formFields(formCode));
    }

    @PutMapping("/{formCode}")
    public ApiResponse<FormFieldConfig> saveFormFields(
            @PathVariable String formCode,
            @Valid @RequestBody SaveFormFieldsRequest request
    ) {
        var fields = request.fields().stream()
                .map(field -> new FormFieldRule(
                        field.fieldCode(),
                        field.fieldLabel(),
                        field.controlType(),
                        field.required(),
                        field.enabled(),
                        field.sortOrder(),
                        field.dictionaryCategory(),
                        field.placeholder(),
                        field.defaultValue(),
                        field.remark()
                ))
                .toList();
        return ApiResponse.success(formFieldSettingsService.replaceFormFields(formCode, fields));
    }

    @PostMapping("/defaults/initialize")
    public ApiResponse<List<FormFieldConfig>> initializeDefaultFormFields() {
        return ApiResponse.success(formFieldSettingsService.initializeDefaultFormFields());
    }
}
