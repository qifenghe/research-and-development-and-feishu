package com.lhr.rnd.model;

import java.util.List;

public record PricingFileDetailView(
        PricingFileRecord pricingFile,
        SampleVersion version,
        FinanceNotification financeNotification,
        List<DetailFieldGroup> fieldGroups,
        List<DetailAction> availableActions
) {
}
