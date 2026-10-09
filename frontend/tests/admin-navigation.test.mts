import test from 'node:test';
import assert from 'node:assert/strict';
import {buildAdminNavigation,navigationSelection} from '../apps/pc/src/layouts/adminNavigation.ts';

test('role filtering removes empty groups but retains finance-only access',()=>{
 const paths=new Set(['/dashboard','/finance','/reports']);
 const nav=buildAdminNavigation(p=>paths.has(p),{materials:false,sops:false});
 assert.deepEqual(nav.map(n=>n.label),['工作台','成果文件']);
 assert.deepEqual(nav[1].children?.map(n=>n.key),['/finance','/reports']);
 assert.equal(nav.some(n=>n.children?.length===0),false);
});
test('server capabilities govern materials and SOP without exposing other file routes',()=>{
 const nav=buildAdminNavigation(p=>p==='/dashboard',{materials:true,sops:true});
 assert.deepEqual(nav.map(n=>n.label),['工作台','成果文件','基础资料']);
 assert.deepEqual(nav[1].children?.map(n=>n.key),['/sops']);
 assert.deepEqual(nav[2].children?.map(n=>n.key),['/materials']);
});
test('detail and legacy URLs highlight their consolidated destination',()=>{
 for(const [path,selectedKey,openKey] of [
  ['/demand/R1/edit','/demand/list','demand'],['/demand/new','/demand/list','demand'],
  ['/rnd/assign','/rnd/pool','rnd'],['/rnd/tasks/T1/experiment','/rnd/my-tasks','rnd'],
  ['/shipment/record','/shipment/list','shipment'],['/pricing/P1','/pricing/list','outputs'],
  ['/sops/amendments/S1','/sops','outputs'],['/settings/users','/settings/users','settings'],
  ['/settings/workflows','/settings','settings'],['/materials','/materials','reference'],
 ])assert.deepEqual(navigationSelection(path),{selectedKey,openKey});
});
test('menus contain business destinations rather than action and module-home entries',()=>{
 const nav=buildAdminNavigation(()=>true,{materials:true,sops:true});
 assert.deepEqual(nav.map(n=>n.label),['工作台','样品需求','研发管理','寄样管理','成果文件','基础资料','系统设置']);
 const keys=nav.flatMap(n=>n.children?.map(c=>c.key)??[n.key]);
 for(const removed of ['/demand/new','/demand','/rnd','/rnd/assign','/shipment','/shipment/record','/flowchart'])assert.equal(keys.includes(removed),false);
 assert.equal(keys.filter(k=>k==='/settings').length,1);
});
