package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.ArrayList;

/** Value overlay on the supplied 53-row company workbook, including identical continuation sheets. */
final class ExactPricingRenderer {
    static byte[] render(PricingBasisDraft.Header h,List<CompanyPricingRenderer.Food> foods,List<CompanyPricingRenderer.Pack> packs,
            BigDecimal total,BigDecimal requisition,BigDecimal output,BigDecimal yield,BigDecimal packingYield,BigDecimal bags,
            List<String> mainRefs,boolean preview,List<String> issues,String provenance,DocumentSignature sig) {
        if(h==null)h=new PricingBasisDraft.Header(null,null,null,null,null,null,null,null,null);
        if(sig==null)sig=DocumentSignature.pending(h.authorName(),null);
        try(var in=ExactPricingRenderer.class.getResourceAsStream("/templates/pricing-exact-template.xlsx");var w=new XSSFWorkbook(in);var out=new ByteArrayOutputStream()) {
            int pages=Math.max(1,Math.max((foods.size()+26)/27,(packs.size()+4)/5));
            for(int i=1;i<pages;i++)TemplateWorkbook.cloneSheet(w,0,"原料续"+(i+1));
            for(int page=0;page<pages;page++) {
                var s=w.getSheetAt(page);
                TemplateWorkbook.put(s,"D3",TemplateWorkbook.text(h.productName())+(preview?"\n预览·非正式":"")+(preview&&provenance!=null?"；"+provenance:""));TemplateWorkbook.put(s,"D5","产品负责人："+TemplateWorkbook.text(h.ownerName()));
                TemplateWorkbook.put(s,"I5",h.specification());TemplateWorkbook.put(s,"D7",h.category());TemplateWorkbook.put(s,"G7",preview?"预览":sig.reviewed()?"受控":"待审核");
                TemplateWorkbook.put(s,"J7",h.version());TemplateWorkbook.put(s,"D8",h.documentNumber());TemplateWorkbook.put(s,"G8",h.effectiveDate());
                TemplateWorkbook.pricingSignature(s,sig);
                if(preview&&!issues.isEmpty())TemplateWorkbook.put(s,"J12",String.join("\n",issues.stream().map(i->"待补充："+i).toList()));
                var notes=new ArrayList<String>();var laterNotes=new ArrayList<String>();
                var noteValues=new ArrayList<String>();var laterNoteValues=new ArrayList<String>();
                for(int i=0;i<27&&page*27+i<foods.size();i++) {
                    var f=foods.get(page*27+i);int row=12+i;
                    TemplateWorkbook.put(s,"C"+row,page*27+i+1);TemplateWorkbook.put(s,"D"+row,f.code());TemplateWorkbook.put(s,"E"+row,f.name());
                    TemplateWorkbook.put(s,"F"+row,f.usage());TemplateWorkbook.put(s,"G"+row,f.rate());TemplateWorkbook.put(s,"H"+row,f.requisition());
                    if(f.divide()&&f.requisition()!=null)TemplateWorkbook.put(s,"H"+row,0).setCellFormula("F"+row+"/G"+row);
                    if(f.remark()!=null&&!f.remark().isBlank()) {
                        (i<5?notes:laterNotes).add(f.name()+"："+f.remark());
                        (i<5?noteValues:laterNoteValues).add(f.remark());
                    }
                }
                int populated=Math.min(27,Math.max(0,foods.size()-page*27));
                writeNotes(w,s,groupNote(notes,noteValues,Math.min(5,populated)),groupNote(laterNotes,laterNoteValues,Math.max(0,populated-5)));
                TemplateWorkbook.put(s,"F39",total);TemplateWorkbook.put(s,"H39",requisition);TemplateWorkbook.put(s,"F40",output);
                TemplateWorkbook.put(s,"F41",yield==null?null:yield.movePointLeft(2));TemplateWorkbook.put(s,"F42",packingYield);TemplateWorkbook.put(s,"F43",bags);
                // All continuation sheets show the same full-batch totals, not misleading page subtotals.
                if(!foods.isEmpty())TemplateWorkbook.put(s,"F39",0).setCellFormula(sumRanges(w,foods.size(),"F"));
                if(requisition!=null&&!foods.isEmpty())TemplateWorkbook.put(s,"H39",0).setCellFormula(sumRanges(w,foods.size(),"H"));
                if(output!=null&&!mainRefs.isEmpty()&&yield!=null) {
                    var refs=new ArrayList<String>();
                    for(var r:mainRefs){int n=Integer.parseInt(r.substring(1))-12;refs.add("'"+w.getSheetAt(n/27).getSheetName()+"'!H"+(12+n%27));}
                    TemplateWorkbook.put(s,"F42",0).setCellFormula("F40/SUM("+String.join(",",refs)+")");
                }
                for(int i=0;i<5&&page*5+i<packs.size();i++) {
                    var p=packs.get(page*5+i);int row=47+i;
                    TemplateWorkbook.put(s,"B"+row,page*5+i+1);TemplateWorkbook.put(s,"C"+row,p.code());TemplateWorkbook.put(s,"D"+row,p.name());
                    TemplateWorkbook.put(s,"E"+row,p.internal());TemplateWorkbook.put(s,"F"+row,p.quantity());TemplateWorkbook.put(s,"G"+row,TemplateWorkbook.text(p.specification())+(p.unit()==null?"":"\n数量单位："+p.unit()));
                    // Original H:I is the remark slot; there is no added unit column.
                    TemplateWorkbook.put(s,"H"+row,p.remark());
                }
            }
            w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.write(out);return out.toByteArray();
        }catch(java.io.IOException e){throw new IllegalStateException("按原模板生成核价表失败",e);}
    }
    private static String groupNote(List<String> notes,List<String> values,int populated){
        return !values.isEmpty()&&values.size()==populated&&values.stream().distinct().count()==1?values.get(0):String.join("\n",notes);
    }
    private static void writeNotes(XSSFWorkbook w,Sheet s,String first,String later) {
        var firstPart=TemplateText.take(first,0,TemplateText.capacity(s,"I12"));
        var laterPart=TemplateText.take(later,0,TemplateText.capacity(s,"I17"));
        TemplateWorkbook.put(s,"I12",firstPart.text());TemplateWorkbook.put(s,"I17",laterPart.text());
        var remaining=(first.substring(firstPart.end())+"\n"+later.substring(laterPart.end())).strip();int offset=0;
        while(offset<remaining.length()) {
            var page=TemplateWorkbook.cloneSheet(w,0,"备注续"+(w.getNumberOfSheets()+1));
            for(int row=12;row<=38;row++)for(String col:List.of("C","D","E","F","G","H"))TemplateWorkbook.put(page,col+row,null);
            for(int row=47;row<=51;row++)for(String col:List.of("B","C","D","E","F","G","H"))TemplateWorkbook.put(page,col+row,null);
            TemplateWorkbook.put(page,"J12",null);TemplateWorkbook.put(page,"I38",null);
            var a=TemplateText.take(remaining,offset,TemplateText.capacity(page,"I12"));offset=a.end();TemplateWorkbook.put(page,"I12",a.text());
            var b=TemplateText.take(remaining,offset,TemplateText.capacity(page,"I17"));offset=b.end();TemplateWorkbook.put(page,"I17",b.text());
        }
    }
    private static String sumRanges(XSSFWorkbook w,int count,String col){
        var ranges=new ArrayList<String>();for(int page=0;page*27<count;page++)ranges.add("'"+w.getSheetAt(page).getSheetName()+"'!"+col+"12:"+col+(11+Math.min(27,count-page*27)));
        return "SUM("+String.join(",",ranges)+")";
    }
}
