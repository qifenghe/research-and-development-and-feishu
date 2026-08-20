import assert from "node:assert/strict";
import test from "node:test";
import { confirmProcessPlanRepair, copyMajorProcess, previousFlowOutputs, repairProcessPlanFlow } from "../apps/pc/src/components/process/processPlanFlow.ts";
import { createEmptyProcessPlan, type ProcessPlanDraft } from "../packages/shared/src/process-plan.ts";

function chain(): ProcessPlanDraft {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses = [
    { key: "m1", sequence: 1, processName: "前段", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [], steps: [
      { key: "s1", sequence: 1, stepName: "产出", stepType: "NORMAL", materials: [], outputs: [
        { key: "o1", id: "o1", sequence: 1, outputType: "INTERMEDIATE", outputName: "中间料", materialState: "SOLID", primaryOutput: true, continueFlow: true },
      ], controlPoints: [] },
    ] },
    { key: "m2", sequence: 2, processName: "后段", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [], steps: [
      { key: "s2", sequence: 1, stepName: "使用", stepType: "NORMAL", materials: [
        { key: "x", sequence: 1, materialRole: "PRIMARY", materialName: "中间料", materialState: "SOLID", sourceType: "STEP_OUTPUT", sourceStepOutputId: "o1" },
      ], outputs: [
        { key: "o2", id: "o2", sequence: 1, outputType: "FINISHED", outputName: "成品", materialState: "SOLID", primaryOutput: true, continueFlow: false },
      ], controlPoints: [] },
    ] },
  ];
  return plan;
}

test("orders producer and consumer by major/step tuples", () => {
  assert.equal(previousFlowOutputs(chain(), "m2", "s2", true)[0]?.output.id, "o1");
});

test("removes, never externalizes, references broken by output/step/major deletion", () => {
  for (const mutate of [
    (plan: ProcessPlanDraft) => { plan.majorProcesses[0]!.steps[0]!.outputs = []; },
    (plan: ProcessPlanDraft) => { plan.majorProcesses[0]!.steps = []; },
    (plan: ProcessPlanDraft) => { plan.majorProcesses.splice(0, 1); },
  ]) {
    const plan = chain();
    mutate(plan);
    const repaired = repairProcessPlanFlow(plan);
    assert.equal(repaired.removedConsumers.length, 1);
    assert.equal(repaired.plan.majorProcesses.at(-1)!.steps[0]!.materials.length, 0);
  }
});

test("repairs precedence, continue-flow, primary compatibility and duplicate IDs", () => {
  const cases = [
    (plan: ProcessPlanDraft) => { plan.majorProcesses.reverse(); },
    (plan: ProcessPlanDraft) => { plan.majorProcesses[0]!.steps[0]!.outputs![0]!.continueFlow = false; },
    (plan: ProcessPlanDraft) => { plan.majorProcesses[0]!.steps[0]!.outputs![0]!.primaryOutput = false; },
    (plan: ProcessPlanDraft) => { plan.majorProcesses[1]!.steps[0]!.outputs![0]!.id = "o1"; },
  ];
  for (const mutate of cases) {
    const plan = chain();
    mutate(plan);
    const repaired = repairProcessPlanFlow(plan);
    assert.equal(repaired.removedConsumers.length, 1);
    assert.equal(repaired.plan.majorProcesses.find(major => major.key === "m2")!.steps[0]!.materials.length, 0);
    const ids = repaired.plan.majorProcesses.flatMap(major => major.steps.flatMap(step => step.outputs || []).map(output => output.id));
    assert.equal(new Set(ids).size, ids.length);
  }
});

test("clears and reports a stale source output id on an external material", () => {
  const plan = chain();
  const material = plan.majorProcesses[1]!.steps[0]!.materials[0]!;
  material.sourceType = "EXTERNAL";

  const repaired = repairProcessPlanFlow(plan);

  assert.equal(repaired.removedConsumers.length, 1);
  assert.equal(repaired.removedConsumers[0]?.reason, "EXTERNAL_SOURCE");
  assert.equal(repaired.removedConsumers[0]?.action, "CLEARED_SOURCE");
  assert.equal(repaired.plan.majorProcesses[1]!.steps[0]!.materials.length, 1);
  assert.equal(repaired.plan.majorProcesses[1]!.steps[0]!.materials[0]!.sourceStepOutputId, undefined);
});

test("step reorder within a major invalidates a now-later producer", () => {
  const plan = chain();
  plan.majorProcesses[1]!.steps[0]!.outputs![0]!.continueFlow = true;
  const consumer = plan.majorProcesses[1]!.steps[0]!;
  const producer = structuredClone(plan.majorProcesses[0]!.steps[0]!);
  producer.key = "inside";
  producer.sequence = 1;
  consumer.sequence = 2;
  plan.majorProcesses = [{ ...plan.majorProcesses[1]!, sequence: 1, steps: [producer, consumer] }];
  plan.majorProcesses[0]!.steps.reverse();
  const repaired = repairProcessPlanFlow(plan);
  assert.equal(repaired.removedConsumers.length, 1);
});

test("copy remaps output IDs and preserves sources from earlier majors", () => {
  const plan = chain();
  plan.majorProcesses[1]!.steps[0]!.outputs![0]!.continueFlow = true;
  plan.majorProcesses[1]!.steps.push({
    key: "s3", sequence: 2, stepName: "内部消费", stepType: "NORMAL",
    materials: [{ key: "internal", sequence: 1, materialRole: "PRIMARY", materialName: "成品", materialState: "SOLID", sourceType: "STEP_OUTPUT", sourceStepOutputId: "o2" }],
    outputs: [], controlPoints: [],
  });
  const copied = copyMajorProcess(plan, "m2");
  const copy = copied.plan.majorProcesses[2]!;
  const copiedOutputId = copy.steps[0]!.outputs![0]!.id;
  assert.notEqual(copiedOutputId, "o2");
  assert.equal(copy.steps[0]!.materials[0]!.sourceStepOutputId, "o1");
  assert.equal(copy.steps[1]!.materials[0]!.sourceStepOutputId, copiedOutputId);
  const ids = copied.plan.majorProcesses.flatMap(major => major.steps.flatMap(step => step.outputs || []).map(output => output.id));
  assert.equal(new Set(ids).size, ids.length);
});

test("every mutation that discovers a flow repair is transactional", async () => {
  const broken = chain();
  broken.majorProcesses[0]!.steps[0]!.outputs![0]!.continueFlow = false;
  let confirmations = 0;

  const cancelled = await confirmProcessPlanRepair(broken, async consumers => {
    confirmations++;
    assert.deepEqual(consumers.map(item => item.materialName), ["中间料"]);
    return false;
  });
  assert.equal(confirmations, 1);
  assert.equal(cancelled.accepted, false);
  assert.equal(cancelled.plan.majorProcesses[1]!.steps[0]!.materials.length, 1);
  assert.equal(broken.majorProcesses[1]!.steps[0]!.materials.length, 1);

  const accepted = await confirmProcessPlanRepair(broken, async () => true);
  assert.equal(accepted.accepted, true);
  assert.equal(accepted.plan.majorProcesses[1]!.steps[0]!.materials.length, 0);

  let cleanConfirmations = 0;
  const clean = await confirmProcessPlanRepair(chain(), async () => {
    cleanConfirmations++;
    return false;
  });
  assert.equal(clean.accepted, true);
  assert.equal(cleanConfirmations, 0);
});
