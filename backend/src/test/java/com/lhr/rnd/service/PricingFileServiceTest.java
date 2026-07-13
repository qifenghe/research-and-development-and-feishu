package com.lhr.rnd.service;

import com.lhr.rnd.model.ExperimentMaterial;
import com.lhr.rnd.model.SampleVersion;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PricingFileServiceTest {
    private static final String TEMPLATE_PATH = "/templates/pricing-material-list-template.xlsx";
    private static final List<String> REFERENCE_PRODUCT_TOKENS = List.of(
            "200g黄豆焖猪脚", "杭椒牛柳", "我问问", "500g", "香卤", "大肠",
            "YRP", "FGT", "TJJ", "FXL", "FYT", "FBT", "YSC", "99999", "100.000",
            "2026.04.28", "赵新武", "黄丽金"
    );

    @Test
    void generatesFormalLayoutForTwoMaterials() throws Exception {
        var service = new PricingFileService();
        var result = service.generate(pricingVersionWithMaterials(2), "V1", "LHYC");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            PricingWorkbookAssertions.assertFormalLayout(workbook, 2);
        }
    }

    @Test
    void generatesFormalLayoutForTwentyFiveMaterials() throws Exception {
        var service = new PricingFileService();
        var result = service.generate(pricingVersionWithMaterials(25), "V1", "LHYC");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result.content()))) {
            PricingWorkbookAssertions.assertFormalLayout(workbook, 25);
        }
    }

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
            PricingWorkbookAssertions.assertFormalLayout(workbook, version.materials().size());
        }
    }

    @Test
    void templateContainsNoReferenceProductMetadata() throws Exception {
        try (var template = PricingFileService.class.getResourceAsStream(TEMPLATE_PATH)) {
            assertThat(template).as("pricing template").isNotNull();
            assertSanitizedWorkbook(template.readAllBytes(), false);
        }
    }

    @Test
    void generatedWorkbookContainsNoInheritedReferenceProductMetadata() throws Exception {
        var result = new PricingFileService().generate(sanitizedPricingVersion(), "V1", "客户A");

        assertSanitizedWorkbook(result.content(), true);
    }

    private SampleVersion pricingVersionWithMaterials(int materialCount) {
        return SampleVersion.builder()
                .sampleNo("YP202607130001")
                .productName("500g香卤大肠头")
                .productType("酱卤肉制品")
                .specification("500g/袋，20袋/箱")
                .versionNo("A0")
                .ownerName("赵新武")
                .authorName("黄丽金")
                .effectiveDate(LocalDate.of(2026, 7, 13))
                .referenceOutputKg(new BigDecimal("89"))
                .unitWeightKg(new BigDecimal("0.5"))
                .materials(materials(materialCount))
                .build();
    }

    private SampleVersion sanitizedPricingVersion() {
        return SampleVersion.builder()
                .sampleNo("SP202607130001")
                .productName("清炖牛腩")
                .productType("预制菜肴")
                .specification("300克/袋，12袋/箱")
                .versionNo("B1")
                .ownerName("李明")
                .authorName("王芳")
                .effectiveDate(LocalDate.of(2026, 7, 13))
                .referenceOutputKg(new BigDecimal("72"))
                .unitWeightKg(new BigDecimal("0.3"))
                .materials(List.of(
                        new ExperimentMaterial("预处理", 1, "MAT001", "牛腩", new BigDecimal("80"),
                                new BigDecimal("0.90"), "测试用料")
                ))
                .build();
    }

    private List<ExperimentMaterial> materials(int materialCount) {
        var materials = new ArrayList<ExperimentMaterial>();
        for (int index = 1; index <= materialCount; index++) {
            materials.add(new ExperimentMaterial(
                    "前处理",
                    index,
                    "MAT%03d".formatted(index),
                    "测试物料" + index,
                    BigDecimal.valueOf(index),
                    new BigDecimal("0.95"),
                    "测试备注"
            ));
        }
        return materials;
    }

    private void assertSanitizedWorkbook(byte[] content, boolean printAreaExpected) throws Exception {
        assertSanitizedPackage(content);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                assertThat(workbook.getSheetVisibility(sheetIndex))
                        .as("sheet %s visibility", sheet.getSheetName())
                        .isEqualTo(org.apache.poi.ss.usermodel.SheetVisibility.VISIBLE);
                assertContainsNoReferenceProductText(sheet.getSheetName());
                var formatter = new DataFormatter();
                for (var row : sheet) {
                    for (var cell : row) {
                        assertContainsNoReferenceProductText(formatter.formatCellValue(cell));
                    }
                }
            }

            assertThat(workbook.getAllNames())
                    .as("workbook names")
                    .allSatisfy(name -> {
                        assertThat(name.getRefersToFormula()).doesNotContain("#REF!");
                        assertThat(name.getNameName()).matches("(?i)_?xlnm\\.?(print_area|print_titles)|print_area|print_titles");
                    });
            if (printAreaExpected) {
                assertThat(workbook.getAllNames()).hasSize(1);
                assertThat(workbook.getPrintArea(0)).isNotBlank();
            } else {
                assertThat(workbook.getAllNames()).isEmpty();
                assertThat(workbook.getPrintArea(0)).isBlank();
            }

            assertThat(workbook).isInstanceOf(XSSFWorkbook.class);
            assertThat(((XSSFWorkbook) workbook).getExternalLinksTable()).isEmpty();
        }
    }

    private void assertSanitizedPackage(byte[] content) throws IOException {
        var entryNames = new ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entryNames.add(entry.getName());
                var bytes = readCurrentZipEntry(zip);
                var text = new String(bytes, StandardCharsets.UTF_8) + new String(bytes, StandardCharsets.UTF_16LE);
                assertContainsNoReferenceProductText(text);
            }
        }
        assertThat(entryNames).noneMatch(name -> name.equals("docProps/custom.xml")
                || name.startsWith("customXml/")
                || name.startsWith("xl/externalLinks/"));
    }

    private byte[] readCurrentZipEntry(ZipInputStream zip) throws IOException {
        var output = new ByteArrayOutputStream();
        zip.transferTo(output);
        return output.toByteArray();
    }

    private void assertContainsNoReferenceProductText(String text) {
        for (var token : REFERENCE_PRODUCT_TOKENS) {
            assertThat(text).as("reference product token %s", token).doesNotContain(token);
        }
    }
}
