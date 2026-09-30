import type { ProcessPlanDraft } from './process-plan';
import { mergeMaterialAcknowledgement } from './material-catalog.ts';

/** Match server rows to the submitted structure, then to current client keys, not current positions. */
export function mergeProcessMaterialAcknowledgements(local:ProcessPlanDraft,sent:ProcessPlanDraft,ack:ProcessPlanDraft):ProcessPlanDraft {
 const pairs=new Map<string,{sent:typeof sent.majorProcesses[number]['steps'][number]['materials'][number];ack:typeof sent.majorProcesses[number]['steps'][number]['materials'][number]}>();
 for(const major of sent.majorProcesses){
  const savedMajor=ack.majorProcesses.find(x=>x.sequence===major.sequence);
  for(const step of major.steps){
   const savedStep=savedMajor?.steps.find(x=>x.sequence===step.sequence);
   for(const material of step.materials){
    const saved=savedStep?.materials.find(x=>x.sequence===material.sequence);
    if(saved)pairs.set(material.key,{sent:material,ack:saved});
   }
  }
 }
 return {...local,majorProcesses:local.majorProcesses.map(major=>({...major,steps:major.steps.map(step=>({...step,materials:step.materials.map(material=>{
  const pair=pairs.get(material.key);return pair?mergeMaterialAcknowledgement(material,pair.sent,pair.ack):material;
 })}))}))};
}
