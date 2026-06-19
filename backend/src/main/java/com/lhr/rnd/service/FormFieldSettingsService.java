package com.lhr.rnd.service;

import com.lhr.rnd.model.FormFieldConfigItem;
import com.lhr.rnd.persistence.entity.FormFieldConfigEntity;
import com.lhr.rnd.persistence.repository.FormFieldConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class FormFieldSettingsService {
    private final FormFieldConfigRepository repository;

    public FormFieldSettingsService(FormFieldConfigRepository repository) {
        this.repository = repository;
    }

    public FormFieldConfig formFields(String formCode) {
        var normalizedFormCode = normalizeFormCode(formCode);
        return new FormFieldConfig(
                normalizedFormCode,
                repository.findByFormCodeOrderBySortOrderAsc(normalizedFormCode).stream()
                        .map(FormFieldConfigEntity::toModel)
                        .toList()
        );
    }

    public List<FormFieldConfig> formFields() {
        var grouped = new LinkedHashMap<String, List<FormFieldConfigItem>>();
        repository.findAllByOrderByFormCodeAscSortOrderAsc().stream()
                .map(FormFieldConfigEntity::toModel)
                .forEach(field -> grouped.computeIfAbsent(field.formCode(), ignored -> new ArrayList<>()).add(field));
        return grouped.entrySet().stream()
                .map(entry -> new FormFieldConfig(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Transactional
    public FormFieldConfig replaceFormFields(String formCode, List<FormFieldRule> fields) {
        var normalizedFormCode = normalizeFormCode(formCode);
        repository.deleteByFormCode(normalizedFormCode);
        return saveFormFields(normalizedFormCode, fields, LocalDateTime.now());
    }

    @Transactional
    public List<FormFieldConfig> initializeDefaultFormFields() {
        return defaultFormFields().stream()
                .map(config -> {
                    if (repository.countByFormCode(config.formCode()) == 0) {
                        var rules = config.fields().stream()
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
                        return saveFormFields(config.formCode(), rules, LocalDateTime.now());
                    }
                    return formFields(config.formCode());
                })
                .toList();
    }

    private FormFieldConfig saveFormFields(String formCode, List<FormFieldRule> fields, LocalDateTime now) {
        var saved = fields.stream()
                .map(field -> repository.save(new FormFieldConfigEntity(
                        "FORM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 23),
                        formCode,
                        field.fieldCode(),
                        field.fieldLabel(),
                        field.controlType().toUpperCase(),
                        field.required(),
                        field.enabled(),
                        field.sortOrder(),
                        normalizeOptionalCode(field.dictionaryCategory()),
                        field.placeholder(),
                        field.defaultValue(),
                        field.remark(),
                        now
                )))
                .map(FormFieldConfigEntity::toModel)
                .sorted(Comparator.comparingInt(FormFieldConfigItem::sortOrder))
                .toList();
        return new FormFieldConfig(formCode, saved);
    }

    private List<FormFieldConfig> defaultFormFields() {
        return List.of(
                config("SAMPLE_REQUEST", List.of(
                        field("sampleNo", "样品编号", "TEXT", true, 10, null, "系统可自动生成或手工录入"),
                        field("productName", "产品名称", "TEXT", true, 20, null, "请输入样品名称"),
                        field("productType", "产品类型", "SELECT", true, 30, "PRODUCT_TYPE", null),
                        field("customerName", "客户名称", "TEXT", false, 40, null, "请输入客户名称"),
                        field("specification", "规格", "TEXT", true, 50, null, "如 500g/袋")
                )),
                config("EXPERIMENT_FORM", List.of(
                        field("versionCode", "样品版本", "TEXT", true, 10, null, "A0/A1/A2/A3"),
                        field("materials", "原辅料/包材", "TABLE", true, 20, null, null),
                        field("processSteps", "工序记录", "TABLE", true, 30, null, null),
                        field("yieldRate", "得率", "NUMBER", false, 40, null, "自动或手工填写"),
                        field("attachments", "现场附件", "UPLOAD", false, 50, null, null)
                )),
                config("TEST_RECORD", List.of(
                        field("testerName", "测试人员", "USER_SELECT", true, 10, null, null),
                        field("appearance", "外观", "TEXTAREA", false, 20, null, "记录色泽、形态、完整度"),
                        field("taste", "口味口感", "TEXTAREA", true, 30, null, "记录咸淡、香气、口感"),
                        field("decision", "测试结论", "SELECT", true, 40, "TEST_DECISION", null)
                )),
                config("SHIPMENT_FEEDBACK", List.of(
                        field("shipmentQuantity", "寄样数量", "NUMBER", true, 10, null, null),
                        field("expressNo", "快递单号", "TEXT", false, 20, null, null),
                        field("feedbackResult", "反馈结果", "SELECT", true, 30, "CUSTOMER_FEEDBACK_RESULT", null),
                        field("feedbackRemark", "反馈说明", "TEXTAREA", false, 40, null, null)
                )),
                config("PRICING_FILE", List.of(
                        field("pricingVersion", "核价版本", "TEXT", true, 10, null, "如 A0-核价V1"),
                        field("templateCode", "核价模板", "SELECT", true, 20, "PRICING_TEMPLATE", null),
                        field("referenceYieldKg", "研发参考出成kg", "NUMBER", true, 30, null, null),
                        field("remark", "核价备注", "TEXTAREA", false, 40, null, null)
                ))
        );
    }

    private FormFieldConfig config(String formCode, List<FormFieldRule> fields) {
        var items = fields.stream()
                .map(field -> new FormFieldConfigItem(
                        null,
                        formCode,
                        field.fieldCode(),
                        field.fieldLabel(),
                        field.controlType(),
                        field.required(),
                        field.enabled(),
                        field.sortOrder(),
                        field.dictionaryCategory(),
                        field.placeholder(),
                        field.defaultValue(),
                        field.remark(),
                        null
                ))
                .toList();
        return new FormFieldConfig(formCode, items);
    }

    private FormFieldRule field(
            String fieldCode,
            String fieldLabel,
            String controlType,
            boolean required,
            int sortOrder,
            String dictionaryCategory,
            String placeholder
    ) {
        return new FormFieldRule(
                fieldCode,
                fieldLabel,
                controlType,
                required,
                true,
                sortOrder,
                dictionaryCategory,
                placeholder,
                null,
                null
        );
    }

    private String normalizeFormCode(String formCode) {
        return formCode == null ? "" : formCode.trim().toUpperCase();
    }

    private String normalizeOptionalCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }
}
