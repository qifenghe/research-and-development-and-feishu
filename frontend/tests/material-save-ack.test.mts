import test from 'node:test';
import assert from 'node:assert/strict';
import {mergeMaterialAcknowledgement,selectCatalogMaterial,loadIngredientOptions} from '../packages/shared/src/material-catalog.ts';
const item={id:'MC1',code:'BEEF',name:'牛肉',category:'RAW',specification:'500g',unit:'kg',status:'ACTIVE',version:0};
test('ack adopts row identity and consumes refresh without overwriting weight edits',()=>{
 const sent=selectCatalogMaterial({key:'client',weightKg:10},item);
 const ack={...sent,id:'server',refreshCatalogSnapshot:false};
 const merged=mergeMaterialAcknowledgement({...sent,weightKg:12},sent,ack);
 assert.equal(merged.id,'server');assert.equal(merged.weightKg,12);assert.equal(merged.refreshCatalogSnapshot,false);
});
test('selection made during save remains pending even when reselecting same catalog item',()=>{
 const sent=selectCatalogMaterial({id:'old',key:'client'},item);
 const local=selectCatalogMaterial(sent,item);
 const merged=mergeMaterialAcknowledgement(local,sent,{...sent,id:'server',refreshCatalogSnapshot:false});
 assert.equal(merged.id,'server');assert.equal(merged.refreshCatalogSnapshot,true);
});
test('material read remains usable if classifications are unauthorized',async()=>{
 const data={items:[item],total:1};
 const result=await loadIngredientOptions(async()=>data,async()=>{throw new Error('403');});
 assert.deepEqual(result.data,data);assert.deepEqual(result.categories,[]);
});
