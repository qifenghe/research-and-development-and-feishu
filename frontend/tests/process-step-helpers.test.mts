import assert from "node:assert/strict";
import test from "node:test";
import {
  blankProcessStep,
  copyProcessStep,
  lossWeightKg,
  lossRatePercent,
  moveProcessStep,
  removeProcessStep,
} from "../apps/mobile/src/components/ProcessStepEditor.helpers.ts";

function named(name: string) {
  return { ...blankProcessStep(), processName: name };
}

test("moves a process step without mutating the original list", () => {
  const original = [named("解冻"), named("修割"), named("熟制")];
  const moved = moveProcessStep(original, 2, 0);
  assert.deepEqual(moved.map((step) => step.processName), ["熟制", "解冻", "修割"]);
  assert.deepEqual(original.map((step) => step.processName), ["解冻", "修割", "熟制"]);
});

test("copies a process step after its source", () => {
  const copied = copyProcessStep([named("解冻"), named("熟制")], 0);
  assert.deepEqual(copied.map((step) => step.processName), ["解冻", "解冻（副本）", "熟制"]);
});

test("keeps at least one process step when removing", () => {
  assert.equal(removeProcessStep([named("解冻")], 0).length, 1);
  assert.deepEqual(
    removeProcessStep([named("解冻"), named("熟制")], 0).map((step) => step.processName),
    ["熟制"],
  );
});

test("calculates the loss rate percentage", () => {
  assert.equal(lossRatePercent({ ...named("熟制"), beforeWeightKg: "10", afterWeightKg: "8.5" }), "15.0");
  assert.equal(lossRatePercent(named("熟制")), null);
});

test("subtracts residual weight before calculating process loss", () => {
  const step = { ...named("修割"), beforeWeightKg: "10", afterWeightKg: "8", remainingWeightKg: "1" };
  assert.equal(lossWeightKg(step), "1.000");
  assert.equal(lossRatePercent(step), "10.0");
});
