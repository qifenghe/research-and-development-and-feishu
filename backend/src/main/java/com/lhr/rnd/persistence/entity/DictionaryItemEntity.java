package com.lhr.rnd.persistence.entity;

import com.lhr.rnd.model.DictionaryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "dictionary_item")
public class DictionaryItemEntity {
    @Id
    private String id;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "item_label", nullable = false)
    private String itemLabel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "remark")
    private String remark;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DictionaryItemEntity() {
    }

    public DictionaryItemEntity(
            String id,
            String category,
            String itemCode,
            String itemLabel,
            boolean enabled,
            int sortOrder,
            String remark,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.category = category;
        this.itemCode = itemCode;
        this.itemLabel = itemLabel;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.remark = remark;
        this.updatedAt = updatedAt;
    }

    public String getCategory() {
        return category;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public DictionaryItem toModel() {
        return new DictionaryItem(
                id,
                category,
                itemCode,
                itemLabel,
                enabled,
                sortOrder,
                remark,
                updatedAt
        );
    }
}
