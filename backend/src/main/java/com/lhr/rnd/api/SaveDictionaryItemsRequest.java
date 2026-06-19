package com.lhr.rnd.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveDictionaryItemsRequest(
        @NotNull List<@Valid DictionaryItemRequest> items
) {
}
