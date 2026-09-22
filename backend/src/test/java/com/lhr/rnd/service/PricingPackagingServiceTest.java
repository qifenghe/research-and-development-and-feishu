package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.PackagingTemplateItem;
import com.lhr.rnd.model.PricingPackagingItem;
import com.lhr.rnd.model.PricingPackagingSource;
import com.lhr.rnd.model.PricingPackagingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingPackagingServiceTest {
    private final PricingPackagingService service = new PricingPackagingService();

    @Test
    void createsUncodedProductLabelsAndTemplatePackagingQuantities() {
        var items = service.createSuggestedItems(
                "PRICE-001",
                "1kg吮指五香味酱汁-MY",
                new BigDecimal("270"),
                new BigDecimal("1"),
                10,
                List.of(
                        template("FBZ0231", "空白袋", "PER_BAG", "1"),
                        template("FBZ0003", "1kg空白箱", "PER_BOX", "1")
                )
        );

        assertThat(items).anySatisfy(item -> {
            assertThat(item.source()).isEqualTo(PricingPackagingSource.SYSTEM_LABEL);
            assertThat(item.materialCode()).isNull();
            assertThat(item.materialName()).isEqualTo("1kg吮指五香味酱汁-MY内袋标签");
            assertThat(item.quantity()).isEqualByComparingTo("270");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.materialCode()).isNull();
            assertThat(item.materialName()).isEqualTo("1kg吮指五香味酱汁-MY外箱标签");
            assertThat(item.quantity()).isEqualByComparingTo("27");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.materialCode()).isEqualTo("FBZ0003");
            assertThat(item.quantity()).isEqualByComparingTo("27");
        });
    }

    @Test
    void copiesOnlyExplicitTemplateUnitsAndNeverInfersFromPackagingName() {
        var items = service.createSuggestedItems("P", "产品", BigDecimal.TEN, BigDecimal.ONE, 5,
                List.of(new PackagingTemplateItem("T", 1, "A", "名字含袋但按米计", "PER_BAG", BigDecimal.ONE, null, null, "米"),
                        template("B", "空白袋", "PER_BAG", "1")));
        assertThat(items.get(0).quantityUnit()).isEqualTo("米");
        assertThat(items.get(1).quantityUnit()).isNull();
        assertThat(items.get(2).quantityUnit()).isEqualTo("张");
    }

    @Test
    void rejectsSubmissionWhenAnyPackagingRowIsUnconfirmed() {
        var pending = new PricingPackagingItem("PKG-1", "PRICE-1", 1, PricingPackagingSource.MANUAL,
                null, "手动包装", BigDecimal.ONE, "1个/袋", null, null,
                PricingPackagingStatus.PENDING_CONFIRMATION, null);

        assertThatThrownBy(() -> service.validateForSubmission(List.of(pending)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("包装物料尚未确认");
    }

    private PackagingTemplateItem template(String code, String name, String conversionType, String unitsPerParent) {
        return new PackagingTemplateItem("DEFAULT_BAG", 10, code, name, conversionType,
                new BigDecimal(unitsPerParent), "", "");
    }
}
