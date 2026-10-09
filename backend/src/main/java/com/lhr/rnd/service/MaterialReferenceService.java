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
  boolean packaging="PACKAGING".equals(m.materialRole());
  if(packaging&&intermediate)throw invalid("包装物不可引用食品中间产物");
  if(packaging&&(m.weightKg()!=null||m.quantity()!=null&&m.quantity().signum()<0))throw invalid("包装物请填写非负数量，不可填写食品重量");
  if(!packaging&&(m.quantity()!=null||m.quantityUnit()!=null))throw invalid("原辅料请填写kg重量，不可填写包材数量");
  var ref=intermediate?new Reference(null,null,m.materialName(),null,null):
   resolveStepReference(m,m.refreshCatalogSnapshot()||persisted==null||!Objects.equals(m.materialRole(),persisted.materialRole())||"STEP_OUTPUT".equals(persisted.sourceType())?null:persisted);
  if(packaging&&!List.of("个","张","只","袋","盒","箱","米").contains(m.quantityUnit()==null?ref.unit()==null?"":ref.unit():m.quantityUnit()))throw invalid("请选择包装数量单位");
  if(packaging&&ref.catalogMaterialId()!=null&&m.quantityUnit()!=null&&!Objects.equals(m.quantityUnit(),ref.unit()))throw invalid("包装数量单位与物料库不一致");
  return new ProcessPlan.StepMaterial(m.id(),m.sequence(),m.materialRole(),ref.code(),ref.name(),m.materialState(),m.weightKg(),
   intermediate?null:m.formulaMaterialId(),m.remark(),m.sourceType(),intermediate?m.sourceStepOutputId():null,m.additionTiming(),m.additionMethod(),
   ref.catalogMaterialId(),ref.specification(),ref.unit(),false,
   intermediate||ref.catalogMaterialId()!=null?null:m.temporaryMaterialId(),
   intermediate||ref.catalogMaterialId()!=null?null:m.temporaryClassificationId(),
   intermediate||ref.catalogMaterialId()!=null?null:m.temporarySpecification(),
   packaging?m.quantity():null,packaging?(ref.catalogMaterialId()!=null?ref.unit():m.quantityUnit()):null);
 }
 private Reference resolveStepReference(ProcessPlan.StepMaterial m,ProcessPlan.StepMaterial persisted){
  if(!"PACKAGING".equals(m.materialRole()))return resolveForSave(reference(m),persisted==null?null:reference(persisted));
  var incoming=reference(m);
  if(incoming.catalogMaterialId()==null||incoming.catalogMaterialId().isBlank())return incoming;
  if(persisted!=null&&Objects.equals(m.catalogMaterialId(),persisted.catalogMaterialId()))return reference(persisted);
  var item=catalog.find(incoming.catalogMaterialId());requirePackaging(item);
  return new Reference(item.id(),item.code(),item.name(),item.specification(),item.unit());
 }
 private void requireStepReference(ProcessPlan.StepMaterial m){
  if(!"PACKAGING".equals(m.materialRole())){requireActiveReference(reference(m));return;}
  if(m.catalogMaterialId()==null||m.catalogMaterialId().isBlank())throw invalid("包装物尚未关联物料库");
  requirePackaging(catalog.find(m.catalogMaterialId()));
  if(m.quantity()==null||m.quantity().signum()<0||!Objects.equals(m.quantityUnit(),m.catalogUnit()))throw invalid("包装数量或单位待完善");
 }
 private void requirePackaging(MaterialCatalogItem item){
  if(!"ACTIVE".equals(item.status())||!"PACKAGING".equals(item.category()))throw invalid("请选择启用的包装物料");
 }
 public void requireActiveReferences(ProcessPlan plan){
  for(var major:values(plan.majorProcesses()))for(var step:values(major.steps()))for(var material:values(step.materials()))
   if(!"STEP_OUTPUT".equals(material.sourceType()))requireStepReference(material);
 }
 public List<com.lhr.rnd.model.ProcessSubmissionCheck.Issue> submissionIssues(ProcessPlan plan){
  var issues=new java.util.ArrayList<com.lhr.rnd.model.ProcessSubmissionCheck.Issue>();
  if(plan==null)return issues;
  for(var major:values(plan.majorProcesses()))for(var step:values(major.steps()))for(var material:values(step.materials())){
   if("STEP_OUTPUT".equals(material.sourceType()))continue;
   try {requireStepReference(material);}
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
