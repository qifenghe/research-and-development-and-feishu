package com.lhr.rnd.service;

import com.lhr.rnd.model.DictionaryItem;

import java.util.List;

public record DictionaryConfig(
        String category,
        List<DictionaryItem> items
) {
}
