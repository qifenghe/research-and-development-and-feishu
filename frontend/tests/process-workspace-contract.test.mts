import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const root = new URL("../apps/pc/src/", import.meta.url);
const source = (path: string) => fs.readFileSync(new URL(path, root), "utf8");

test("experiment form makes the major-process workspace the primary process editor", () => {
  const form = source("views/rnd/ExperimentFormView.vue");
  assert.match(form, /ProcessPlanWorkspace/);
  assert.match(form, /历史兼容数据/);
  assert.ok(!form.includes("<ProcessHierarchyEditor v-model=\"processPlan\""));
});

test("workspace composes the major board, minor editor, draft controls, versions and output center", () => {
  const workspace = source("components/process/ProcessPlanWorkspace.vue");
  for (const marker of [
    "MajorProcessBoard", "MinorStepWorkspace", "ProcessSubmitDialog", "RndOutputCenter",
    "保存草稿", "正式提交", "版本历史", "calculateBatchYield", "saveProcessPlan",
  ]) assert.ok(workspace.includes(marker), `missing ${marker}`);
  assert.match(workspace, /v-model:selected-revision-id/);
  assert.match(workspace, /selectedRevision\.snapshot/);
  assert.match(workspace, /aggregateProcessRecipe/);
});

test("major and minor workspaces expose native drag, clone, prior-output flow and KCP editing", () => {
  const board = source("components/process/MajorProcessBoard.vue");
  const minor = source("components/process/MinorStepWorkspace.vue");
  const controls = source("components/process/ControlPointEditor.vue");
  for (const marker of ["draggable", "dragstart", "drop", "复制", "nextProcessKey"]) assert.ok(board.includes(marker), `major ${marker}`);
  for (const marker of ["STEP_OUTPUT", "previousOutputs", "不进入物料库", "primaryOutput", "draggable", "复制"]) assert.ok(minor.includes(marker), `minor ${marker}`);
  for (const marker of ["measurements", "CRITICAL", "confirmedBy", "deviationAction", "resolved"]) assert.ok(controls.includes(marker), `control ${marker}`);
  for (const component of [board, minor]) {
    assert.match(component, /aria-label/);
    assert.match(component, /focus-visible/);
    assert.match(component, /上移/);
    assert.match(component, /下移/);
  }
  assert.match(minor, /modelValue:\s*ProcessPlanDraft/);
  assert.match(minor, /majorKey:\s*string/);
  assert.match(controls, /cloneVueValue/);
});

test("route loader and output center fence stale requests and reset controlled selection", () => {
  const form = source("views/rnd/ExperimentFormView.vue");
  const output = source("components/process/RndOutputCenter.vue");
  assert.match(form, /watch\(\(\) => route\.params\.id/);
  assert.match(form, /resetAndLoad/);
  assert.match(form, /requestGeneration\.isCurrent/);
  assert.match(output, /selectedRevisionId\?:\s*string/);
  assert.match(output, /update:selectedRevisionId/);
  assert.match(output, /listGeneration\.isCurrent/);
  assert.match(output, /mutationGeneration\.isCurrent/);
});

test("experiment parent snapshots Vue process state through the shared raw clone boundary", () => {
  const form = source("views/rnd/ExperimentFormView.vue");
  assert.match(form, /cloneVueValue/);
  assert.match(form, /processPlan:\s*cloneVueValue\(processPlan\.value\)/);
  assert.ok(!form.includes("structuredClone(processPlan.value)"));
});

test("submission requires explicit confirmation/reason and output center never offers pricing generation", () => {
  const submit = source("components/process/ProcessSubmitDialog.vue");
  const output = source("components/process/RndOutputCenter.vue");
  for (const marker of ["getProcessSubmissionCheck", "confirmed", "changeReason", "revisionNo"]) assert.ok(submit.includes(marker), `submit ${marker}`);
  assert.match(output, /等待包装确认后由现有核价流程生成/);
  assert.ok(!/generate.*pricing/i.test(output));
  const taskApi = fs.readFileSync(new URL("../packages/shared/src/api/task.ts", import.meta.url), "utf8");
  assert.match(taskApi, /getProcessSubmissionCheck/);
  assert.match(taskApi, /submission-check/);
});
