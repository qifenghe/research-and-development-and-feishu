package com.lhr.rnd.service;

import com.lhr.rnd.api.BusinessException;
import com.lhr.rnd.model.MaterialCatalogItem;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.List;
import java.util.HashMap;
import com.lhr.rnd.model.ProcessPlan;

/** Resolves ingredient identity without allowing later catalog edits to rewrite history. */
@Service
public class MaterialReferenceService {
 private final MaterialCatalogService catalog;
 public MaterialReferenceService(MaterialCatalogService catalog){this.catalog=catalog;}
 public record Reference(String catalogMaterialId,String code,String name,String specification,String unit){}
 public Reference resolveForSave(Reference incoming,Reference persisted){
  if(incoming==null)throw invalid("物料不能为空");
  if(incoming.catalogMaterialId()==null||incoming.catalogMaterialId().isBlank())return incoming;
  if(persisted!=null&&Objects.equals(incoming.catalogMaterialId(),persisted.catalogMaterialId()))return persisted;
  var item=catalog.find(incoming.catalogMaterialId());requireIngredient(item);
  return new Reference(item.id(),item.code(),item.name(),item.specification(),item.unit());
 }
 public void requireActiveReference(Reference reference){
  if(reference==null||reference.catalogMaterialId()==null||reference.catalogMaterialId().isBlank())throw invalid("外部物料尚未关联物料库");
  requireIngredient(catalog.find(reference.catalogMaterialId()));
 }
 public ProcessPlan resolvePlan(ProcessPlan incoming,ProcessPlan persisted){
  if(incoming==null)return null;
  var old=new HashMap<String,ProcessPlan.StepMaterial>();
  if(persisted!=null) for(var major:values(persisted.majorProcesses()))for(var step:values(major.steps()))for(var material:values(step.materials()))
   if(material.id()!=null)old.put(material.id(),material);
  var majors=values(incoming.majorProcesses()).stream().map(major->new ProcessPlan.MajorProcess(
   major.id(),major.sequence(),major.processCode(),major.processName(),major.description(),major.yieldBasis(),major.remark(),
   values(major.steps()).stream().map(step->new ProcessPlan.MinorStep(step.id(),step.sequence(),step.stepCode(),step.stepName(),step.stepType(),
    step.parameter1Name(),step.parameter1Value(),step.parameter1Unit(),step.parameter2Name(),step.parameter2Value(),step.parameter2Unit(),
    step.equipment(),step.instruction(),values(step.materials()).stream().map(m->resolveMaterial(m,old.get(m.id()))).toList(),
    step.outputs(),step.controlPoints(),step.parameters())).toList(),major.inputs(),major.outputs(),major.yield())).toList();
  return new ProcessPlan(incoming.id(),incoming.experimentFormId(),incoming.versionNo(),incoming.status(),majors,incoming.batchYieldPercent(),
   incoming.balanceToleranceKg(),incoming.legacy(),incoming.sourceRevisionId(),incoming.changeReason());
 }
 public List<com.lhr.rnd.model.ExperimentMaterial> resolveLegacy(List<com.lhr.rnd.model.ExperimentMaterial> incoming,List<com.lhr.rnd.model.ExperimentMaterial> persisted){
  var usedIds=new java.util.HashSet<String>();
  return values(incoming).stream().map(m->{
   var old=m.id()==null?null:values(persisted).stream().filter(p->Objects.equals(p.id(),m.id())).findFirst().orElse(null);
   // Only a row belonging to this form can carry an existing identity forward.
   var rowId=old==null?java.util.UUID.randomUUID().toString().replace("-", ""):old.id();
   if(!usedIds.add(rowId))throw invalid("投料行重复，请刷新后重试");
   var ref=resolveForSave(new Reference(m.catalogMaterialId(),m.materialCode(),m.materialName(),m.catalogSpecification(),m.catalogUnit()),
    old==null||m.refreshCatalogSnapshot()?null:new Reference(old.catalogMaterialId(),old.materialCode(),old.materialName(),old.catalogSpecification(),old.catalogUnit()));
   return new com.lhr.rnd.model.ExperimentMaterial(m.stage(),m.sequence(),ref.code(),ref.name(),m.weightKg(),m.utilizationRate(),m.remark(),
    m.materialCategory(),m.primaryMaterial(),m.formulaRatio(),m.inputUnit(),ref.catalogMaterialId(),ref.specification(),ref.unit(),false,rowId);
  }).toList();
 }
 public ProcessPlan.StepMaterial resolveMaterial(ProcessPlan.StepMaterial m,ProcessPlan.StepMaterial persisted){
  boolean intermediate="STEP_OUTPUT".equals(m.sourceType());
  var ref=intermediate?new Reference(null,m.materialCode(),m.materialName(),null,null):
   resolveForSave(reference(m),m.refreshCatalogSnapshot()||persisted==null||"STEP_OUTPUT".equals(persisted.sourceType())?null:reference(persisted));
  return new ProcessPlan.StepMaterial(m.id(),m.sequence(),m.materialRole(),ref.code(),ref.name(),m.materialState(),m.weightKg(),
   m.formulaMaterialId(),m.remark(),m.sourceType(),intermediate?m.sourceStepOutputId():null,m.additionTiming(),m.additionMethod(),
   ref.catalogMaterialId(),ref.specification(),ref.unit());
 }
 public void requireActiveReferences(ProcessPlan plan){
  for(var major:values(plan.majorProcesses()))for(var step:values(major.steps()))for(var material:values(step.materials()))
   if(!"STEP_OUTPUT".equals(material.sourceType()))requireActiveReference(reference(material));
 }
 public List<com.lhr.rnd.model.ProcessSubmissionCheck.Issue> submissionIssues(ProcessPlan plan){
  var issues=new java.util.ArrayList<com.lhr.rnd.model.ProcessSubmissionCheck.Issue>();
  if(plan==null)return issues;
  for(var major:values(plan.majorProcesses()))for(var step:values(major.steps()))for(var material:values(step.materials())){
   if("STEP_OUTPUT".equals(material.sourceType()))continue;
   try {requireActiveReference(reference(material));}
   catch(BusinessException e){issues.add(new com.lhr.rnd.model.ProcessSubmissionCheck.Issue("MATERIAL_REFERENCE_INVALID",
     material.materialName()+"："+e.getMessage(),major.sequence(),step.sequence()));}
  }
  return issues;
 }
 private Reference reference(ProcessPlan.StepMaterial m){return new Reference(m.catalogMaterialId(),m.materialCode(),m.materialName(),m.catalogSpecification(),m.catalogUnit());}
 private static <T> List<T> values(List<T> items){return items==null?List.of():items;}
 private void requireIngredient(MaterialCatalogItem item){
  if(!"ACTIVE".equals(item.status()))throw invalid("物料已停用，请重新选择");
  if("PACKAGING".equals(item.category())||!"kg".equals(item.unit()))throw invalid("原辅料投料不可选择包装物料或非kg单位");
 }
 private BusinessException invalid(String message){return new BusinessException("MATERIAL_REFERENCE_INVALID",message);}
}
