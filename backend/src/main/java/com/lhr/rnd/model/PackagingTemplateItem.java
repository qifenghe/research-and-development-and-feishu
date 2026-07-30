package com.lhr.rnd.model;

import java.math.BigDecimal;

public record PackagingTemplateItem(
        String templateCode,
        int sequence,
        String materialCode,
        String materialName,
        String conversionType,
        BigDecimal unitsPerParent,
        String packageSpec,
        String remark
) {
}
