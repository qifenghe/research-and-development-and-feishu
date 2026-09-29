import test from 'node:test';
import assert from 'node:assert/strict';
import {classificationOptions} from '../packages/shared/src/material-catalog.ts';
test('disabled parent excludes child from new assignment but can be shown for browsing',()=>{
 const tree=[{id:'r',name:'原料',parentId:null,businessType:'RAW',sortOrder:0,status:'INACTIVE',version:0},{id:'b',name:'牛',parentId:'r',businessType:'RAW',sortOrder:0,status:'ACTIVE',version:0}];
 assert.deepEqual(classificationOptions(tree,true),[]);
 assert.deepEqual(classificationOptions(tree,false).map(x=>x.value),['r','b']);
});
