package com.lhr.rnd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class MaterialIntakeSourceService {
 private final ProcessPlanService plans; private final TrialSchemeService trials; private final JdbcTemplate db; private final ObjectMapper mapper;
 public MaterialIntakeSourceService(ProcessPlanService p,TrialSchemeService t,JdbcTemplate d,ObjectMapper m){plans=p;trials=t;db=d;mapper=m;}
 public record Source(String formId,String trialId,int version,ProcessPlan plan,TrialScheme trial){}
 public Source loadEditableSource(String formId,String trialId,SessionPrincipal actor){
  plans.requireDraftReadAccess(formId,actor);
  if(!plans.canReadForComparison(formId,actor))throw new BusinessException("MATERIAL_INTAKE_FORBIDDEN","当前账号不能修改来源方案");
  var states=db.query("select status from experiment_form where id=? for update",(r,n)->r.getString(1),formId);
  if(states.isEmpty()||!"DRAFT".equals(states.get(0)))throw new BusinessException("MATERIAL_INTAKE_LOCKED","来源实验单已锁定");
  if(trialId!=null&&!trialId.isBlank()){
   db.query("select id from experiment_trial_scheme where id=? and experiment_form_id=? for update",(r,n)->r.getString(1),trialId,formId);
   var trial=trials.find(formId,trialId,actor);if(trial.archived())throw new BusinessException("MATERIAL_INTAKE_LOCKED","来源试验方案已归档");
   return new Source(formId,trialId,trial.versionNo(),trial.plan(),trial);
  }
  var plan=plans.find(formId);if(!"DRAFT".equals(plan.status()))throw new BusinessException("MATERIAL_INTAKE_LOCKED","正式工艺已提交，请先创建草稿");
  return new Source(formId,null,plan.versionNo(),plan,null);
 }
 public void applyApprovedReferences(Source source,Map<String,MaterialCatalogItem> bindings,SessionPrincipal actor){
  try{
   var tree=(ObjectNode)mapper.valueToTree(source.plan());
   for(var major:tree.path("majorProcesses"))for(var step:major.path("steps"))for(var row:step.path("materials")){
    var item=bindings.get(row.path("temporaryMaterialId").asText());if(item==null)continue;
    var node=(ObjectNode)row;node.put("catalogMaterialId",item.id());node.put("materialCode",item.code());node.put("materialName",item.name());
    node.put("catalogSpecification",item.specification());node.put("catalogUnit",item.unit());node.put("refreshCatalogSnapshot",true);
    node.remove(java.util.List.of("temporaryMaterialId","temporaryClassificationId","temporarySpecification","formulaMaterialId"));
   }
   var plan=mapper.treeToValue(tree,ProcessPlan.class);
   if(source.trial()==null)plans.save(source.formId(),plan,actor);
   else {var t=source.trial();trials.save(source.formId(),t.id(),new TrialSchemeService.SaveCommand(t.versionNo(),t.name(),t.purpose(),t.variables(),t.conclusion(),t.recommendationReason(),t.qualityScore(),t.qualityNotes(),t.difficulty(),plan,t.plannedData()),actor);}
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
