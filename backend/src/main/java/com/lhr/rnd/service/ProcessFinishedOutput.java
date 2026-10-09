package com.lhr.rnd.service;

import com.lhr.rnd.model.ProcessPlan;
import com.lhr.rnd.model.ProcessExportView;
import com.lhr.rnd.model.TrialScheme;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

/** Finished mass is actual terminal output, never a yield-derived estimate. */
final class ProcessFinishedOutput {
    private ProcessFinishedOutput() {}
    static ProcessExportView.FinishedQuantity view(ProcessPlan plan, TrialScheme.FinishedOutput output, String source) {
        return output == null ? null : new ProcessExportView.FinishedQuantity(weight(plan),"kg".equals(output.unit())?weight(plan):output.quantity(),output.unit(),source);
    }
    static BigDecimal weight(ProcessPlan plan) {
        var consumed=new HashSet<String>();
        for(var m:values(plan.majorProcesses()))for(var s:values(m.steps()))for(var r:values(s.materials()))
            if("STEP_OUTPUT".equals(r.sourceType()))consumed.add(r.sourceStepOutputId());
        var ids=new HashSet<String>(); BigDecimal sum=BigDecimal.ZERO; boolean found=false;
        for(var m:values(plan.majorProcesses()))for(var s:values(m.steps()))for(var o:values(s.outputs())) {
            if(!"FINISHED".equals(o.outputType())||o.continueFlow()||consumed.contains(o.id()))continue;
            found=true;
            if(o.id()==null||!ids.add(o.id())||o.weightKg()==null||o.weightKg().signum()<0)return null;
            sum=sum.add(o.weightKg());
        }
        return found?sum:null;
    }
    private static <T> List<T> values(List<T> v){return v==null?List.of():v;}
}
