package com.lhr.rnd.service;

import com.lhr.rnd.model.*;
import com.lhr.rnd.domain.StepParameterService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

/** Supplied production SOP workbook. Layout is immutable; only existing value cells are filled. */
public final class SopExcelRenderer {
    private record Step(String major,ProcessPlan.MinorStep value) {}
    public byte[] render(ProcessExportView v,String revision,String documentVersion,boolean preview) {
        try(var in=getClass().getResourceAsStream("/templates/sop-exact-template.xlsx");var w=new XSSFWorkbook(in);var out=new ByteArrayOutputStream()) {
            var steps=new ArrayList<Step>();
            if(v.snapshot()!=null)for(var major:sorted(v.snapshot().majorProcesses(),ProcessPlan.MajorProcess::sequence))
                for(var step:sorted(major.steps(),ProcessPlan.MinorStep::sequence))steps.add(new Step(major.processName(),step));
            var root=w.getSheetAt(1);var sig=v.signature();
            TemplateWorkbook.put(root,"D3",v.productName());TemplateWorkbook.put(root,"D5","产品负责人："+sig.compilerLabel());
            TemplateWorkbook.put(root,"D8",(revision==null?v.sourceLabel():revision)+(documentVersion==null?"":" / "+documentVersion));TemplateWorkbook.put(root,"D9",sig.compilerLabel()+"\n"+sig.compiledDate());
            TemplateWorkbook.put(root,"G7",preview?"预览":sig.reviewed()?"受控":"待审核");TemplateWorkbook.put(root,"G8",sig.reviewed()?sig.reviewedDate():null);
            TemplateWorkbook.put(root,"I9",sig.reviewerLabel()+"\n"+sig.reviewedDate());
            TemplateWorkbook.put(root,"C12",null);TemplateWorkbook.put(root,"D12",v.productName());TemplateWorkbook.put(root,"E12",v.metadata()==null?null:v.metadata().specification());
            TemplateWorkbook.put(root,"F12",v.finishedQuantity()==null?null:v.finishedQuantity().unit());
            // No invented storage, shelf life, approval or product code if absent from the frozen snapshot.
            var main=v.ingredients().stream().filter(i->!"AUXILIARY".equals(i.role())).toList();
            var auxiliary=v.ingredients().stream().filter(i->"AUXILIARY".equals(i.role())).toList();
            var recipe=new ArrayList<>(main);
            if(!auxiliary.isEmpty())recipe.add(new ProcessExportView.Ingredient(null,"辅料配方","AUXILIARY",sum(auxiliary),0,0));
            int recipePages=Math.max(1,(recipe.size()+3)/4);
            for(int page=1;page<recipePages;page++)TemplateWorkbook.cloneSheet(w,1,"配方续"+(page+1));
            for(int page=0;page<recipePages;page++) {
                var sheet=page==0?root:w.getSheet("配方续"+(page+1));
                for(int i=0;i<4&&page*4+i<recipe.size();i++) {
                    var m=recipe.get(page*4+i);int row=16+i;
                    TemplateWorkbook.put(sheet,"C"+row,page*4+i+1);TemplateWorkbook.put(sheet,"D"+row,m.materialCode());TemplateWorkbook.put(sheet,"E"+row,m.materialName());
                    TemplateWorkbook.put(sheet,"H"+row,m.weightKg());TemplateWorkbook.put(sheet,"I"+row,"实际外部投料");
                }
                TemplateWorkbook.put(sheet,"H20",v.externalInputKg());TemplateWorkbook.put(sheet,"H21",v.finishedQuantity()==null?null:v.finishedQuantity().weightKg());
                TemplateWorkbook.put(sheet,"H22",v.finishedQuantity()!=null&&"袋".equals(v.finishedQuantity().unit())?v.finishedQuantity().quantity():null);
                TemplateWorkbook.put(sheet,"H23",v.mainYieldPercent()==null?null:v.mainYieldPercent().movePointLeft(2));
                TemplateWorkbook.put(sheet,"H24",null);
            }
            int seasoningPages=Math.max(1,(auxiliary.size()+5)/6);
            for(int page=1;page<seasoningPages;page++)TemplateWorkbook.cloneSheet(w,2,"调味配方续"+(page+1));
            for(int page=0;page<seasoningPages;page++) {
                var sheet=page==0?w.getSheetAt(2):w.getSheet("调味配方续"+(page+1));
                TemplateWorkbook.put(sheet,"D14","辅料配方");TemplateWorkbook.put(sheet,"C14",page+1);
                for(int i=0;i<6&&page*6+i<auxiliary.size();i++) {
                    var m=auxiliary.get(page*6+i);int row=14+i;
                    TemplateWorkbook.put(sheet,"E"+row,m.materialCode());TemplateWorkbook.put(sheet,"F"+row,m.materialName());TemplateWorkbook.put(sheet,"H"+row,m.weightKg());
                }
                TemplateWorkbook.put(sheet,"H20",sum(auxiliary));
            }
            var prep=steps.stream().filter(s->!packaging(s)).toList();var pack=steps.stream().filter(SopExcelRenderer::packaging).toList();
            fillSteps(w,3,"前处理",prep,5,false);fillSteps(w,4,"包装 ",pack,6,true);
            fillPacking(w,v.packaging());
            var equipment=steps.stream().map(s->s.value().equipment()).filter(e->e!=null&&!e.isBlank()).distinct().toList();
            String[] machineCells={"A41","D41","F41","H41"};
            for(int i=0;i<Math.min(equipment.size(),4);i++)TemplateWorkbook.put(w.getSheetAt(0),machineCells[i],equipment.get(i));
            var es=w.getSheetAt(5);w.setSheetName(5,"设备清单");TemplateWorkbook.put(es,"B3",v.productName()+"设备清单");
            int equipmentPages=Math.max(1,(equipment.size()+24)/25);
            for(int page=1;page<equipmentPages;page++)TemplateWorkbook.cloneSheet(w,5,"设备清单续"+(page+1));
            for(int page=0;page<equipmentPages;page++) {
                var sheet=page==0?es:w.getSheet("设备清单续"+(page+1));
                for(int i=0;i<25&&page*25+i<equipment.size();i++){TemplateWorkbook.put(sheet,"B"+(5+i),page*25+i+1);TemplateWorkbook.put(sheet,"C"+(5+i),equipment.get(page*25+i));}
            }
            String[] flowCells={"C46","E46","F46","H46","I46","J46"};
            for(int i=0;i<Math.min(steps.size(),6);i++)TemplateWorkbook.put(w.getSheetAt(0),flowCells[i],steps.get(i).value().stepName());
            // Header references to missing values otherwise display numeric zero in Excel.
            // Populate their existing cells directly, with no new fields or formatting.
            int printedPages=0;for(var s:w)if(!s.getSheetName().contains("设备清单"))printedPages++;
            int pageNo=0;
            for(var s:w)if(!s.getSheetName().contains("设备清单")) {
                boolean pre=s.getSheetName().startsWith("前处理"),spice=s.getSheetName().startsWith("调味配方");
                String c=pre?"E":"D";
                TemplateWorkbook.put(s,c+"3",v.productName());TemplateWorkbook.put(s,c+"5","产品负责人："+sig.compilerLabel());
                TemplateWorkbook.put(s,c+"7",null);TemplateWorkbook.put(s,"G7",preview?"预览":sig.reviewed()?"受控":"待审核");
                TemplateWorkbook.put(s,(spice?"J":"I")+"7",null);
                TemplateWorkbook.put(s,c+"8",(revision==null?v.sourceLabel():revision)+(documentVersion==null?"":" / "+documentVersion));
                TemplateWorkbook.put(s,"G8",sig.reviewed()?sig.reviewedDate():null);
                TemplateWorkbook.put(s,c+"9",sig.compilerLabel()+(sig.compiledAt()==null?"":" "+sig.compiledDate()));
                TemplateWorkbook.put(s,(spice?"J":"I")+"9",sig.reviewed()?sig.reviewedBy()+" "+sig.reviewedDate():"待审核");
                TemplateWorkbook.put(s,(spice?"K":"J")+"5","页 "+(++pageNo)+" 共 "+printedPages+" 页");
            }
            w.getCreationHelper().createFormulaEvaluator().evaluateAll();w.write(out);return out.toByteArray();
        }catch(java.io.IOException e){throw new IllegalStateException("按原模板生成 SOP Excel 失败",e);}
    }
    private static void fillSteps(XSSFWorkbook w,int index,String name,List<Step> steps,int capacity,boolean pack) {
        int[] rows=pack?new int[]{14,20,22,25,30,34}:new int[]{23,25,27,29,31};
        String[] columns=pack?new String[]{"B","F"}:new String[]{"B","E"};
        String[] flow=pack?null:new String[]{"C","E","F","H","I","J"};
        int next=0,bodyOffset=0,controlOffset=0,page=0;
        while(next<steps.size()) {
            var s=page==0?w.getSheetAt(index):TemplateWorkbook.cloneSheet(w,index,name.trim()+"续"+(page+1));
            clearSteps(s,pack);var controls=new ArrayList<String>();
            var shared=pack?null:TemplateText.capacity(s,"I23");int controlLines=pack?0:shared.lines();
            for(int i=0;i<capacity&&next<steps.size();i++) {
                var step=steps.get(next).value();int row=rows[i];var body=instructions(step);var control=controls(step);
                if(!pack&&bodyOffset>=body.length()&&controlOffset<control.length()&&controlLines<=2)break;
                String address=columns[1]+row;var bodyCapacity=TemplateText.capacity(s,address);
                boolean combined=pack&&row==20;
                var bodyPart=TemplateText.take(body,bodyOffset,combined&&controlOffset<control.length()?bodyCapacity.lines(Math.max(1,bodyCapacity.lines()/2)):bodyCapacity);
                TemplateText.Part controlPart;
                if(pack) {
                    var target=row==14?"J12":"J"+row;
                    var limit=combined?bodyCapacity.lines(Math.max(0,bodyCapacity.lines()-bodyPart.lines()-1)):TemplateText.capacity(s,target);
                    controlPart=TemplateText.take(control,controlOffset,limit);
                    if(!combined)TemplateWorkbook.put(s,target,controlPart.text());
                }else {
                    controlPart=TemplateText.take(control,controlOffset,shared.lines(Math.max(0,controlLines-2)));
                    if(!controlPart.text().isEmpty()){controls.add(step.stepName()+"\n"+controlPart.text());controlLines-=controlPart.lines()+2;}
                }
                boolean continued=bodyOffset>0||controlOffset>0;
                TemplateWorkbook.put(s,columns[0]+row,step.stepName()+(continued?"（续）":""));
                TemplateWorkbook.put(s,address,bodyPart.text()+(combined&&!controlPart.text().isEmpty()?"\n"+controlPart.text():""));
                if(flow!=null){TemplateWorkbook.put(s,flow[i]+"16",step.stepName()+(continued?"（续）":""));TemplateWorkbook.put(s,flow[i]+"17","详见下方管控标准");}
                bodyOffset=bodyPart.end();controlOffset=controlPart.end();
                if(bodyOffset>=body.length()&&controlOffset>=control.length()){next++;bodyOffset=0;controlOffset=0;}
            }
            if(!pack)TemplateWorkbook.put(s,"I23",String.join("\n\n",controls));
            page++;
        }
    }
    private static void clearSteps(Sheet s,boolean pack) {
        if(pack) {
            for(int row:new int[]{14,20,22,25,30,34}){TemplateWorkbook.put(s,"B"+row,null);TemplateWorkbook.put(s,"F"+row,null);}
            for(String cell:List.of("J12","J22","J25","J30","J34"))TemplateWorkbook.put(s,cell,null);
        }else {
            for(int row:new int[]{23,25,27,29,31}){TemplateWorkbook.put(s,"B"+row,null);TemplateWorkbook.put(s,"E"+row,null);}
            TemplateWorkbook.put(s,"I23",null);
            for(String col:List.of("C","E","F","H","I","J")){TemplateWorkbook.put(s,col+"16",null);TemplateWorkbook.put(s,col+"17",null);}
        }
    }
    private static void fillPacking(XSSFWorkbook w,List<PricingPackagingItem> packaging) {
        int pages=Math.max(1,(packaging.size()+5)/6);
        // Step and material continuations share one base; allocate extra pages only when needed.
        for(int p=1;p<pages;p++)if(w.getSheet("包装续"+(p+1))==null)clearSteps(TemplateWorkbook.cloneSheet(w,4,"包装续"+(p+1)),true);
        for(int p=0;p<pages;p++) {
            var s=p==0?w.getSheetAt(4):w.getSheet("包装续"+(p+1));
            for(int i=0;i<6&&p*6+i<packaging.size();i++) {
                var x=packaging.get(p*6+i);int row=41+i;
                TemplateWorkbook.put(s,"B"+row,p*6+i+1);TemplateWorkbook.put(s,"C"+row,x.materialCode());TemplateWorkbook.put(s,"E"+row,x.materialName());
                TemplateWorkbook.put(s,"H"+row,x.quantity());TemplateWorkbook.put(s,"I"+row,x.packageSpec());TemplateWorkbook.put(s,"J"+row,x.remark());
            }
        }
    }
    private static String instructions(ProcessPlan.MinorStep s) {
        var lines=new ArrayList<String>();if(s.instruction()!=null)lines.add(s.instruction());
        if(s.equipment()!=null&&!s.equipment().isBlank())lines.add("设备："+s.equipment());
        for(var p:StepParameterService.effective(s)) {
            var basis=switch(TemplateWorkbook.text(p.measurementBasis())){case "GAUGE"->"表压";case "ABSOLUTE"->"绝对压力";case "DEVICE_PERCENT"->"设备显示百分比";default->TemplateWorkbook.text(p.measurementBasis());};
            lines.add(TemplateWorkbook.text(p.name())+"："+TemplateWorkbook.text(p.actualValue())+TemplateWorkbook.text(p.unit())+(basis.isEmpty()?"":"（"+basis+"）")+(p.remark()==null?"":"；"+p.remark()));
            if(p.lowerLimit()!=null&&!p.lowerLimit().isBlank())lines.add(TemplateWorkbook.text(p.name())+"下限："+p.lowerLimit()+TemplateWorkbook.text(p.unit()));
            if(p.upperLimit()!=null&&!p.upperLimit().isBlank())lines.add(TemplateWorkbook.text(p.name())+"上限："+p.upperLimit()+TemplateWorkbook.text(p.unit()));
        }
        for(var m:values(s.materials()))lines.add(("STEP_OUTPUT".equals(m.sourceType())?"承接产物：":"投料：")+TemplateWorkbook.text(m.materialName())+" "+("PACKAGING".equals(m.materialRole())?ExportDisplayFormat.number(m.quantity())+TemplateWorkbook.text(m.quantityUnit()):ExportDisplayFormat.actualKg(m.weightKg())));
        for(var m:values(s.materials()))if(m.additionTiming()!=null||m.additionMethod()!=null)lines.add(TemplateWorkbook.text(m.materialName())+"："+TemplateWorkbook.text(m.additionTiming())+" "+TemplateWorkbook.text(m.additionMethod()));
        for(var o:values(s.outputs()))lines.add("产出："+TemplateWorkbook.text(o.outputName())+" "+(o.weightKg()==null?"待填写":o.weightKg().stripTrailingZeros().toPlainString()+"kg"));
        return String.join("\n",lines);
    }
    private static String controls(ProcessPlan.MinorStep s) {
        var lines=new ArrayList<String>();
        for(var c:values(s.controlPoints())) {
            lines.add(TemplateWorkbook.text(c.itemName())+" "+TemplateWorkbook.text(c.basisOrRemark()));
            if(c.targetValue()!=null)lines.add("目标："+c.targetValue().stripTrailingZeros().toPlainString()+TemplateWorkbook.text(c.unit()));
            if(c.lowerLimit()!=null||c.upperLimit()!=null)lines.add("范围："+(c.lowerLimit()==null?"无下限":c.lowerLimit().stripTrailingZeros().toPlainString())+" 至 "+(c.upperLimit()==null?"无上限":c.upperLimit().stripTrailingZeros().toPlainString())+TemplateWorkbook.text(c.unit()));
            lines.add("检测："+TemplateWorkbook.text(c.method())+"；工具："+TemplateWorkbook.text(c.measurementTool())+"；频次："+TemplateWorkbook.text(c.frequency()));
            lines.add("偏差处理："+TemplateWorkbook.text(c.deviationAction()));
            for(var m:values(c.measurements())) {
                lines.add("实测："+(m.measuredValue()==null?"待填写":m.measuredValue().stripTrailingZeros().toPlainString())+TemplateWorkbook.text(c.unit())+" "+ExportDisplayFormat.dateTime(m.measuredAt())+" "+ExportDisplayFormat.measurementResult(m.result()));
                if(m.deviationAction()!=null)lines.add("实测偏差处理："+m.deviationAction());
                if(m.retestResult()!=null)lines.add("复测结果："+m.retestResult());
                if(m.remark()!=null)lines.add("实测备注："+m.remark());
            }
        }
        return String.join("\n",lines);
    }
    private static boolean packaging(Step x){return TemplateWorkbook.text(x.major()).contains("包装")||TemplateWorkbook.text(x.major()).contains("入库")||TemplateWorkbook.text(x.value().stepName()).matches(".*(包装|金检|金探|装箱|入库).*" );}
    private static BigDecimal sum(List<ProcessExportView.Ingredient> list){if(list.stream().anyMatch(i->i.weightKg()==null))return null;return list.stream().map(ProcessExportView.Ingredient::weightKg).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static <T> List<T> values(List<T> v){return v==null?List.of():v;}
    private static <T> List<T> sorted(List<T> v,java.util.function.ToIntFunction<T> order){return values(v).stream().sorted(Comparator.comparingInt(order)).toList();}
}
