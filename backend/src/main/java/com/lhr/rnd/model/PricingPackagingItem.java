package com.lhr.rnd.model;

import java.math.BigDecimal;

public record PricingPackagingItem(
        String id,
        String pricingFileId,
        int sequence,
        PricingPackagingSource source,
        String materialCode,
        String materialName,
        BigDecimal quantity,
        String packageSpec,
        String conversionRule,
        String remark,
        PricingPackagingStatus confirmationStatus,
        String modificationReason,
        String quantityUnit
) {
    public PricingPackagingItem(String id, String pricingFileId, int sequence, PricingPackagingSource source,
            String materialCode, String materialName, BigDecimal quantity, String packageSpec,
            String conversionRule, String remark, PricingPackagingStatus confirmationStatus, String modificationReason) {
        this(id, pricingFileId, sequence, source, materialCode, materialName, quantity, packageSpec,
                conversionRule, remark, confirmationStatus, modificationReason, null);
    }
}
