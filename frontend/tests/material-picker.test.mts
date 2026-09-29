import test from 'node:test';
import assert from 'node:assert/strict';
import {selectCatalogMaterial, clearCatalogReference, materialLinkLabel, LatestMaterialQuery} from '../packages/shared/src/material-catalog.ts';
const item={id:'MC1',code:'BEEF',name:'牛肉',category:'RAW',specification:'500g',unit:'kg',status:'ACTIVE',version:0,classificationId:'CAT-BEEF'};
test('selection replaces identity and retains weight and operation fields',()=>{
 const selected=selectCatalogMaterial({id:'old-row',key:'row',materialName:'旧物料',weightKg:12,materialRole:'PRIMARY',additionTiming:'水沸后'},item);
 assert.equal(selected.materialName,'牛肉');assert.equal(selected.materialCode,'BEEF');assert.equal(selected.weightKg,12);assert.equal(selected.materialRole,'PRIMARY');assert.equal(selected.additionTiming,'水沸后');assert.equal(selected.id,'old-row');assert.equal(selected.catalogMaterialId,'MC1');assert.equal(selected.refreshCatalogSnapshot,true);
});
test('legacy material is labelled without losing original data',()=>{const old={materialName:'旧牛肉',weightKg:10};assert.equal(materialLinkLabel(old),'待关联物料');assert.equal(old.materialName,'旧牛肉');});
test('intermediate source clears catalog reference',()=>{const value=clearCatalogReference({...item,catalogMaterialId:'MC1',catalogSpecification:'规格',catalogUnit:'kg',refreshCatalogSnapshot:true});assert.equal(value.catalogMaterialId,undefined);assert.equal(value.catalogUnit,undefined);assert.equal(value.refreshCatalogSnapshot,false);});
test('late search and invalidated step cannot overwrite current results',async()=>{
 const gate=new LatestMaterialQuery();let finish:(v:string)=>void=()=>{};const shown:string[]=[];
 const slow=gate.run(()=>new Promise<string>(r=>finish=r),v=>shown.push(v));
 await gate.run(async()=>'新结果',v=>shown.push(v));finish('旧结果');await slow;assert.deepEqual(shown,['新结果']);
 const after=gate.run(async()=>'上一步',v=>shown.push(v));gate.invalidate();await after;assert.deepEqual(shown,['新结果']);
});
