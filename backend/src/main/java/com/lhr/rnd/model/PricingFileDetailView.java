package com.lhr.rnd.model;

import java.util.List;

public record PricingFileDetailView(
        PricingFileRecord pricingFile,
        SampleVersion version,
        PricingProcessSource source,
        FinanceNotification financeNotification,
        List<PricingPackagingItem> packagingItems,
        List<DetailFieldGroup> fieldGroups,
        List<DetailAction> availableActions
) {
}
