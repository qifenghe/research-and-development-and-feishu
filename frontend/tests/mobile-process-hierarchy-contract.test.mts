import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

test("mobile process hierarchy is read-only and shows formal flow controls and yields",()=>{
  const source=fs.readFileSync(new URL("../apps/mobile/src/components/ProcessHierarchyReadonly.vue",import.meta.url),"utf8");
  for(const marker of ["手机端","成品得率","大工序得率","中间流转","关键控制点","outputs","controlPoints","sourceStepOutputId","materials","parameter1Value"]){assert.ok(source.includes(marker),`missing ${marker}`)}
  assert.ok(!source.includes("v-model=\"major"));
  for(const forbidden of ["draggable","@drop","@dragstart","添加工序","正式提交","实测值"]){assert.ok(!source.includes(forbidden),`read-only mobile must not expose ${forbidden}`)}
});

test("mobile experiment form chooses draft for R&D and formal revision outputs for read-only roles",()=>{
  const source=fs.readFileSync(new URL("../apps/mobile/src/views/ExperimentFormView.vue",import.meta.url),"utf8");
  assert.ok(source.includes("api.task.getProcessPlan"));
  for(const marker of ["api.task.getProcessRevisions","api.task.getProcessRevision","api.task.getProcessArtifacts","api.report.downloadProcessArtifact","正式工艺 V","标准配方","研发版 SOP"]){assert.ok(source.includes(marker),`missing ${marker}`)}
  assert.ok(source.includes("ProcessHierarchyReadonly"));
  assert.ok(source.includes('v-model="yieldCalculationMode" :disabled="readOnly"'),"read-only viewers must not change yield mode locally");
  for(const forbidden of ["generateProcessArtifact","submitProcessPlan","saveProcessPlan","createDraftFromRevision"]){assert.ok(!source.includes(forbidden),`mobile must not expose ${forbidden}`)}
});
