import assert from "node:assert/strict";
import test from "node:test";
import { ProcessPlanSaveCoordinator } from "../apps/pc/src/components/process/processPlanAutosave.ts";

type Draft = { versionNo: number; status: "DRAFT" | "SUBMITTED"; graph: string };

test("keeps an edit made while a save is in flight and persists one follow-up", async () => {
  let resolveFirst!: (value: Draft) => void;
  const calls: Draft[] = [];
  const coordinator = new ProcessPlanSaveCoordinator<Draft>({
    debounceMs: 0,
    isDraft: plan => plan.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo, status: ack.status }),
    save: plan => {
      calls.push(plan);
      if (calls.length === 1) return new Promise(resolve => { resolveFirst = resolve; });
      return Promise.resolve({ ...plan, versionNo: 2 });
    },
  });
  coordinator.hydrate({ versionNo: 0, status: "DRAFT", graph: "A" });
  coordinator.edit(plan => ({ ...plan, graph: "B" }));
  const first = coordinator.flush();
  coordinator.edit(plan => ({ ...plan, graph: "C" }));
  resolveFirst({ versionNo: 1, status: "DRAFT", graph: "B" });
  await first;
  await coordinator.flush();
  assert.deepEqual(calls.map(call => call.graph), ["B", "C"]);
  assert.deepEqual(coordinator.value, { versionNo: 2, status: "DRAFT", graph: "C" });
});

test("hydration, late acknowledgements and submitted drafts never schedule a write", async () => {
  const calls: Draft[] = [];
  const coordinator = new ProcessPlanSaveCoordinator<Draft>({
    debounceMs: 0,
    isDraft: plan => plan.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo, status: ack.status }),
    save: async plan => { calls.push(plan); return { ...plan, versionNo: plan.versionNo + 1 }; },
  });
  coordinator.hydrate({ versionNo: 7, status: "DRAFT", graph: "server" });
  await coordinator.flush();
  coordinator.hydrate({ versionNo: 8, status: "SUBMITTED", graph: "formal" });
  coordinator.edit(plan => ({ ...plan, graph: "must-not-save" }));
  await coordinator.flush();
  coordinator.dispose();
  assert.equal(calls.length, 0);
});
