package com.lhr.rnd.service;

import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PricingMaterialListRendererTest {
    @Test void distributableTemplateContainsLayoutOnlyAndNoBusinessCells() throws Exception {
        try(var in=CompanyPricingRenderer.class.getResourceAsStream("/templates/pricing-red-braised-template.xlsx");var w=new XSSFWorkbook(in)) {
            for(var sheet:w)for(var row:sheet)for(var cell:row)
                assertThat(cell.getCellType()).as("template cell %s",cell.getAddress()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(w.getSheetAt(0).getColumnWidth(4)).isEqualTo(22*256+128);
            assertThat(w.getSheetAt(0).getNumMergedRegions()).isGreaterThan(20);
        }
    }
    @Test void processExportDoesNotGuessRequisitionOrConvertOtherUnitsToBags() throws Exception {
        for(var unit:java.util.List.of("kg","盒","袋")) {
            var v=new com.lhr.rnd.model.ProcessExportView("测试","正式",null,java.math.BigDecimal.TEN,null,
                    java.util.List.of(new com.lhr.rnd.model.ProcessExportView.Ingredient("001","肉","PRIMARY",java.math.BigDecimal.TEN,1,1)),java.util.List.of(),
                    new com.lhr.rnd.model.ProcessExportView.FinishedQuantity(java.math.BigDecimal.TEN,20,unit,"记录"),java.util.List.of(),java.util.List.of(),null);
            try(var w=new XSSFWorkbook(new ByteArrayInputStream(CompanyPricingRenderer.render(v,false)))) {
                var s=w.getSheetAt(0);
                assertThat(s.getRow(11).getCell(7).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
                assertThat(s.getRow(38).getCell(7).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
                if(unit.equals("袋")) assertThat(s.getRow(42).getCell(5).getNumericCellValue()).isEqualTo(20);
                else assertThat(s.getRow(42).getCell(5).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            }
        }
    }
    @Test void usesSuppliedRedBraisedPorkCoordinatesAndSingleSheet() throws Exception {
        var draft=PricingBasisServiceTest.valid();
        var bytes=new PricingMaterialListRenderer().render(draft,new PricingBasisCalculator().calculate(draft),false);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(1);var s=w.getSheetAt(0);
            assertThat(s.getRow(10).getCell(2).getStringCellValue()).isEqualTo("编号.");
            assertThat(s.getRow(10).getCell(5).getStringCellValue()).isEqualTo("重量（kg）");
            assertThat(s.getRow(10).getCell(7).getStringCellValue()).isEqualTo("领料重量（kg)");
            assertThat(s.getRow(44).getCell(3).getStringCellValue()).isEqualTo("包装物料");
            assertThat(s.getRow(45).getCell(5).getStringCellValue()).isEqualTo("数量（供参考）");
            assertThat(w.getPrintArea(0)).contains("$J$");
            assertThat(s.getPrintSetup().getFitHeight()).isEqualTo((short)1);
        }
    }
    @Test void longListsKeepRepeatedIngredientsAndSafeSheetNames() throws Exception {
        var base=PricingBasisServiceTest.valid();
        var materials=new java.util.ArrayList<com.lhr.rnd.model.PricingBasisDraft.Material>();
        for(int i=0;i<100;i++) materials.add(new com.lhr.rnd.model.PricingBasisDraft.Material("m"+i,"g"+(i/10),"s"+i,"m"+i,"工序"+(i/10),null,i%2==0?"盐":"很长的原材料名称用于检验自动换行及打印范围不会截断明细记录","PRIMARY","EXTERNAL",java.math.BigDecimal.ONE,"AFTER_PROCESS",java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,null,null,"同料不同工序分行"));
        var h=base.header();var header=new com.lhr.rnd.model.PricingBasisDraft.Header(h.companyName(),"产品/测试*?[]",h.specification(),h.ownerName(),h.authorName(),h.category(),h.documentNumber(),h.version(),h.effectiveDate());
        var d=new com.lhr.rnd.model.PricingBasisDraft(1,base.sourceRevisionId(),header,materials,base.mainOutputKg(),java.util.List.of("m0"),base.mainScopeBasis(),base.finishedNetKg(),base.unitWeightKg(),20,178,base.packaging());
        var bytes=new PricingMaterialListRenderer().render(d,new PricingBasisCalculator().calculate(d),false);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var s=w.getSheetAt(0);assertThat(s.getSheetName()).doesNotContain("/","*","?","[","]");
            for(int i=0;i<100;i++)assertThat(s.getRow(11+i).getCell(4).getStringCellValue()).isEqualTo(materials.get(i).materialName());
            assertThat(s.getRow(12).getHeightInPoints()).isGreaterThan(30);
            assertThat(s.getPrintSetup().getFitHeight()).isZero();
            assertThat(s.getRow(111).getCell(5).getCellFormula()).isEqualTo("SUM(F12:F111)");
            assertThat(s.getRow(111).getCell(7).getCellFormula()).isEqualTo("SUM(H12:H111)");
            assertThat(s.getRow(114).getCell(5).getCellFormula()).isEqualTo("F113/SUM(H12)");
            assertThat(s.getRow(117).getCell(3).getStringCellValue()).isEqualTo("包装物料");
            assertThat(s.getMergedRegions()).anyMatch(r->r.formatAsString().equals("D113:E113"));
            assertThat(s.getMergedRegions()).anyMatch(r->r.formatAsString().equals("H120:I120"));
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
            assertThat(eval.evaluate(s.getRow(11).getCell(7)).getNumberValue()).isCloseTo(105.263157894737,within(.00000001));
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
