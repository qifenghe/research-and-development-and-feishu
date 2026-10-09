import {nextProcessKey,type ProcessPlanDraft} from './process-plan.ts';
export function initializeTemporaryDefinitions(plan:ProcessPlanDraft):ProcessPlanDraft{
 return {...plan,majorProcesses:plan.majorProcesses.map(m=>({...m,steps:m.steps.map(s=>({...s,materials:s.materials.map(r=>r.sourceType!=='STEP_OUTPUT'&&!r.catalogMaterialId&&!r.temporaryMaterialId?{...r,temporaryMaterialId:nextProcessKey('tmp')}:{...r})}))}))};
}
export function availableTemporaryGroups(groups:TemporaryMaterialGroup[],pendingIds:string[]):TemporaryMaterialGroup[]{const pending=new Set(pendingIds);return groups.filter(g=>!pending.has(g.temporaryMaterialId));}
export interface TemporaryMaterialGroup {temporaryMaterialId:string;name:string;classificationId?:string;specification?:string;rowKeys:string[];paths:string[];totalWeightKg?:number;quantity?:number;quantityUnit?:string;materialRole?:string}
export function collectTemporaryMaterials(plan:ProcessPlanDraft): TemporaryMaterialGroup[]{
 const groups=new Map<string,TemporaryMaterialGroup>();
 for(const major of plan.majorProcesses)for(const step of major.steps)for(const row of step.materials){
  if(row.sourceType==='STEP_OUTPUT'||row.catalogMaterialId||!row.temporaryMaterialId)continue;
  const packaging=row.materialRole==='PACKAGING';
  const item=groups.get(row.temporaryMaterialId)??{temporaryMaterialId:row.temporaryMaterialId,name:row.materialName,classificationId:row.temporaryClassificationId,specification:row.temporarySpecification,rowKeys:[],paths:[],...(packaging?{quantity:0,quantityUnit:row.quantityUnit,materialRole:'PACKAGING'}:{totalWeightKg:0})};
  item.rowKeys.push(row.id||row.key);item.paths.push(`${major.processName} / ${step.stepName}`);
  if(packaging)item.quantity=(item.quantity??0)+(row.quantity??0);else item.totalWeightKg=(item.totalWeightKg??0)+(row.weightKg??0);groups.set(row.temporaryMaterialId,item);
 }
 return [...groups.values()];
}
