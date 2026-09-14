import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { createEmptyProcessPlan, type ProcessPlanDraft } from "../packages/shared/src/process-plan.ts";
import {
  TrialRequestFence,
  normalizeTrialLocalDateTime,
  prepareTrialPlanForSave,
  readTrialDraft,
  trialDraftKey,
  writeTrialDraft,
} from "../apps/pc/src/components/trials/trialDraft.ts";

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length() { return this.values.size; }
  clear() { this.values.clear(); }
  getItem(key: string) { return this.values.get(key) ?? null; }
  key(index: number) { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string) { this.values.delete(key); }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

test("trial cache identity isolates user, form and trial", () => {
  const storage = new MemoryStorage();
  const key = trialDraftKey("USER-1", "FORM-1", "TRIAL-A");
  writeTrialDraft(storage, key, { name: "A 的本地修改" }, "2026-09-15T10:00:00");

  assert.deepEqual(readTrialDraft(storage, key)?.value, { name: "A 的本地修改" });
  assert.equal(readTrialDraft(storage, trialDraftKey("USER-2", "FORM-1", "TRIAL-A")), null);
  assert.equal(readTrialDraft(storage, trialDraftKey("USER-1", "FORM-2", "TRIAL-A")), null);
  assert.equal(readTrialDraft(storage, trialDraftKey("USER-1", "FORM-1", "TRIAL-B")), null);
});

test("request fence rejects a callback after trial identity changes without invalidating another fence", () => {
  const detailFence = new TrialRequestFence();
  const listFence = new TrialRequestFence();
  const staleDetail = detailFence.begin("FORM-1", "TRIAL-A");
  const currentList = listFence.begin("FORM-1", "list");

  detailFence.begin("FORM-1", "TRIAL-B");

  assert.equal(detailFence.isCurrent(staleDetail), false);
  assert.equal(listFence.isCurrent(currentList), true);
});

test("save preparation gives every new graph node a stable temporary id", () => {
  const plan: ProcessPlanDraft = {
    ...createEmptyProcessPlan(),
    majorProcesses: [{
      key: "major-local", sequence: 1, processName: "热加工", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
      steps: [{
        key: "step-local", sequence: 1, stepName: "煮制", stepType: "NORMAL",
        materials: [{ key: "material-local", sequence: 1, materialRole: "PRIMARY", materialName: "牛肉", materialState: "SOLID" }],
        outputs: [{ key: "output-local", sequence: 1, outputType: "FINISHED", outputName: "熟牛肉", materialState: "SOLID", primaryOutput: true, continueFlow: false }],
        controlPoints: [{ key: "point-local", sequence: 1, controlType: "PROCESS", importance: "NORMAL", itemName: "温度", resolved: false,
          measurements: [{ key: "measure-local", sequence: 1, measuredAt: "2026-09-15 10:00:00" }] }],
      }],
    }],
  };

  const prepared = prepareTrialPlanForSave(plan);
  const step = prepared.majorProcesses[0]!.steps[0]!;
  assert.equal(prepared.majorProcesses[0]!.id, "major-local");
  assert.equal(step.id, "step-local");
  assert.equal(step.materials[0]!.id, "material-local");
  assert.equal(step.outputs![0]!.id, "output-local");
  assert.equal(step.controlPoints![0]!.id, "point-local");
  assert.equal(step.controlPoints![0]!.measurements[0]!.id, "measure-local");
  assert.equal(step.controlPoints![0]!.measurements[0]!.measuredAt, "2026-09-15T10:00:00");
});

test("trial timestamps stay local and reject zone-bearing values", () => {
  assert.equal(normalizeTrialLocalDateTime("2026-09-15 10:00:00"), "2026-09-15T10:00:00");
  assert.equal(normalizeTrialLocalDateTime(""), undefined);
  assert.throws(() => normalizeTrialLocalDateTime("2026-09-15T10:00:00Z"), /本地日期时间/);
  assert.throws(() => normalizeTrialLocalDateTime("2026-09-15T10:00:00+08:00"), /本地日期时间/);
});

test("trial workbench uses the isolated graph editors and explicit trial confirmation endpoints", () => {
  const root = new URL("../apps/pc/src/", import.meta.url);
  const read = (path: string) => {
    const url = new URL(path, root);
    return fs.existsSync(url) ? fs.readFileSync(url, "utf8") : "";
  };
  const workbench = read("components/trials/TrialWorkbench.vue");
  const form = read("views/rnd/ExperimentFormView.vue");
  const minor = read("components/process/MinorStepWorkspace.vue");
  const controls = read("components/process/ControlPointEditor.vue");
  const service = read("services/trialApi.ts");

  for (const marker of ["MajorProcessBoard", "MinorStepWorkspace", "plannedData", "promotionPreview", "保存并切换", "放弃修改", "取消切换"]) {
    assert.ok(workbench.includes(marker), `workbench missing ${marker}`);
  }
  assert.match(form, /TrialWorkbench/);
  assert.match(minor, /selected-step-extension/);
  assert.match(controls, /confirmationMode/);
  assert.match(controls, /confirm-pass/);
  assert.match(service, /control-points\/\$\{pointId\}\/confirm`/);
  assert.match(service, /submission-preview/);
  assert.match(service, /\/submit`/);
  assert.doesNotMatch(workbench, /saveProcessPlan|__SESSION_CONFIRMATION_REQUESTED__/);
});

test("formal revision drawer loads public source separately from private recovered draft", () => {
  const workspace = fs.readFileSync(new URL("../apps/pc/src/components/process/ProcessPlanWorkspace.vue", import.meta.url), "utf8");
  assert.match(workspace, /revisionSource/);
  assert.match(workspace, /canRecoverDisplacedDraft/);
  assert.match(workspace, /loadDisplacedDraft/);
  assert.match(workspace, /copyRecoveredDraft/);
});
