package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.FormFieldConfigItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "form_field_config")
public class FormFieldConfigEntity {
    @Id
    private String id;

    @Column(name = "form_code", nullable = false)
    private String formCode;

    @Column(name = "field_code", nullable = false)
    private String fieldCode;

    @Column(name = "field_label", nullable = false)
    private String fieldLabel;

    @Column(name = "control_type", nullable = false)
    private String controlType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "dictionary_category")
    private String dictionaryCategory;

    @Column(name = "placeholder")
    private String placeholder;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "remark")
    private String remark;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FormFieldConfigEntity() {
    }

    public FormFieldConfigEntity(
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
        this.id = id;
        this.formCode = formCode;
        this.fieldCode = fieldCode;
        this.fieldLabel = fieldLabel;
        this.controlType = controlType;
        this.required = required;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.dictionaryCategory = dictionaryCategory;
        this.placeholder = placeholder;
        this.defaultValue = defaultValue;
        this.remark = remark;
        this.updatedAt = updatedAt;
    }

    public FormFieldConfigItem toModel() {
        return new FormFieldConfigItem(
                id,
                formCode,
                fieldCode,
                fieldLabel,
                controlType,
                required,
                enabled,
                sortOrder,
                dictionaryCategory,
                placeholder,
                defaultValue,
                remark,
                updatedAt
        );
    }
}
