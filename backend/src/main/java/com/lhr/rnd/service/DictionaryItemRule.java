package com.lhr.rnd.service;

public record DictionaryItemRule(
        String itemCode,
        String itemLabel,
        boolean enabled,
        int sortOrder,
        String remark
) {
}
