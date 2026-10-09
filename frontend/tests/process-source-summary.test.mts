import test from 'node:test';
import assert from 'node:assert/strict';
import * as summaries from '../apps/pc/src/components/process/actualSummary.ts';
import {createEmptyProcessPlan} from '../packages/shared/src/process-plan.ts';
function fixture():any{const plan=createEmptyProcessPlan();plan.majorProcesses=[{key:'m',sequence:1,processName:'准备',yieldBasis:'PRIMARY_INPUT',inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'配料',stepType:'NORMAL',controlPoints:[],materials:[
 {key:'food',sequence:1,materialRole:'PRIMARY',sourceType:'EXTERNAL',materialName:'肉',weightKg:150},
 {key:'bag',sequence:2,materialRole:'PACKAGING',sourceType:'EXTERNAL',materialName:'袋',quantity:277,quantityUnit:'个'},
 {key:'reuse',sequence:3,materialRole:'PRIMARY',sourceType:'STEP_OUTPUT',materialName:'肉',weightKg:145,sourceStepOutputId:'intermediate'}],outputs:[
 {id:'intermediate',key:'intermediate',sequence:1,outputType:'INTERMEDIATE',outputName:'肉',weightKg:145,continueFlow:true,primaryOutput:true},
 {id:'finished',key:'finished',sequence:2,outputType:'FINISHED',outputName:'成品',weightKg:138.5,continueFlow:false,primaryOutput:true}]}]}];return plan;}
test('food total excludes packaging counts and intermediate reuse',()=>{assert.equal(summaries.externalActualTotal(fixture()),150);});
test('missing food weight is unavailable instead of real zero',()=>{const p=fixture();delete p.majorProcesses[0].steps[0].materials[0].weightKg;assert.equal(summaries.externalActualTotal(p),null);});
test('terminal finished weight excludes intermediate and consumed finished nodes',()=>{
 const p=fixture(),fn=(summaries as any).summarizeProcessSource;assert.equal(typeof fn,'function');
 assert.equal(fn(p).finishedWeightKg,138.5);assert.equal(fn(p).foodInputKg,150);
 p.majorProcesses[0].steps[0].outputs[0].outputType='FINISHED';assert.equal(fn(p).finishedWeightKg,138.5);
 p.majorProcesses[0].steps[0].outputs[1].continueFlow=true;assert.equal(fn(p).finishedWeightKg,undefined);
});
test('separate external additions accumulate but package units never mix',()=>{
 const p=fixture(),fn=(summaries as any).summarizeProcessSource;assert.equal(typeof fn,'function');
 p.majorProcesses[0].steps[0].materials.push({key:'more',materialRole:'AUXILIARY',sourceType:'EXTERNAL',materialName:'盐',weightKg:2});
 assert.equal(fn(p).foodInputKg,152);assert.equal(fn(p).packaging[0].quantity,277);assert.equal(fn(p).packaging[0].unit,'个');
});
