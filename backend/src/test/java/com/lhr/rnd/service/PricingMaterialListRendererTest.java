package com.lhr.rnd.service;

import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PricingMaterialListRendererTest {
    @Test void longListsKeepRepeatedIngredientsAndSafeSheetNames() throws Exception {
        var base=PricingBasisServiceTest.valid();
        var materials=new java.util.ArrayList<com.lhr.rnd.model.PricingBasisDraft.Material>();
        for(int i=0;i<100;i++) materials.add(new com.lhr.rnd.model.PricingBasisDraft.Material("m"+i,"g"+(i/10),"s"+i,"m"+i,"工序"+(i/10),null,i%2==0?"盐":"很长的原材料名称用于检验自动换行及打印范围不会截断明细记录","PRIMARY","EXTERNAL",java.math.BigDecimal.ONE,"AFTER_PROCESS",java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,null,null,"同料不同工序分行"));
        var h=base.header();var header=new com.lhr.rnd.model.PricingBasisDraft.Header(h.companyName(),"产品/测试*?[]",h.specification(),h.ownerName(),h.authorName(),h.category(),h.documentNumber(),h.version(),h.effectiveDate());
        var d=new com.lhr.rnd.model.PricingBasisDraft(1,base.sourceRevisionId(),header,materials,base.mainOutputKg(),java.util.List.of("m0"),base.mainScopeBasis(),base.finishedNetKg(),base.unitWeightKg(),20,178,base.packaging());
        var bytes=new PricingMaterialListRenderer().render(d,new PricingBasisCalculator().calculate(d),false);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var s=w.getSheetAt(0);assertThat(s.getSheetName()).doesNotContain("/","*","?","[","]");
            for(int i=0;i<100;i++)assertThat(s.getRow(12+i).getCell(4).getStringCellValue()).isEqualTo(materials.get(i).materialName());
            assertThat(s.getRow(13).getHeightInPoints()).isGreaterThan(30);
            assertThat(s.getPrintSetup().getFitHeight()).isZero();
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/pricing-basis-qa"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/pricing-basis-qa/long-list.xlsx"),bytes);
    }
    @Test void rendersSingleTemplateSheetWithVerifiedFormulaAndPackaging() throws Exception {
        var d=PricingBasisServiceTest.valid();
        var bytes=new PricingMaterialListRenderer().render(d,new PricingBasisCalculator().calculate(d),false);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(1);
            var s=w.getSheetAt(0); var eval=w.getCreationHelper().createFormulaEvaluator();
            assertThat(eval.evaluate(s.getRow(12).getCell(8)).getNumberValue()).isCloseTo(105.263157894737,within(.00000001));
            var text=new StringBuilder(); var fmt=new DataFormatter();
            for(var r:s) for(var c:r) text.append(fmt.formatCellValue(c,eval)).append('|');
            assertThat(text.toString()).contains("原材料清单","NS编码","领料重量","84.55%","178","003","0.99").doesNotContain("黄豆焖猪脚","成本","单价","已批准");
            assertThat(w.getPrintArea(0)).isNotBlank();
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/pricing-basis-qa"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/pricing-basis-qa/confirmed-list.xlsx"),bytes);
    }
    @Test void previewsMissingValuesButFormalRejectsThem() {
        var d=PricingBasisCalculatorTest.draft(PricingBasisCalculatorTest.material("AFTER_PROCESS","100","100",null),null);
        var r=new PricingBasisCalculator().calculate(d); var renderer=new PricingMaterialListRenderer();
        assertThat(renderer.render(d,r,true)).isNotEmpty();
        assertThatThrownBy(()->renderer.render(d,r,false)).hasMessageContaining("利用率");
    }
}
