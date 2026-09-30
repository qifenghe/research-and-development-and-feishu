import test from 'node:test';
import assert from 'node:assert/strict';
import {mergeProcessMaterialAcknowledgements} from '../packages/shared/src/material-save-ack.ts';
import {ProcessPlanSaveCoordinator} from '../apps/pc/src/components/process/processPlanAutosave.ts';
const plan=(rows:any[])=>({status:'DRAFT',versionNo:1,majorProcesses:[{sequence:1,steps:[{sequence:1,materials:rows}]}]}) as any;
test('ack matches submitted keys across reorder/delete/add without reverting weights',()=>{
 const a={key:'a',sequence:1,id:'old-a',materialName:'A'};
 const b={key:'b',sequence:2,id:'old-b',materialName:'B'};
 const local=plan([{...b,sequence:1,weightKg:12},{key:'c',sequence:2,materialName:'C'}]);
 const ack=plan([{...a,id:'new-a'},{...b,id:'new-b'}]);
 const merged=mergeProcessMaterialAcknowledgements(local,plan([a,b]),ack).majorProcesses[0].steps[0].materials;
 assert.equal(merged[0].id,'new-b');assert.equal(merged[0].weightKg,12);assert.equal(merged[1].id,undefined);
});
test('continuous autosave uses acknowledged row IDs on the next request',async()=>{
 let resolve!:(v:any)=>void;const calls:any[]=[];
 const coordinator=new ProcessPlanSaveCoordinator<any>({formId:'f',debounceMs:60000,isDraft:()=>true,
  mergeAck:(local,ack,sent)=>mergeProcessMaterialAcknowledgements(local,sent,ack),
  save:async(_id,p)=>{calls.push(p);return calls.length===1?new Promise(r=>resolve=r):p;}});
 coordinator.hydrate(plan([{key:'a',sequence:1,materialName:'A',weightKg:10}]));
 coordinator.edit(p=>p);const pending=coordinator.flush();
 coordinator.edit(p=>({...p,majorProcesses:[{sequence:1,steps:[{sequence:1,materials:[{...p.majorProcesses[0].steps[0].materials[0],weightKg:12}]}]}]}));
 resolve(plan([{key:'server',id:'server-id',sequence:1,materialName:'A',weightKg:10}]));
 await pending;assert.equal(calls[1].majorProcesses[0].steps[0].materials[0].id,'server-id');
 assert.equal(calls[1].majorProcesses[0].steps[0].materials[0].weightKg,12);coordinator.dispose();
});
