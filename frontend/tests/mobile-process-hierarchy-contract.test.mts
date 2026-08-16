import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

test("mobile process hierarchy is read-only and shows yield materials and parameters",()=>{
  const source=fs.readFileSync(new URL("../apps/mobile/src/components/ProcessHierarchyReadonly.vue",import.meta.url),"utf8");
  for(const marker of ["手机端","整批得率","平衡差","materials","parameter1Value"]){assert.ok(source.includes(marker),`missing ${marker}`)}
  assert.ok(!source.includes("v-model=\"major"));
});

test("mobile experiment form loads process plan",()=>{
  const source=fs.readFileSync(new URL("../apps/mobile/src/views/ExperimentFormView.vue",import.meta.url),"utf8");
  assert.ok(source.includes("api.task.getProcessPlan"));
  assert.ok(source.includes("ProcessHierarchyReadonly"));
});
