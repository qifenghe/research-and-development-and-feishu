package com.lhr.rnd.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportDisplayFormatTest {
    @Test
    void preservesMeaningfulPrecisionAndDistinguishesMissingFromZero() {
        assertThat(ExportDisplayFormat.number(new BigDecimal("0.0001"))).isEqualTo("0.0001");
        assertThat(ExportDisplayFormat.number(new BigDecimal("12.5000"))).isEqualTo("12.5");
        assertThat(ExportDisplayFormat.number(BigDecimal.ZERO)).isEqualTo("0");
        assertThat(ExportDisplayFormat.actualKg(null)).isEqualTo("待填写");
        assertThat(ExportDisplayFormat.actualKg(BigDecimal.ZERO)).isEqualTo("0 kg");
    }

    @Test
    void translatesKnownEnumsAndMarksUnknownValuesForVerification() {
        assertThat(ExportDisplayFormat.materialRole("PRIMARY")).isEqualTo("主料");
        assertThat(ExportDisplayFormat.materialRole("PROCESS_WATER")).isEqualTo("工艺用水");
        assertThat(ExportDisplayFormat.materialState("FROZEN_SOLID")).isEqualTo("冷冻固态");
        assertThat(ExportDisplayFormat.measurementResult("PASS")).isEqualTo("符合");
        assertThat(ExportDisplayFormat.materialState("LAB_STATE_X")).isEqualTo("待核对");
        assertThat(List.of("INTERMEDIATE", "FINISHED", "QUALIFIED", "REUSABLE", "TAILING", "SAMPLE", "WASTE", "HOLD")
                .stream().map(ExportDisplayFormat::outputType).toList())
                .containsExactly("中间产物", "成品", "合格产出", "余料", "尾料", "取样", "废弃", "留存待处理");
        assertThat(ExportDisplayFormat.outputType("BYPRODUCT_X")).isEqualTo("待核对");
    }

    @Test
    void removesSubsecondsFromRecordedTimes() {
        assertThat(ExportDisplayFormat.dateTime("2026-08-19T22:00:22.123456"))
                .isEqualTo("2026-08-19 22:00:22");
        assertThat(ExportDisplayFormat.dateTime("2026-08-19T22:00:22"))
                .isEqualTo("2026-08-19 22:00:22");
    }
}
