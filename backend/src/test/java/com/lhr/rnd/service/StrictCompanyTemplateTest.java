package com.lhr.rnd.service;

import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StrictCompanyTemplateTest {
    @Test void remarkForOneMaterialDoesNotBecomeAWholeGroupRequirement() throws Exception {
        var d=PricingBasisServiceTest.valid();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(d);
        var first=(com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/materials/0");first.put("remark","仅主料需要预处理");
        var second=first.deepCopy();second.put("id","aux").put("role","AUXILIARY").put("materialName","盐").putNull("remark");
        ((com.fasterxml.jackson.databind.node.ArrayNode)tree.get("materials")).add(second);
        var changed=mapper.treeToValue(tree,com.lhr.rnd.model.PricingBasisDraft.class);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(CompanyPricingRenderer.render(changed,new PricingBasisCalculator().calculate(changed),false)))) {
            assertThat(w.getSheetAt(0).getRow(11).getCell(8).getStringCellValue()).contains("大肠头：仅主料需要预处理");
        }
    }
    @Test void longRemarksUseIdenticalContinuationSheetsWithoutRepeatingMaterials() throws Exception {
        var d=PricingBasisServiceTest.valid();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(d);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/materials/0")).put("remark","备注哨兵\n".repeat(80));
        var changed=mapper.treeToValue(tree,com.lhr.rnd.model.PricingBasisDraft.class);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(CompanyPricingRenderer.render(changed,new PricingBasisCalculator().calculate(changed),false)))) {
            assertThat(w.getNumberOfSheets()).isGreaterThan(1);int count=0;
            for(var s:w)for(String address:java.util.List.of("I12","I17")){var ref=new org.apache.poi.ss.util.CellReference(address);count+=s.getRow(ref.getRow()).getCell(ref.getCol()).getStringCellValue().split("备注哨兵",-1).length-1;}
            assertThat(count).isEqualTo(80);
            for(int p=1;p<w.getNumberOfSheets();p++){var s=w.getSheetAt(p);assertThat(s.getRow(11).getCell(4).getStringCellValue()).isEmpty();assertThat(s.getPrintSetup().getPaperSize()).isEqualTo(w.getSheetAt(0).getPrintSetup().getPaperSize());}
        }
    }
    @Test void pricingKeepsOriginalDimensionsMergesLabelsAndNumberFormats() throws Exception {
        var d=PricingBasisServiceTest.valid();
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(CompanyPricingRenderer.render(d,new PricingBasisCalculator().calculate(d),false)))) {
            var s=w.getSheetAt(0);
            assertThat(s.getNumMergedRegions()).isEqualTo(37);
            assertThat(s.getRow(7).getHeightInPoints()).isEqualTo(24);
            assertThat(s.getRow(10).getHeightInPoints()).isEqualTo(28.5f);
            assertThat(s.getRow(11).getHeightInPoints()).isEqualTo(38);
            assertThat(s.getRow(46).getHeightInPoints()).isEqualTo(36);
            assertThat(s.getLastRowNum()).isEqualTo(52);
            assertThat(s.getRow(8).getCell(0).getStringCellValue()).isEqualTo("编写人");
            assertThat(s.getRow(42).getCell(5).getCellStyle().getDataFormatString()).isEqualTo("0");
            assertThat(s.getRow(46).getCell(5).getCellStyle().getDataFormatString()).isEqualTo("0_ ");
            assertThat(s.getMergedRegions()).anyMatch(m->m.formatAsString().equals("I12:I16"));
            assertThat(s.getMergedRegions()).anyMatch(m->m.formatAsString().equals("J8:J9"));
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/exact-template-qa"));
            try(var out=java.nio.file.Files.newOutputStream(java.nio.file.Path.of("target/exact-template-qa/核价.xlsx"))){w.write(out);}
        }
    }
}
