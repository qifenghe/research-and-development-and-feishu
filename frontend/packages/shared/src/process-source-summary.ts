import type {ProcessPlanDraft} from './process-plan.ts';
export function summarizeProcessSource(plan:ProcessPlanDraft):{foodInputKg?:number;finishedWeightKg?:number;packaging:{materialKey:string;name:string;quantity?:number;unit:string}[];incomplete:boolean}{
 const materials=plan.majorProcesses.flatMap(m=>m.steps.flatMap(s=>s.materials));
 const external=materials.filter(m=>m.sourceType!=='STEP_OUTPUT');
 const foods=external.filter(m=>m.materialRole!=='PACKAGING');
 const valid=(n:number|undefined)=>n!=null&&Number.isFinite(n)&&n>=0;
 const foodInputKg=foods.length&&foods.every(m=>valid(m.weightKg))?foods.reduce((n,m)=>n+m.weightKg!,0):undefined;
 const consumed=new Set(materials.filter(m=>m.sourceType==='STEP_OUTPUT').map(m=>m.sourceStepOutputId));
 const finished=plan.majorProcesses.flatMap(m=>m.steps.flatMap(s=>s.outputs||[])).filter(o=>o.outputType==='FINISHED'&&!o.continueFlow&&!consumed.has(o.id||o.key));
 const ids=finished.map(o=>o.id||o.key),duplicate=ids.some((id,i)=>ids.indexOf(id)!==i);
 const finishedWeightKg=finished.length&&!duplicate&&finished.every(o=>valid(o.weightKg))?finished.reduce((n,o)=>n+o.weightKg!,0):undefined;
 const groups=new Map<string,{materialKey:string;name:string;quantity?:number;unit:string}>();
 for(const row of external.filter(m=>m.materialRole==='PACKAGING')){
  const unit=row.quantityUnit||row.catalogUnit||'',key=`${row.catalogMaterialId||row.temporaryMaterialId||row.id||row.key}:${unit}`;
  const group=groups.get(key);
  if(group)group.quantity=group.quantity!=null&&valid(row.quantity)?group.quantity+row.quantity!:undefined;
  else groups.set(key,{materialKey:key,name:row.materialName,quantity:valid(row.quantity)?row.quantity:undefined,unit});
 }
 const packaging=[...groups.values()];
 return {foodInputKg,finishedWeightKg,packaging,incomplete:foodInputKg==null||finishedWeightKg==null||packaging.some(p=>p.quantity==null||!p.unit)};
}
