package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Company single-sheet layout. Blank workbook prevents inherited template sample-data leakage. */
@Service
public class PricingMaterialListRenderer {
    public byte[] render(PricingBasisDraft d,PricingBasisResult result,boolean preview) {
        if(!preview&&!result.issues().isEmpty()) throw new BusinessException("PRICING_BASIS_INCOMPLETE",result.issues().get(0).message());
        try(var w=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var h=d.header(); var name=h==null?"原材料清单":h.productName();
            var s=w.createSheet(WorkbookUtil.createSafeSheetName(name==null?"原材料清单":name));
            int[] widths={5,6,6,16,18,12,14,16,17,18,10};
            for(int i=0;i<widths.length;i++)s.setColumnWidth(i,widths[i]*256);
            var font=w.createFont();font.setFontName("宋体");font.setFontHeightInPoints((short)11);
            var body=w.createCellStyle();body.setFont(font);body.setWrapText(true);body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setBorderBottom(BorderStyle.THIN);body.setBorderTop(BorderStyle.THIN);body.setBorderLeft(BorderStyle.THIN);body.setBorderRight(BorderStyle.THIN);
            body.setDataFormat(w.createDataFormat().getFormat("0.####"));
            var pct=w.createCellStyle();pct.cloneStyleFrom(body);pct.setDataFormat(w.createDataFormat().getFormat("0.00%"));
            var title=w.createCellStyle();title.cloneStyleFrom(body);title.setAlignment(HorizontalAlignment.CENTER);
            var titleFont=w.createFont();titleFont.setFontName("宋体");titleFont.setFontHeightInPoints((short)18);titleFont.setBold(true);title.setFont(titleFont);
            var head=w.createCellStyle();head.cloneStyleFrom(body);head.setAlignment(HorizontalAlignment.CENTER);head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            merged(s,0,1,0,10,(h==null?"":text(h.companyName()))+"\n原材料清单"+(preview?"（预览·非正式）":""));
            merged(s,2,5,0,2,"产品名称");merged(s,2,3,3,10,name);
            merged(s,4,5,3,6,"产品负责人："+(h==null?"待填写":text(h.ownerName())));merged(s,4,5,7,10,"规格："+(h==null?"待填写":text(h.specification())));
            merged(s,6,6,0,2,"类别");merged(s,6,6,3,4,h==null?null:h.category());cell(s,6,5,"状态");merged(s,6,6,6,7,"待审核");cell(s,6,8,"版本号");merged(s,6,6,9,10,h==null?null:h.version());
            merged(s,7,7,0,2,"文件编号");merged(s,7,7,3,4,h==null?null:h.documentNumber());cell(s,7,5,"实施日期");merged(s,7,7,6,7,h==null?null:h.effectiveDate());merged(s,7,8,8,8,"研发批准");merged(s,7,8,9,10,"待审批");
            merged(s,8,8,0,2,"编写人");merged(s,8,8,3,4,h==null?null:h.authorName());cell(s,8,5,"修改日期");merged(s,8,8,6,7,"—");
            merged(s,11,11,0,1,"工序");cell(s,11,2,"编号");cell(s,11,3,"NS编码");merged(s,11,11,4,5,"物料名称");cell(s,11,6,"重量（kg）");cell(s,11,7,"参考利用率（%）");cell(s,11,8,"领料重量（kg）");merged(s,11,11,9,10,"备注");
            int row=12,groupStart=12;String previous=null;
            for(int i=0;i<d.materials().size();i++) {
                var m=d.materials().get(i);var r=result.materials().get(i);
                if(i>0&&!java.util.Objects.equals(previous,m.majorId())) {merged(s,groupStart,row-1,0,1,d.materials().get(i-1).stage());groupStart=row;}
                previous=m.majorId();
                cell(s,row,2,i+1);cell(s,row,3,m.materialCode()==null?"":m.materialCode());merged(s,row,row,4,5,m.materialName());cell(s,row,6,r.usageKg());cell(s,row,7,r.utilizationRate());
                if(r.requisitionKg()!=null) {
                    if("AFTER_PROCESS".equals(m.weightBasis())) cell(s,row,8,0).setCellFormula("G"+(row+1)+"/H"+(row+1));
                    else cell(s,row,8,r.requisitionKg());
                } else cell(s,row,8,null);
                merged(s,row,row,9,10,m.remark()==null?"":m.remark());row++;
            }
            if(!d.materials().isEmpty())merged(s,groupStart,row-1,0,1,d.materials().get(d.materials().size()-1).stage());
            int end=row;
            merged(s,row,row,0,5,"总计");
            if(result.usageTotalKg()!=null)cell(s,row,6,0).setCellFormula("SUM(G13:G"+row+")");else cell(s,row,6,null);
            if(result.requisitionTotalKg()!=null)cell(s,row,8,0).setCellFormula("SUM(I13:I"+row+")");else cell(s,row,8,null);
            merged(s,row,row,9,10,"");row++;
            merged(s,row,row,0,5,"研发部参考主料出成（kg）");merged(s,row,row,6,10,d.mainOutputKg());int outputRow=++row;
            merged(s,row,row,0,5,"主料得率（%）");merged(s,row,row,6,10,result.mainYieldPercent()==null?null:result.mainYieldPercent().divide(new BigDecimal("100")));
            if(result.mainYieldPercent()!=null) {
                var refs=new java.util.ArrayList<String>();for(int i=0;i<d.materials().size();i++)if(d.mainMaterialIds().contains(d.materials().get(i).id()))refs.add("I"+(13+i));
                s.getRow(row).getCell(6).setCellFormula("G"+outputRow+"/SUM("+String.join(",",refs)+")");
            }
            int yieldRow=row++;
            merged(s,row,row,0,5,"研发部参考包数");merged(s,row,row,6,10,result.referenceBags());row++;
            merged(s,row,row,0,10,"可分装成品净重："+text(d.finishedNetKg())+" kg；单包净重："+text(d.unitWeightKg())+" kg；不足整包余量："+text(result.remainingKg())+" kg；参考箱数："+text(result.referenceBoxes()));row+=2;
            merged(s,row,row,0,10,"包装物料");row++;
            int packagingHeader=row;
            merged(s,row,row,0,1,"编号");cell(s,row,2,"NS编码");merged(s,row,row,3,4,"物料名称");cell(s,row,5,"内部代码");cell(s,row,6,"数量（供参考）");cell(s,row,7,"数量单位");cell(s,row,8,"包装规格");merged(s,row,row,9,10,"备注");row++;
            if(d.packaging()!=null)for(int i=0;i<d.packaging().size();i++) {
                var p=d.packaging().get(i);merged(s,row,row,0,1,i+1);cell(s,row,2,p.materialCode()==null?"":p.materialCode());merged(s,row,row,3,4,p.materialName());cell(s,row,5,p.internalCode()==null?"":p.internalCode());cell(s,row,6,p.quantity());cell(s,row,7,p.quantityUnit());cell(s,row,8,p.specification());merged(s,row,row,9,10,p.remark()==null?"":p.remark());row++;
            }
            if(preview)for(var issue:result.issues()){merged(s,row,row,0,10,"待补充："+issue.message());row++;}
            row++;merged(s,row,row,0,10,"本资料为机密文件，未经公司研发部门书面许可，不得保有或泄露本资料任何内容。");
            for(int ri=0;ri<=row;ri++) { var rr=s.getRow(ri);if(rr==null)rr=s.createRow(ri);rr.setHeightInPoints(30);
                for(int c=0;c<11;c++){var cc=rr.getCell(c);if(cc==null)cc=rr.createCell(c);cc.setCellStyle(ri<=1?title:ri==11||ri==packagingHeader?head:body);}
                if(ri>=12&&ri<end)rr.getCell(7).setCellStyle(pct);
                // Long names and notes get space without reducing type size.
                int max=0;for(var cc:rr)if(cc.getCellType()==CellType.STRING)max=Math.max(max,cc.getStringCellValue().length());
                if(ri>=12&&ri<end||ri>packagingHeader&&ri<row)rr.setHeightInPoints(Math.max(30,(float)Math.ceil(max/18.0)*17+8));
            }
            s.getRow(yieldRow).getCell(6).setCellStyle(pct);s.getRow(row).setHeightInPoints(38);
            s.setDisplayGridlines(false);s.createFreezePane(0,12);s.setFitToPage(true);s.setAutobreaks(true);s.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);s.getPrintSetup().setFitWidth((short)1);s.getPrintSetup().setFitHeight((short)0);s.setRepeatingRows(new CellRangeAddress(11,11,-1,-1));s.getFooter().setRight("第 &P 页 / 共 &N 页");w.setPrintArea(0,0,10,0,row);
            w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.write(out);return out.toByteArray();
        } catch(java.io.IOException e) {throw new IllegalStateException("生成原材料清单失败",e);}
    }
    private static String text(Object value){return value==null?"待填写":value.toString();}
    private static Cell cell(Sheet s,int row,int col,Object value){var r=s.getRow(row);if(r==null)r=s.createRow(row);var c=r.getCell(col);if(c==null)c=r.createCell(col);if(value instanceof Number n)c.setCellValue(n.doubleValue());else c.setCellValue(text(value));return c;}
    private static void merged(Sheet s,int r1,int r2,int c1,int c2,Object value){cell(s,r1,c1,value);if(r1!=r2||c1!=c2)s.addMergedRegion(new CellRangeAddress(r1,r2,c1,c2));}
}
