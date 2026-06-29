package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.SampleVersion;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingFileServiceTest {

    @Test
    void generatesPricingWorkbookFromOfficialTemplate() throws Exception {
        var service = new PricingFileService();
        var version = SampleVersion.builder()
                .sampleNo("YP202606170001")
                .productName("500g香卤大肠头")
                .productType("酱卤肉制品")
                .specification("500g/袋，20袋/箱")
                .versionNo("A0")
                .ownerName("赵新武")
                .authorName("黄丽金")
                .effectiveDate(LocalDate.of(2026, 4, 29))
                .referenceOutputKg(new BigDecimal("89"))
                .unitWeightKg(new BigDecimal("0.5"))
                .materials(List.of(
                        new ExperimentMaterial("清洗", 1, "YRP00033", "冻猪大肠头（预煮）",
                                new BigDecimal("100"), new BigDecimal("0.95"), "前处理车间配制")
                ))
                .build();

        var result = service.generate(version, "V1", "LHYC");

        assertThat(result.fileName()).isEqualTo("500g香卤大肠头-LHYC（核价）原料清单A0 2026.04.29.xlsx");
        assertThat(result.pricingVersion()).isEqualTo("A0-核价V1");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("500g香卤大肠头-LHYC原料清单");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("500g香卤大肠头（核价）-LHYC");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("产品负责人:赵新武");
            assertThat(sheet.getRow(4).getCell(9).getStringCellValue()).isEqualTo("规格：500g/袋，20袋/箱");
            assertThat(sheet.getRow(6).getCell(9).getStringCellValue()).isEqualTo("A/0");
            assertThat(sheet.getRow(12).getCell(0).getStringCellValue()).isEqualTo("清洗");
            assertThat(sheet.getRow(12).getCell(3).getStringCellValue()).isEqualTo("YRP00033");
            assertThat(sheet.getRow(12).getCell(8).getCellFormula()).isEqualTo("G13/H13");
            assertThat(sheet.getRow(49).getCell(6).getCellFormula()).isEqualTo("SUM(G13:G13)");
            assertThat(sheet.getRow(50).getCell(6).getNumericCellValue()).isEqualTo(89D);
            assertThat(sheet.getRow(51).getCell(6).getCellFormula()).isEqualTo("G51/I13");
            assertThat(sheet.getRow(52).getCell(6).getNumericCellValue()).isEqualTo(178D);
            assertThat(sheet.getRow(57).getCell(6).getNumericCellValue()).isEqualTo(178D);
            assertThat(sheet.getRow(60).getCell(6).getNumericCellValue()).isEqualTo(9D);
        }
    }
}
