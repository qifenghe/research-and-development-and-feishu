import assert from "node:assert/strict";
import test from "node:test";
import { watch } from "../apps/pc/node_modules/vue/index.mjs";
import { createProcessWorkspaceHarnessRouter } from "../apps/pc/src/processWorkspaceHarnessRouter.ts";
import { experimentDraftKey } from "../packages/shared/src/experiment/draft-cache.ts";

test("the parent harness binds initial A and subsequent B loads, cache writes and autosaves to route id", async () => {
  const router = createProcessWorkspaceHarnessRouter();
  await router.push("/rnd/tasks/task-a/experiment");

  const loads: string[] = [];
  const cacheWrites: string[] = [];
  const autosaves: string[] = [];
  let activeTaskId = "";
  const stop = watch(() => router.currentRoute.value.params.id, taskId => {
    if (activeTaskId) cacheWrites.push(experimentDraftKey("engineer-1", activeTaskId));
    activeTaskId = String(taskId);
    loads.push(activeTaskId);
  }, { immediate: true, flush: "sync" });
  const editCurrentTask = () => {
    cacheWrites.push(experimentDraftKey("engineer-1", activeTaskId));
    autosaves.push(activeTaskId);
  };

  assert.equal(router.currentRoute.value.params.id, "task-a");
  assert.equal(router.currentRoute.value.name, "process-workspace-experiment");
  assert.deepEqual(loads, ["task-a"]);
  editCurrentTask();
  assert.deepEqual(cacheWrites, ["rnd:experiment-draft:v1:engineer-1:task-a"]);
  assert.deepEqual(autosaves, ["task-a"]);

  await router.push("/rnd/tasks/task-b/experiment");
  assert.equal(router.currentRoute.value.params.id, "task-b");
  assert.equal(router.currentRoute.value.name, "process-workspace-experiment");
  assert.deepEqual(loads, ["task-a", "task-b"]);
  assert.equal(cacheWrites.at(-1), "rnd:experiment-draft:v1:engineer-1:task-a");
  editCurrentTask();
  assert.equal(cacheWrites.at(-1), "rnd:experiment-draft:v1:engineer-1:task-b");
  assert.deepEqual(autosaves, ["task-a", "task-b"]);
  stop();
});
