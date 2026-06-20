package com.lhr.rnd.model;

import java.util.List;

public record PagedResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages
) {
}
