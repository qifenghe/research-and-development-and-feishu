package com.lhr.rnd.api;

import jakarta.validation.constraints.NotBlank;

public record DictionaryItemRequest(
        @NotBlank String itemCode,
        @NotBlank String itemLabel,
        boolean enabled,
        int sortOrder,
        String remark
) {
}
