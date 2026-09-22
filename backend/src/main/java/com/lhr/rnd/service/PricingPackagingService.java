package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.PackagingTemplateItem;
import com.lhr.rnd.model.PricingPackagingItem;
import com.lhr.rnd.model.PricingPackagingSource;
import com.lhr.rnd.model.PricingPackagingStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PricingPackagingService {
    public List<PricingPackagingItem> createSuggestedItems(
            String pricingFileId,
            String productName,
            BigDecimal referenceOutputKg,
            BigDecimal unitWeightKg,
            int bagsPerBox,
            List<PackagingTemplateItem> templateItems
    ) {
        var packageCount = resolvePackageCount(referenceOutputKg, unitWeightKg);
        var boxCount = resolveBoxCount(packageCount, bagsPerBox);
        var items = new ArrayList<PricingPackagingItem>();
        var nextSequence = 10;

        for (var template : templateItems) {
            var quantity = quantityForTemplateItem(template, packageCount, boxCount);
            items.add(new PricingPackagingItem(
                    nextId(), pricingFileId, template.sequence(), PricingPackagingSource.TEMPLATE,
                    blankToNull(template.materialCode()), template.materialName(), quantity, template.packageSpec(),
                    conversionRule(template), template.remark(), PricingPackagingStatus.PENDING_CONFIRMATION, null, template.quantityUnit()
            ));
            nextSequence = Math.max(nextSequence, template.sequence() + 10);
        }

        items.add(systemLabel(pricingFileId, nextSequence, productName + "内袋标签", packageCount, "1 张/袋"));
        items.add(systemLabel(pricingFileId, nextSequence + 10, productName + "外箱标签", boxCount, "1 张/箱"));
        return items;
    }

    public void validateForSubmission(List<PricingPackagingItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("PRICING_PACKAGING_EMPTY", "请至少确认一条包装物料");
        }
        for (var item : items) {
            if (item.materialName() == null || item.materialName().isBlank()) {
                throw new BusinessException("PRICING_PACKAGING_NAME_REQUIRED", "包装物料名称不能为空");
            }
            if (item.quantity() == null || item.quantity().signum() < 0) {
                throw new BusinessException("PRICING_PACKAGING_QUANTITY_INVALID", "包装物料数量必须为零或正数");
            }
            if (item.confirmationStatus() != PricingPackagingStatus.CONFIRMED) {
                throw new BusinessException("PRICING_PACKAGING_PENDING", "包装物料尚未确认");
            }
        }
    }

    private PricingPackagingItem systemLabel(
            String pricingFileId,
            int sequence,
            String name,
            BigDecimal quantity,
            String packageSpec
    ) {
        return new PricingPackagingItem(
                nextId(), pricingFileId, sequence, PricingPackagingSource.SYSTEM_LABEL,
                null, name, quantity, packageSpec, packageSpec, null,
                PricingPackagingStatus.PENDING_CONFIRMATION, null, "张"
        );
    }

    private BigDecimal resolvePackageCount(BigDecimal referenceOutputKg, BigDecimal unitWeightKg) {
        if (referenceOutputKg == null || unitWeightKg == null || unitWeightKg.signum() <= 0) {
            return null;
        }
        return referenceOutputKg.divide(unitWeightKg, 0, RoundingMode.DOWN);
    }

    private BigDecimal resolveBoxCount(BigDecimal packageCount, int bagsPerBox) {
        if (packageCount == null || bagsPerBox <= 0) {
            return null;
        }
        return packageCount.divide(BigDecimal.valueOf(bagsPerBox), 0, RoundingMode.CEILING);
    }

    private BigDecimal quantityForTemplateItem(
            PackagingTemplateItem item,
            BigDecimal packageCount,
            BigDecimal boxCount
    ) {
        var parentCount = switch (item.conversionType()) {
            case "PER_BAG" -> packageCount;
            case "PER_BOX", "PER_METER" -> boxCount;
            default -> null;
        };
        return parentCount == null ? null : parentCount.multiply(item.unitsPerParent());
    }

    private String conversionRule(PackagingTemplateItem item) {
        return switch (item.conversionType()) {
            case "PER_BAG" -> "%s / 袋".formatted(item.unitsPerParent().stripTrailingZeros().toPlainString());
            case "PER_BOX" -> "%s / 箱".formatted(item.unitsPerParent().stripTrailingZeros().toPlainString());
            case "PER_METER" -> "%s 米 / 箱".formatted(item.unitsPerParent().stripTrailingZeros().toPlainString());
            default -> item.conversionType();
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nextId() {
        return "PKG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
    }
}
