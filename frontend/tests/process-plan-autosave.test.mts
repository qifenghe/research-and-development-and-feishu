import assert from "node:assert/strict";
import test from "node:test";
import { ProcessPlanSaveCoordinator } from "../apps/pc/src/components/process/processPlanAutosave.ts";

type Draft = { versionNo: number; status: "DRAFT" | "SUBMITTED"; graph: string };

test("keeps an edit made while a save is in flight and persists one follow-up", async () => {
  let resolveFirst!: (value: Draft) => void;
  const calls: Draft[] = [];
  const coordinator = new ProcessPlanSaveCoordinator<Draft>({
    formId: "FORM-A",
    debounceMs: 0,
    isDraft: plan => plan.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo, status: ack.status }),
    save: (_formId, plan) => {
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
    formId: "FORM-A",
    debounceMs: 0,
    isDraft: plan => plan.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo, status: ack.status }),
    save: async (_formId, plan) => { calls.push(plan); return { ...plan, versionNo: plan.versionNo + 1 }; },
  });
  coordinator.hydrate({ versionNo: 7, status: "DRAFT", graph: "server" });
  await coordinator.flush();
  coordinator.hydrate({ versionNo: 8, status: "SUBMITTED", graph: "formal" });
  coordinator.edit(plan => ({ ...plan, graph: "must-not-save" }));
  await coordinator.flush();
  coordinator.dispose();
  assert.equal(calls.length, 0);
});

test("rebind fences a late acknowledgement and restores a local draft as dirty", async () => {
  let resolveA!: (value: Draft) => void;
  const calls: Array<[string, Draft]> = [];
  const coordinator = new ProcessPlanSaveCoordinator<Draft>({
    formId: "FORM-A", debounceMs: 0, isDraft: value => value.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo }),
    save: (formId, plan) => { calls.push([formId, plan]); return formId === "FORM-A" ? new Promise(resolve => { resolveA = resolve; }) : Promise.resolve({ ...plan, versionNo: 8 }); },
  });
  coordinator.hydrateServer({ versionNo: 0, status: "DRAFT", graph: "A" });
  coordinator.edit(value => ({ ...value, graph: "A-edit" }));
  void coordinator.flush();
  coordinator.rebind("FORM-B", { versionNo: 7, status: "DRAFT", graph: "B" });
  coordinator.restoreLocalDirty({ versionNo: 7, status: "DRAFT", graph: "B-cache" });
  const flushB = coordinator.flush();
  resolveA({ versionNo: 1, status: "DRAFT", graph: "A-edit" });
  await flushB;
  assert.deepEqual(coordinator.value, { versionNo: 8, status: "DRAFT", graph: "B-cache" });
  assert.equal(calls[0]![0], "FORM-A");
  assert.equal(calls[1]![0], "FORM-B");
});

test("rebind fences a late failure without publishing or reporting it", async () => {
  let rejectA!: (error: Error) => void;
  const errors: string[] = [];
  const published: Array<[string, string]> = [];
  const coordinator = new ProcessPlanSaveCoordinator<Draft>({
    formId: "FORM-A",
    debounceMs: 0,
    isDraft: value => value.status === "DRAFT",
    mergeAck: (local, ack) => ({ ...local, versionNo: ack.versionNo }),
    save: (formId, plan) => formId === "FORM-A"
      ? new Promise((_resolve, reject) => { rejectA = reject; })
      : Promise.resolve({ ...plan, versionNo: 8 }),
    onChange: (value, state) => published.push([value.graph, state]),
    onError: error => errors.push(error instanceof Error ? error.message : String(error)),
  });
  coordinator.hydrateServer({ versionNo: 0, status: "DRAFT", graph: "A" });
  coordinator.edit(value => ({ ...value, graph: "A-edit" }));
  const staleFlush = coordinator.flush();
  coordinator.rebind("FORM-B", { versionNo: 7, status: "DRAFT", graph: "B" });
  coordinator.restoreLocalDirty({ versionNo: 7, status: "DRAFT", graph: "B-cache" });
  await coordinator.flush();
  rejectA(new Error("A failed late"));
  await assert.doesNotReject(staleFlush);
  assert.deepEqual(coordinator.value, { versionNo: 8, status: "DRAFT", graph: "B-cache" });
  assert.equal(coordinator.state, "saved");
  assert.deepEqual(errors, []);
  assert.deepEqual(published.at(-1), ["B-cache", "saved"]);
});
