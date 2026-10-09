import test from 'node:test';
import assert from 'node:assert/strict';
import {prepareSopSnapshot,sopDraftDifferences} from '../apps/pc/src/services/sopAmendmentDraft.ts';

test('new SOP entities receive stable IDs without changing original snapshot',()=>{
 const original={majorProcesses:[{key:'M',steps:[{key:'S',materials:[{key:'I'}],outputs:[{key:'O'}],controlPoints:[],parameters:[{key:'P',actualValue:'1'}]}],inputs:[],outputs:[]}]};
 const result=prepareSopSnapshot(original);
 assert.equal(result.majorProcesses[0].id,'M');
 assert.equal(result.majorProcesses[0].steps[0].materials[0].id,'I');
 assert.equal(result.majorProcesses[0].steps[0].outputs[0].id,'O');
 assert.equal('id' in original.majorProcesses[0],false);
 assert.deepEqual(prepareSopSnapshot(result),result);
});
test('SOP changes identify only changed fields and distinguish absent weight from zero',()=>{
 const source={majorProcesses:[{id:'M',sequence:1,steps:[{id:'S',sequence:1,instruction:'煮制',parameters:[{key:'P',valueType:'NUMBER',actualValue:'1'}],outputs:[{id:'O',weightKg:null}]}]}]};
 const draft=structuredClone(source);draft.majorProcesses[0].steps[0].instruction='沥水5分钟';
 draft.majorProcesses[0].steps[0].parameters[0].actualValue='1.0';
 (draft.majorProcesses[0].steps[0].outputs[0] as any).weightKg=0;
 const differences=sopDraftDifferences(source,draft);
 assert.deepEqual(differences.map(d=>[d.entityId,d.fieldPath]),[['S','/majorProcesses/M/steps/S/instruction'],['O','/majorProcesses/M/steps/S/outputs/O/weightKg']]);
});
test('deleted steps retain their previous content for red comparison',()=>{
 const before={majorProcesses:[{id:'M',steps:[{id:'S',instruction:'原操作'}]}]};
 const after={majorProcesses:[{id:'M',steps:[]}]};
 const [difference]=sopDraftDifferences(before,after);
 assert.equal(difference.type,'REMOVED');assert.equal((difference.before as any).instruction,'原操作');
});
