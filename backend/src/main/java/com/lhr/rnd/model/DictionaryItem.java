package com.lhr.rnd.model;

import java.time.LocalDateTime;

public record DictionaryItem(
        String id,
        String category,
        String itemCode,
        String itemLabel,
        boolean enabled,
        int sortOrder,
        String remark,
        LocalDateTime updatedAt
) {
}
