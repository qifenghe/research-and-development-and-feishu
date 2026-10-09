import test from 'node:test';
import assert from 'node:assert/strict';
import { collectTemporaryMaterials,initializeTemporaryDefinitions,availableTemporaryGroups } from '../packages/shared/src/temporary-materials.ts';
import { aggregateProcessRecipe,createEmptyProcessPlan } from '../packages/shared/src/process-plan.ts';
test('temporary packaging quantities stay separate from food kilograms',()=>{
 const plan=createEmptyProcessPlan();plan.majorProcesses=[{key:'m',sequence:1,processName:'包装',yieldBasis:'NONE',inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'包装',stepType:'NORMAL',outputs:[],controlPoints:[],materials:[
 {key:'a',sequence:1,materialRole:'PACKAGING',materialName:'袋',materialState:'SOLID',sourceType:'EXTERNAL',temporaryMaterialId:'TMP-A',quantity:277,quantityUnit:'个'},
 {key:'b',sequence:2,materialRole:'PACKAGING',materialName:'胶带',materialState:'SOLID',sourceType:'EXTERNAL',temporaryMaterialId:'TMP-B',quantity:19,quantityUnit:'米'}]}]}];
 const groups:any[]=collectTemporaryMaterials(plan);
 assert.deepEqual(groups.map(g=>[g.quantity,g.quantityUnit,g.totalWeightKg]),[[277,'个',undefined],[19,'米',undefined]]);
 assert.deepEqual(aggregateProcessRecipe(plan),[]);
});
test('temporary identity separates same-name specs and excludes intermediate reuse',()=>{
 const plan=createEmptyProcessPlan();
 plan.majorProcesses=[{key:'major',sequence:1,processName:'试验',yieldBasis:'PRIMARY_INPUT',inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'配料',stepType:'NORMAL',outputs:[],controlPoints:[],materials:[
 {key:'a',sequence:1,materialRole:'PRIMARY',materialName:'牛肉',materialState:'SOLID',sourceType:'EXTERNAL',temporaryMaterialId:'TMP-1',temporarySpecification:'20mm',weightKg:2},
 {key:'b',sequence:2,materialRole:'PRIMARY',materialName:'牛肉',materialState:'SOLID',sourceType:'EXTERNAL',temporaryMaterialId:'TMP-1',temporarySpecification:'20mm',weightKg:3},
 {key:'c',sequence:3,materialRole:'AUXILIARY',materialName:'牛肉',materialState:'SOLID',sourceType:'EXTERNAL',temporaryMaterialId:'TMP-2',temporarySpecification:'30mm',weightKg:4},
 {key:'d',sequence:4,materialRole:'PRIMARY',materialName:'牛肉',materialState:'SOLID',sourceType:'STEP_OUTPUT',weightKg:5},
 ]}]}];
 assert.deepEqual(collectTemporaryMaterials(plan).map(x=>[x.temporaryMaterialId,x.totalWeightKg]),[['TMP-1',5],['TMP-2',4]]);
 assert.deepEqual(aggregateProcessRecipe(plan).map(x=>x.weightKg),[5,4]);
 assert.deepEqual(availableTemporaryGroups(collectTemporaryMaterials(plan),['TMP-1']).map(x=>x.temporaryMaterialId),['TMP-2']);
});
test('legacy rows get separate stable identities without mutating or merging by name',()=>{
 const plan=createEmptyProcessPlan();plan.majorProcesses=[{key:'m',sequence:1,processName:'准备',yieldBasis:'PRIMARY_INPUT',inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'领料',stepType:'NORMAL',outputs:[],controlPoints:[],materials:[{key:'a',sequence:1,sourceType:'EXTERNAL',materialName:'牛肉',materialRole:'PRIMARY',materialState:'SOLID'},{key:'b',sequence:2,sourceType:'EXTERNAL',materialName:'牛肉',materialRole:'AUXILIARY',materialState:'SOLID'}]}]}];
 const normalized=initializeTemporaryDefinitions(plan);const rows=normalized.majorProcesses[0].steps[0].materials;
 assert.ok(rows[0].temporaryMaterialId);assert.notEqual(rows[0].temporaryMaterialId,rows[1].temporaryMaterialId);
 assert.equal(plan.majorProcesses[0].steps[0].materials[0].temporaryMaterialId,undefined);
 assert.equal(initializeTemporaryDefinitions(normalized).majorProcesses[0].steps[0].materials[0].temporaryMaterialId,rows[0].temporaryMaterialId);
});
