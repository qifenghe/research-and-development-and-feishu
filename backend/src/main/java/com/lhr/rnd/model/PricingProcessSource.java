package com.lhr.rnd.model;

import java.math.BigDecimal;

public record PricingProcessSource(
        String source,
        String processRevisionId,
        Integer revisionNo,
        BigDecimal finishedYieldPercent
) {
}
