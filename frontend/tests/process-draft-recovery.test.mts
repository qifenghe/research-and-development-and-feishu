import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import { recoverProcessDraft, hasLayeredProcessData, templateProcessCode } from '../apps/pc/src/components/process/processDraftRecovery.ts';
import {createEmptyProcessPlan, normalizeProcessPlan} from '../packages/shared/src/process-plan.ts';

function plan(versionNo: number, name = '熟制') {
  return normalizeProcessPlan({...createEmptyProcessPlan(), versionNo, majorProcesses:[{key:'m', sequence:1, processName:name, yieldBasis:'PRIMARY_INPUT', inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'煮制',stepType:'NORMAL',materials:[],outputs:[],controlPoints:[]}]}]});
}
test('refresh keeps authoritative version when only cached metadata is stale',()=>{
  const cached = {...plan(12),legacy:true};
  const server = {...plan(14),legacy:false};
  assert.equal(recoverProcessDraft(server,cached),'server');
});
test('different stale content requires user choice, never a silent overwrite',()=>{
  assert.equal(recoverProcessDraft(plan(14),plan(12,'本地未同步修改')),'conflict');
});
test('same-version unsaved edits can be restored without changing the lock version',()=>{
  assert.equal(recoverProcessDraft(plan(14),plan(14,'本地修改')),'local');
});
test('formal server revision cannot be replaced by an old draft',()=>{
  assert.equal(recoverProcessDraft({...plan(14),status:'SUBMITTED'},plan(14,'本地修改')),'conflict');
});
test('layered steps become the recipe source even when loaded from a legacy shell',()=>{
  assert.equal(hasLayeredProcessData({...plan(1),legacy:true}),true);
  assert.equal(hasLayeredProcessData(createEmptyProcessPlan()),false);
});
test('repeated default templates carry the same nonempty comparison identity',()=>{
  const names=['原辅料准备','解冻与净制','分切与规格化','调味与腌制','热加工','冷却与冻结','分装与包装','检测与入库'];
  const codes=names.map(templateProcessCode);
  assert.ok(codes.every(code=>code.length>0));
  assert.equal(new Set(codes).size,8);
  assert.equal(templateProcessCode('热加工'),codes[4]);
  assert.equal(templateProcessCode('自定义工序'),'');
});

test('conflict recovery locks duplicate requests and all editable form fields',()=>{
  const source=readFileSync(new URL('../apps/pc/src/views/rnd/ExperimentFormView.vue',import.meta.url),'utf8');
  const template=source.split('<script setup')[0];
  assert.ok(source.includes('!formId || resolvingProcessConflict.value) return;'));
  assert.ok(source.includes('processDraftConflict.value !== candidate'));
  assert.ok(!template.includes(':disabled="readOnly"'));
  assert.ok(!template.includes(':readonly="readOnly"'));
  assert.ok(!template.includes('v-if="!readOnly'));
});
