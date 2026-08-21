import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

test("PC and mobile flush the current experiment draft before every test handoff", () => {
  const pc = fs.readFileSync(new URL("../apps/pc/src/views/rnd/ExperimentFormView.vue", import.meta.url), "utf8");
  const mobile = fs.readFileSync(new URL("../apps/mobile/src/views/ExperimentFormView.vue", import.meta.url), "utf8");
  const pcDetail = fs.readFileSync(new URL("../apps/pc/src/views/rnd/TaskDetailView.vue", import.meta.url), "utf8");
  const mobileDetail = fs.readFileSync(new URL("../apps/mobile/src/views/TaskDetailView.vue", import.meta.url), "utf8");

  assert.match(pc, /const flushed = await saveDraft\(\{ silent: true \}\)/);
  assert.match(pc, /if \(!flushed\) return/);
  assert.match(mobile, /const flushed=await saveDraft\(\{silent:true,context,payload\}\)/);
  assert.match(mobile, /if\(!isActionContextCurrent\(context\)\|\|!flushed\)return/);
  for (const source of [pc, mobile, pcDetail, mobileDetail]) {
    assert.match(source, /submitExperimentForTest\(experimentId,\s*"AUTO_ASSIGN"\)/);
    assert.doesNotMatch(source, /submitExperimentForTest\(experimentId,\s*(auth\.displayName|context\.operatorName)\)/);
  }
});

test("only the owner engineer or a director can edit and hand off an experiment", () => {
  const policy = fs.readFileSync(new URL("../packages/shared/src/detail-fields.ts", import.meta.url), "utf8");
  const editPolicy = policy.match(/export function canEditExperiment[\s\S]*?\n}/)?.[0] || "";
  const notifyPolicy = policy.match(/export function canNotifyInternalTest[\s\S]*?\n}/)?.[0] || "";
  assert.match(editPolicy, /role === "RND_DIRECTOR"/);
  assert.match(editPolicy, /role === "RND_ENGINEER"/);
  assert.doesNotMatch(editPolicy, /\["RND_ENGINEER", "RND_DIRECTOR", "RND"\]/);
  assert.doesNotMatch(notifyPolicy, /role === "RND_ASSISTANT"/);
  assert.match(notifyPolicy, /canEditExperiment/);

  const pcForm = fs.readFileSync(new URL("../apps/pc/src/views/rnd/ExperimentFormView.vue", import.meta.url), "utf8");
  const pcDetail = fs.readFileSync(new URL("../apps/pc/src/views/rnd/TaskDetailView.vue", import.meta.url), "utf8");
  const pcWorkflow = fs.readFileSync(new URL("../apps/pc/src/components/TaskWorkflowPanel.vue", import.meta.url), "utf8");
  const mobileDetail = fs.readFileSync(new URL("../apps/mobile/src/views/TaskDetailView.vue", import.meta.url), "utf8");
  for (const source of [pcForm, pcDetail, pcWorkflow, mobileDetail]) {
    assert.doesNotMatch(source, /内勤(?:可|无需|代为|.*通知内部测试|.*通知测试)/);
  }
});
