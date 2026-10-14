package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import java.util.List;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SopExcelTemplateTest {
    @Test void packagingMaterialContinuationDoesNotRepeatFirstPageSteps() throws Exception {
        var v=example();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(v.snapshot());
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0")).put("stepName","包装");
        var packs=new java.util.ArrayList<PricingPackagingItem>();
        for(int i=0;i<7;i++)packs.add(new PricingPackagingItem("p"+i,null,i,null,"B"+i,"包装物"+i,BigDecimal.ONE,"1袋/个",null,null,null,null,"个"));
        var changed=new ProcessExportView(v.productName(),v.sourceLabel(),mapper.treeToValue(tree,ProcessPlan.class),v.externalInputKg(),v.mainYieldPercent(),v.ingredients(),v.majors(),v.finishedQuantity(),packs,v.issues(),v.metadata());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(new SopExcelRenderer().render(changed,"R1","V1",false)))) {
            assertThat(w.getSheet("包装续2").getRow(13).getCell(1).getStringCellValue()).isEmpty();
            assertThat(w.getSheet("包装续2").getRow(40).getCell(4).getStringCellValue()).isEqualTo("包装物6");
        }
    }
    @Test void pressureParametersRetainTheirMeasurementBasis() throws Exception {
        var v=example();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(v.snapshot());
        var s=(com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0");
        s.putArray("parameters").addObject().put("key","vacuum").put("name","真空压力").put("valueType","NUMBER").put("actualValue","50").put("unit","kPa").put("measurementBasis","ABSOLUTE");
        var changed=new ProcessExportView(v.productName(),v.sourceLabel(),mapper.treeToValue(tree,ProcessPlan.class),v.externalInputKg(),v.mainYieldPercent(),v.ingredients(),v.majors(),v.finishedQuantity(),v.packaging(),v.issues(),v.metadata());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(new SopExcelRenderer().render(changed,"R1","V1",false)))) {
            var all=new StringBuilder();for(var sheet:w)for(var row:sheet)for(var c:row)all.append(c.toString());
            assertThat(all.toString()).contains("绝对压力");
        }
    }
    @Test void longInstructionsContinueInOriginalSlotsRatherThanBeingClipped() throws Exception {
        var v=example();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(v.snapshot());
        var instruction="第一行测试要求\n".repeat(30);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0")).put("instruction",instruction);
        var p=mapper.treeToValue(tree,ProcessPlan.class);
        var changed=new ProcessExportView(v.productName(),v.sourceLabel(),p,v.externalInputKg(),v.mainYieldPercent(),v.ingredients(),v.majors(),v.finishedQuantity(),v.packaging(),v.issues(),v.metadata());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(new SopExcelRenderer().render(changed,"R1","V1",false)))) {
            assertThat(w.getSheet("前处理续2")).isNotNull();
            assertThat(w.getPrintArea(w.getSheetIndex("前处理续2"))).contains("$A$1:$J$51");
            assertThat(w.getSheet("前处理续2").getPrintSetup().getPaperSize()).isEqualTo(w.getSheet("前处理").getPrintSetup().getPaperSize());
            int count=0;for(var s:w)if(s.getSheetName().startsWith("前处理"))for(int row:new int[]{22,24,26,28,30})count+=s.getRow(row).getCell(4).getStringCellValue().split("第一行测试要求",-1).length-1;
            assertThat(count).isEqualTo(30);
        }
    }
    @Test void firstPackagingControlIsWrittenToVisibleMergedAnchor() throws Exception {
        var v=example();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var tree=mapper.valueToTree(v.snapshot());
        var step=(com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/majorProcesses/0/steps/0");step.put("stepName","包装");
        step.putArray("controlPoints").addObject().put("itemName","封口完整性").put("basisOrRemark","无破袋");
        var p=mapper.treeToValue(tree,ProcessPlan.class);
        var changed=new ProcessExportView(v.productName(),v.sourceLabel(),p,v.externalInputKg(),v.mainYieldPercent(),v.ingredients(),v.majors(),v.finishedQuantity(),v.packaging(),v.issues(),v.metadata());
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(new SopExcelRenderer().render(changed,"R1","V1",false)))) {
            assertThat(w.getSheetAt(4).getRow(11).getCell(9).getStringCellValue()).contains("封口完整性");
        }
    }
    static ProcessExportView example() {
        var step=new ProcessPlan.MinorStep("s",1,"CUT","切块","NORMAL",null,null,null,null,null,null,"切块机","切成规定规格",List.of());
        var major=new ProcessPlan.MajorProcess("m",1,"PREP","前处理",null,"NONE",null,List.of(step),List.of(),List.of(),null);
        var plan=new ProcessPlan("p","f",1,"DRAFT",List.of(major),null,false);
        return new ProcessExportView("软件验收牛肉","R1",plan,BigDecimal.TEN,null,List.of(new ProcessExportView.Ingredient("B01","牛肉","PRIMARY",BigDecimal.TEN,1,1)),List.of(),
                new ProcessExportView.FinishedQuantity(BigDecimal.TEN,20,"袋","正式"),List.of(),List.of(),new ProcessExportView.Metadata("500g/袋","张研发","2026-10-13"));
    }
    @Test void excelSopTypeUsesSameReadinessChecksAsLegacySop() {
        assertThatCode(()->new ProcessExportCheckService().validateType("SOP_XLSX")).doesNotThrowAnyException();
    }
    @Test void originalExcelSopKeepsHygieneButReplacesSampleEquipmentAndProduct() throws Exception {
        assertThatCode(()->Class.forName("com.lhr.rnd.service.SopExcelRenderer")).doesNotThrowAnyException();
        var type=Class.forName("com.lhr.rnd.service.SopExcelRenderer");
        byte[] bytes=(byte[])type.getDeclaredMethod("render",ProcessExportView.class,String.class,String.class,boolean.class).invoke(type.getDeclaredConstructor().newInstance(),example(),"R1","V1",false);
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(6);
            assertThat(w.getSheetAt(0).getRow(11).getCell(2).getStringCellValue()).isEqualTo("持证上岗，及时补办");
            assertThat(w.getSheetAt(0).getDrawingPatriarch().getShapes().stream().filter(p->p instanceof org.apache.poi.xssf.usermodel.XSSFPicture)).hasSize(5);
            assertThat(w.getSheetAt(1).getRow(2).getCell(3).getStringCellValue()).isEqualTo("软件验收牛肉");
            assertThat(w.getSheetAt(0).getRow(40).getCell(0).getStringCellValue()).isEqualTo("切块机");
            var all=new StringBuilder();var fmt=new org.apache.poi.ss.usermodel.DataFormatter();for(var s:w)for(var r:s)for(var c:r)all.append(fmt.formatCellValue(c)).append('|');
            assertThat(all.toString()).doesNotContain("麦穗腰花","猪腰","ZB80L","康恺","张广魁");
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/exact-template-qa"));java.nio.file.Files.write(java.nio.file.Path.of("target/exact-template-qa/SOP.xlsx"),bytes);
        }
    }
}
