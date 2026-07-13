package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.SampleVersion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class PricingWorkbookFixture {
    static final String FORMAL_OUTPUT_FILE_NAME = "500g香卤大肠头-LHYC（核价）原料清单A0 2026.07.13.xlsx";

    private PricingWorkbookFixture() {
    }

    static SampleVersion fragrantBraisedLargeIntestineA0() {
        return SampleVersion.builder()
                .sampleNo("YP202607130001")
                .productName("500g香卤大肠头")
                .productType("冷冻即热菜")
                .specification("500g/袋，20袋/箱")
                .versionNo("A0")
                .ownerName("赵总监")
                .authorName("张研发")
                .effectiveDate(LocalDate.of(2026, 7, 13))
                .referenceOutputKg(new BigDecimal("89"))
                .unitWeightKg(new BigDecimal("0.5"))
                .materials(List.of(
                        new ExperimentMaterial(
                                "原料",
                                1,
                                "YL-001",
                                "主原料",
                                new BigDecimal("100"),
                                new BigDecimal("0.82"),
                                "按实际投入量记录"
                        )
                ))
                .build();
    }
}
