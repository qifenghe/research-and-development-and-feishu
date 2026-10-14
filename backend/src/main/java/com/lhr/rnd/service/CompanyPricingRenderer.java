package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.ArrayList;

/** Supplied LHYC A:J layout. Every sample value is cleared before real data is written. */
final class CompanyPricingRenderer {
    record Food(String stage,String code,String name,BigDecimal usage,BigDecimal rate,BigDecimal requisition,String remark,boolean divide) {}
    record Pack(String code,String name,String internal,BigDecimal quantity,String unit,String specification,String remark) {}
    static byte[] render(PricingBasisDraft d,PricingBasisResult r,boolean preview) {
        var foods=new ArrayList<Food>();
        for(int i=0;i<d.materials().size();i++){var m=d.materials().get(i);var v=r.materials().get(i);
            foods.add(new Food(m.stage(),m.materialCode(),m.materialName(),v.usageKg(),v.utilizationRate(),v.requisitionKg(),m.remark(),"AFTER_PROCESS".equals(m.weightBasis())));}
        var packs=d.packaging()==null?List.<Pack>of():d.packaging().stream().filter(p->p.quantity()==null||p.quantity().signum()!=0).map(p->new Pack(p.materialCode(),p.materialName(),p.internalCode(),p.quantity(),p.quantityUnit(),p.specification(),p.remark())).toList();
        return render(d.header(),foods,packs,r.usageTotalKg(),r.requisitionTotalKg(),d.finishedNetKg(),r.mainYieldPercent(),null,
                r.referenceBags()==null?null:BigDecimal.valueOf(r.referenceBags()),d.mainMaterialIds().stream().map(id->{for(int i=0;i<d.materials().size();i++)if(id.equals(d.materials().get(i).id()))return "H"+(12+i);return "";}).filter(v->!v.isEmpty()).toList(),preview,
                preview?r.issues().stream().map(PricingBasisResult.Issue::message).toList():List.of(),null,d.signature());
    }
    static byte[] render(ProcessExportView v,boolean preview) {
        var meta=v.metadata();var h=new PricingBasisDraft.Header("江西龙汇肉制品有限责任公司",v.productName(),meta==null?null:meta.specification(),null,meta==null?null:meta.compiledBy(),null,null,v.sourceLabel(),meta==null?null:meta.date());
        var foods=v.ingredients().stream().map(i->new Food(i.majorSequence()+"."+i.stepSequence(),i.materialCode(),i.materialName(),i.weightKg(),null,null,"领料口径待核价确认",false)).toList();
        var packs=v.packaging().stream().map(p->new Pack(p.materialCode(),p.materialName(),null,p.quantity(),p.quantityUnit(),p.packageSpec(),p.remark())).toList();
        var f=v.finishedQuantity();
        return render(h,foods,packs,v.externalInputKg(),null,f==null?null:f.weightKg(),v.mainYieldPercent(),null,f==null||!"袋".equals(f.unit())?null:f.quantity(),List.of(),preview,
                preview?new ProcessExportCheckService().check(v,"PRICING_XLSX").issues().stream().map(ProcessExportView.Issue::message).toList():List.of(),v.actualProvenanceLabel(),v.signature());
    }
    private static byte[] render(PricingBasisDraft.Header h,List<Food> foods,List<Pack> packs,BigDecimal total,BigDecimal requisition,BigDecimal output,BigDecimal yield,BigDecimal packingYield,BigDecimal bags,List<String> mainRefs,boolean preview,List<String> issues,String provenance,DocumentSignature signature) {
        return ExactPricingRenderer.render(h,foods,packs,total,requisition,output,yield,packingYield,bags,mainRefs,preview,issues,provenance,signature);
    }
    private static byte[] legacyRender(PricingBasisDraft.Header h,List<Food> foods,List<Pack> packs,BigDecimal total,BigDecimal requisition,BigDecimal output,BigDecimal yield,BigDecimal packingYield,BigDecimal bags,List<String> mainRefs,boolean preview,List<String> issues,String provenance,DocumentSignature signature) {
        if(h==null)h=new PricingBasisDraft.Header(null,null,null,null,null,null,null,null,null);
        if(signature==null)signature=DocumentSignature.pending(h.authorName(),null);
        try(var in=CompanyPricingRenderer.class.getResourceAsStream("/templates/pricing-red-braised-template.xlsx");var w=WorkbookFactory.create(in);var out=new ByteArrayOutputStream()) {
            var s=w.getSheetAt(0);w.setSheetName(0,"原料");
            var foodStyle=new CellStyle[10];var packStyle=new CellStyle[10];
            for(int c=0;c<10;c++){foodStyle[c]=s.getRow(11).getCell(c,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellStyle();packStyle[c]=s.getRow(46).getCell(c,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellStyle();}
            for(int c=0;c<10;c++) {
                var food=w.createCellStyle();food.cloneStyleFrom(foodStyle[c]);food.setWrapText(true);food.setShrinkToFit(false);foodStyle[c]=food;
                var pack=w.createCellStyle();pack.cloneStyleFrom(packStyle[c]);pack.setWrapText(true);pack.setShrinkToFit(false);packStyle[c]=pack;
            }
            // Optional fractional placeholders still leave a literal decimal point in Excel.
            // General keeps quantities numeric, displaying 178 and 2.5 without a trailing dot.
            var packageCountStyle=w.createCellStyle();packageCountStyle.cloneStyleFrom(packStyle[5]);packageCountStyle.setDataFormat(w.createDataFormat().getFormat("General"));packStyle[5]=packageCountStyle;
            var packageUnitStyle=w.createCellStyle();packageUnitStyle.cloneStyleFrom(packStyle[6]);packStyle[9]=packageUnitStyle;
            for(var row:s)for(var cell:row){cell.setBlank();cell.removeCellComment();if(cell.getHyperlink()!=null)cell.removeHyperlink();}
            for(int i=s.getNumMergedRegions()-1;i>=0;i--)if(s.getMergedRegion(i).getFirstRow()>=10)s.removeMergedRegion(i);
            value(s,0,0,(h.companyName()==null?"江西龙汇肉制品有限责任公司":h.companyName())+"\n原材料清单"+(preview?"（预览·非正式）":""));
            value(s,2,0,"产品名称");value(s,2,3,h.productName());value(s,4,3,"产品负责人："+text(h.ownerName()));value(s,4,6,"规格：");value(s,4,8,h.specification());
            value(s,6,0,"类别");value(s,6,3,h.category());value(s,6,5,"状态");value(s,6,6,preview?"预览":"待审核");value(s,6,8,"版本号");value(s,6,9,h.version());
            value(s,7,0,"文件编号");value(s,7,3,h.documentNumber());value(s,7,5,"实施日期");value(s,7,6,h.effectiveDate());value(s,7,8,"研发批准");value(s,7,9,"待审批");
            value(s,8,0,"编写人 / 编制人");value(s,8,3,signature.compilerLabel());value(s,8,5,"编制日期");value(s,8,6,signature.compiledDate());
            // The narrow approval cell carries only status. Full signatures belong in the wide footer.
            value(s,7,8,"研发批准");value(s,7,9,signature.reviewed()?"已审核":"待审核");
            for(int row=6;row<=8;row++) {
                for(var cell:s.getRow(row)) {
                    var style=w.createCellStyle();style.cloneStyleFrom(cell.getCellStyle());
                    style.setWrapText(true);style.setShrinkToFit(false);cell.setCellStyle(style);
                }
                s.getRow(row).setHeightInPoints(Math.max(s.getRow(row).getHeightInPoints(),34));
            }
            if(provenance!=null){value(s,9,0,provenance);merge(s,9,9,0,9);s.getRow(9).setHeightInPoints(28);}
            var headers=new String[]{"工序","","编号.","NS编码","物料名称","重量（kg）","参考利用率（％）","领料重量（kg)","备注",""};
            for(int c=0;c<10;c++)value(s,10,c,headers[c]);merge(s,10,10,0,1);merge(s,10,10,8,9);
            s.getRow(10).setHeightInPoints(44);
            int count=Math.max(27,foods.size()),sumRow=11+count;
            for(int i=0;i<count;i++){int row=11+i;styled(s,row,foodStyle,30);merge(s,row,row,0,1);merge(s,row,row,8,9);if(i>=foods.size())continue;var f=foods.get(i);
                value(s,row,0,f.stage());value(s,row,2,i+1);value(s,row,3,empty(f.code()));value(s,row,4,f.name());value(s,row,5,f.usage());value(s,row,6,f.rate());value(s,row,7,f.requisition());
                if(f.divide()&&f.requisition()!=null)value(s,row,7,0).setCellFormula("F"+(row+1)+"/G"+(row+1));
                value(s,row,8,empty(f.remark()));s.getRow(row).setHeightInPoints(Math.max(30,(float)Math.ceil(Math.max(Math.max(empty(f.name()).length()/11.0,empty(f.remark()).length()/20.0),Math.max(empty(f.code()).length()/12.0,empty(f.stage()).length()/7.0)))*17+8));}
            String[] labels={"总计","研发部参考出成(kg）","肉的得率（%）","包装得率（%）","研发部参考包数"};
            for(int i=0;i<5;i++){styled(s,sumRow+i,foodStyle,30);value(s,sumRow+i,2,count+i+1);value(s,sumRow+i,3,labels[i]);merge(s,sumRow+i,sumRow+i,3,4);if(i>0)merge(s,sumRow+i,sumRow+i,5,7);}
            value(s,sumRow,5,total);if(total!=null&&!foods.isEmpty())value(s,sumRow,5,0).setCellFormula("SUM(F12:F"+(11+foods.size())+")");
            value(s,sumRow,7,requisition);if(requisition!=null&&!foods.isEmpty())value(s,sumRow,7,0).setCellFormula("SUM(H12:H"+(11+foods.size())+")");
            value(s,sumRow+1,5,output);value(s,sumRow+2,5,yield==null?null:yield.divide(new BigDecimal("100")));value(s,sumRow+3,5,packingYield);
            if(output!=null&&!mainRefs.isEmpty()&&yield!=null)value(s,sumRow+3,5,0).setCellFormula("F"+(sumRow+2)+"/SUM("+String.join(",",mainRefs)+")");
            value(s,sumRow+4,5,bags);var pct=w.createCellStyle();pct.cloneStyleFrom(foodStyle[6]);pct.setDataFormat(w.createDataFormat().getFormat("0.00%"));
            var countStyle=w.createCellStyle();countStyle.cloneStyleFrom(foodStyle[5]);countStyle.setDataFormat(w.createDataFormat().getFormat("General"));s.getRow(sumRow+4).getCell(5).setCellStyle(countStyle);
            for(int row=11;row<sumRow;row++)s.getRow(row).getCell(6).setCellStyle(pct);value(s,sumRow+2,5,yield==null?null:yield.divide(new BigDecimal("100"))).setCellStyle(pct);s.getRow(sumRow+3).getCell(5).setCellStyle(pct);
            int title=sumRow+6,head=title+1;styled(s,title,foodStyle,28);value(s,title,3,"包装物料");styled(s,head,packStyle,30);
            String[] packHeaders={"","编号.","NS编码","物料名称","物料内部代码","数量（供参考）","包装规格","备注","","单位"};
            for(int c=0;c<10;c++)value(s,head,c,packHeaders[c]);merge(s,head,head,7,8);
            s.getRow(head).setHeightInPoints(44);
            int packCount=Math.max(5,packs.size());for(int i=0;i<packCount;i++){int row=head+1+i;styled(s,row,packStyle,34);merge(s,row,row,7,8);if(i>=packs.size())continue;var p=packs.get(i);value(s,row,1,i+1);value(s,row,2,empty(p.code()));value(s,row,3,p.name());value(s,row,4,empty(p.internal()));value(s,row,5,p.quantity());value(s,row,6,empty(p.specification()));value(s,row,7,empty(p.remark()));value(s,row,9,p.unit());s.getRow(row).setHeightInPoints(Math.max(34,(float)Math.ceil(Math.max(Math.max(Math.max(empty(p.name()).length()/10.0,empty(p.code()).length()/9.0),Math.max(empty(p.specification()).length()/12.0,empty(p.remark()).length()/20.0)),empty(p.internal()).length()/20.0))*17+8));}
            int footer=head+packCount+2;
            footerLine(w,s,footer++,foodStyle,"编制人："+signature.compilerLabel()+"    编制日期："+signature.compiledDate());
            footerLine(w,s,footer++,foodStyle,"审核人："+signature.reviewerLabel()+"    审核日期："+signature.reviewedDate());
            for(String issue:issues){styled(s,footer,foodStyle,38);value(s,footer,0,"待补充："+issue);merge(s,footer,footer,0,9);footer++;}
            footerLine(w,s,footer,foodStyle,"本资料为机密文件，未经江西龙汇肉制品有限责任公司研发部门的书面许可，不得以任何形式保有或部分保有以及泄露本资料任何内容。");
            s.setDisplayGridlines(false);s.setFitToPage(true);s.setAutobreaks(true);s.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);s.getPrintSetup().setFitWidth((short)1);s.getPrintSetup().setFitHeight((short)(foods.size()<=27&&packs.size()<=5&&issues.isEmpty()?1:0));s.setRepeatingRows(new CellRangeAddress(10,10,-1,-1));s.createFreezePane(0,11);s.getFooter().setRight("第 &P 页 / 共 &N 页");w.setPrintArea(0,0,9,0,footer);w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.write(out);return out.toByteArray();
        }catch(java.io.IOException e){throw new IllegalStateException("生成公司核价表失败",e);}
    }
    private static void footerLine(Workbook w,Sheet s,int row,CellStyle[] styles,String text) {
        styled(s,row,styles,42);var cell=value(s,row,0,text);merge(s,row,row,0,9);
        var style=w.createCellStyle();style.cloneStyleFrom(cell.getCellStyle());style.setWrapText(true);style.setShrinkToFit(false);
        style.setAlignment(HorizontalAlignment.LEFT);style.setVerticalAlignment(VerticalAlignment.CENTER);
        var font=w.createFont();font.setFontName("宋体");font.setFontHeightInPoints((short)10);style.setFont(font);cell.setCellStyle(style);
    }
    private static void styled(Sheet s,int row,CellStyle[] styles,float height){var r=s.getRow(row);if(r==null)r=s.createRow(row);r.setHeightInPoints(height);for(int c=0;c<10;c++){var cell=r.getCell(c);if(cell==null)cell=r.createCell(c);cell.setCellStyle(styles[c]);}}
    private static Cell value(Sheet s,int row,int col,Object v){var r=s.getRow(row);if(r==null)r=s.createRow(row);var c=r.getCell(col);if(c==null)c=r.createCell(col);if(v==null)c.setBlank();else if(v instanceof Number n)c.setCellValue(n.doubleValue());else c.setCellValue(v.toString());return c;}
    private static void merge(Sheet s,int r1,int r2,int c1,int c2){s.addMergedRegion(new CellRangeAddress(r1,r2,c1,c2));}
    private static String empty(String s){return s==null?"":s;}
    private static String text(String s){return s==null?"待填写":s;}
}
