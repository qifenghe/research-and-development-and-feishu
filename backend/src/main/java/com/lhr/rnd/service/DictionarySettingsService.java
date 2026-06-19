package com.lhr.rnd.service;

import com.lhr.rnd.model.DictionaryItem;
import com.lhr.rnd.persistence.entity.DictionaryItemEntity;
import com.lhr.rnd.persistence.repository.DictionaryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class DictionarySettingsService {
    private final DictionaryItemRepository repository;

    public DictionarySettingsService(DictionaryItemRepository repository) {
        this.repository = repository;
    }

    public DictionaryConfig dictionary(String category) {
        return new DictionaryConfig(
                normalizeCategory(category),
                repository.findByCategoryOrderBySortOrderAsc(normalizeCategory(category)).stream()
                        .map(DictionaryItemEntity::toModel)
                        .toList()
        );
    }

    public List<DictionaryConfig> dictionaries() {
        var grouped = new LinkedHashMap<String, List<DictionaryItem>>();
        repository.findAllByOrderByCategoryAscSortOrderAsc().stream()
                .map(DictionaryItemEntity::toModel)
                .forEach(item -> grouped.computeIfAbsent(item.category(), ignored -> new ArrayList<>()).add(item));
        return grouped.entrySet().stream()
                .map(entry -> new DictionaryConfig(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Transactional
    public DictionaryConfig replaceDictionary(String category, List<DictionaryItemRule> items) {
        var normalizedCategory = normalizeCategory(category);
        repository.deleteByCategory(normalizedCategory);
        return saveDictionary(normalizedCategory, items, LocalDateTime.now());
    }

    @Transactional
    public List<DictionaryConfig> initializeDefaultDictionaries() {
        return defaultDictionaries().stream()
                .map(config -> {
                    if (repository.countByCategory(config.category()) == 0) {
                        var rules = config.items().stream()
                                .map(item -> new DictionaryItemRule(
                                        item.itemCode(),
                                        item.itemLabel(),
                                        item.enabled(),
                                        item.sortOrder(),
                                        item.remark()
                                ))
                                .toList();
                        return saveDictionary(config.category(), rules, LocalDateTime.now());
                    }
                    return dictionary(config.category());
                })
                .toList();
    }

    private DictionaryConfig saveDictionary(String category, List<DictionaryItemRule> items, LocalDateTime now) {
        var saved = items.stream()
                .map(item -> repository.save(new DictionaryItemEntity(
                        "DICT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 23),
                        category,
                        item.itemCode(),
                        item.itemLabel(),
                        item.enabled(),
                        item.sortOrder(),
                        item.remark(),
                        now
                )))
                .map(DictionaryItemEntity::toModel)
                .sorted(Comparator.comparingInt(DictionaryItem::sortOrder))
                .toList();
        return new DictionaryConfig(category, saved);
    }

    private List<DictionaryConfig> defaultDictionaries() {
        return List.of(
                defaultConfig("PRODUCT_TYPE", List.of(
                        item("FROZEN_READY_MEAL", "冷冻即热菜", 10),
                        item("CURED_PRODUCT", "腊制品", 20),
                        item("RAW_PREPARED", "生制调理品", 30)
                )),
                defaultConfig("UNIT", List.of(
                        item("KG", "kg", 10),
                        item("G", "g", 20),
                        item("BAG", "袋", 30),
                        item("BOX", "箱", 40)
                )),
                defaultConfig("MATERIAL_CATEGORY", List.of(
                        item("RAW", "原料", 10),
                        item("AUXILIARY", "辅料", 20),
                        item("PACKAGE", "包材", 30)
                )),
                defaultConfig("SAMPLE_STATUS", List.of(
                        item("PENDING_REVIEW", "待审核", 10),
                        item("SAMPLING", "打样中", 20),
                        item("INTERNAL_TESTING", "内部测试", 30),
                        item("SAMPLE_COMPLETED", "样品完成", 40),
                        item("PRICING_GENERATED", "已生成核价", 50)
                )),
                defaultConfig("ROLE", List.of(
                        item("RND_ASSISTANT", "研发内勤", 10),
                        item("RND_DIRECTOR", "研发总监", 20),
                        item("RND_ENGINEER", "研发人员", 30),
                        item("TESTER", "测试人员", 40),
                        item("FINANCE", "财务", 50),
                        item("MANAGER", "管理层", 60),
                        item("SYSTEM_ADMIN", "系统管理员", 70)
                ))
        );
    }

    private DictionaryConfig defaultConfig(String category, List<DictionaryItemRule> rules) {
        var items = rules.stream()
                .map(rule -> new DictionaryItem(
                        null,
                        category,
                        rule.itemCode(),
                        rule.itemLabel(),
                        rule.enabled(),
                        rule.sortOrder(),
                        rule.remark(),
                        null
                ))
                .toList();
        return new DictionaryConfig(category, items);
    }

    private DictionaryItemRule item(String itemCode, String itemLabel, int sortOrder) {
        return new DictionaryItemRule(itemCode, itemLabel, true, sortOrder, null);
    }

    private String normalizeCategory(String category) {
        return category == null ? "" : category.trim().toUpperCase();
    }
}
