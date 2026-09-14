import assert from "node:assert/strict";
import test from "node:test";
import type { ProcessPlanDraft } from "../packages/shared/src/process-plan.ts";
import type { TrialScheme } from "../apps/pc/src/services/trialApi.ts";
import {
  buildTrialComparison,
  numericDifference,
  plannedActualRows,
} from "../apps/pc/src/components/trials/trialComparison.ts";

function trial(overrides: Partial<TrialScheme> = {}): TrialScheme {
  const plan: ProcessPlanDraft = {
    versionNo: 0, status: "DRAFT", majorProcesses: [{
      id: "major-a", key: "major-a", sequence: 1, processCode: "COOK", processName: "热加工", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
      steps: [{
        id: "step-a", key: "step-a", sequence: 1, stepCode: "COOK-1", stepName: "煮制", stepType: "NORMAL",
        parameter1Name: "温度", parameter1Value: "92", parameter1Unit: "℃", parameter2Name: "时间", parameter2Value: "20", parameter2Unit: "min",
        materials: [{ id: "material-a", key: "material-a", sequence: 1, materialRole: "PRIMARY", materialCode: "BEEF", formulaMaterialId: "FM-BEEF", materialName: "牛肉", materialState: "SOLID", weightKg: 10 }],
        outputs: [{ id: "output-a", key: "output-a", sequence: 1, outputType: "FINISHED", outputName: "熟牛肉", materialState: "SOLID", weightKg: 8, primaryOutput: true, continueFlow: false }],
        controlPoints: [],
      }],
    }],
  };
  return {
    id: "trial-a", experimentFormId: "form-a", versionNo: 1, name: "方案 A", archived: false,
    purpose: null, variables: null, conclusion: "PENDING", recommendationReason: null,
    qualityScore: null, qualityNotes: null, difficulty: null, plan,
    plannedData: { materialWeightsKg: { "material-a": 10 }, stepParameters: { "step-a": { parameter1Value: "90", parameter2Value: "20" } }, majorYieldTargets: { "major-a": 80 }, batchYieldTarget: 80, yieldBasisNote: "按主料" },
    inheritedActuals: false, majorOrigins: { "major-a": "origin-cook" }, createdBy: "研发员", createdAt: "2026-09-15T09:00:00", updatedBy: "研发员", updatedAt: "2026-09-15T09:00:00",
    ...overrides,
  };
}

test("numeric difference is actual minus planned with decimal-safe +0.2kg", () => {
  assert.equal(numericDifference(10, 10.2), 0.2);
  assert.equal(numericDifference(null, 10.2), null);
  assert.equal(numericDifference(0, 0), 0);
});

test("planned versus actual keeps missing actual distinct from zero", () => {
  const value = trial();
  value.plan.majorProcesses[0]!.steps[0]!.materials[0]!.weightKg = undefined;
  const rows = plannedActualRows(value);
  const material = rows.find(row => row.kind === "MATERIAL")!;
  assert.equal(material.planned, 10);
  assert.equal(material.actual, null);
  assert.equal(material.difference, null);
  assert.equal(material.complete, false);
});

test("comparison reports text parameter changes rather than coercing them to numbers", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Value = "小火";
  baseline.plan.majorProcesses[0]!.steps[0]!.parameter1Value = "中火";

  const comparison = buildTrialComparison(candidate, baseline);
  assert.ok(comparison.differences.some(item => item.kind === "PARAMETER" && item.before === "中火" && item.after === "小火"));
});

test("repeated unmatched process names are incomparable instead of paired by row or name", () => {
  const baseline = trial();
  const repeated = structuredClone(baseline.plan.majorProcesses[0]!);
  repeated.id = "major-repeat";
  repeated.key = "major-repeat";
  repeated.sequence = 2;
  baseline.plan.majorProcesses.push(repeated);
  baseline.majorOrigins = {};
  const candidate = trial({ id: "trial-b", name: "方案 B", majorOrigins: {} });

  const comparison = buildTrialComparison(candidate, baseline);
  assert.equal(comparison.majorComparisons.every(item => item.comparable === false), true);
  assert.ok(comparison.majorComparisons.some(item => item.reason?.includes("唯一来源标识")));
});

test("steps without a unique business identifier are reported incomparable instead of silently matching", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  baseline.plan.majorProcesses[0]!.steps[0]!.stepCode = "";
  candidate.plan.majorProcesses[0]!.steps[0]!.stepCode = "";
  candidate.plan.majorProcesses[0]!.steps[0]!.id = "step-b";
  candidate.plan.majorProcesses[0]!.steps[0]!.key = "step-b";
  candidate.plan.majorProcesses[0]!.steps[0]!.materials[0]!.id = "material-b";
  candidate.plan.majorProcesses[0]!.steps[0]!.materials[0]!.key = "material-b";
  candidate.plannedData.materialWeightsKg = { "material-b": 10.2 };

  const comparison = buildTrialComparison(candidate, baseline);
  assert.ok(comparison.differences.some(item => item.kind === "INCOMPARABLE" && item.path.includes("小步骤")));
  assert.equal(comparison.differences.some(item => item.kind === "FORMULA"), false, "an empty stepCode must never fall back to name or row order");
});

test("matched major with a different yield basis is marked incomparable", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  candidate.plan.majorProcesses[0]!.yieldBasis = "TOTAL_INPUT";

  const comparison = buildTrialComparison(candidate, baseline);
  assert.equal(comparison.majorComparisons[0]!.comparable, false);
  assert.match(comparison.majorComparisons[0]!.reason!, /得率口径/);
});

test("duplicate major origins are explicitly incomparable", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  const repeated = structuredClone(candidate.plan.majorProcesses[0]!);
  repeated.id = "major-b-repeat";
  repeated.key = "major-b-repeat";
  repeated.sequence = 2;
  candidate.plan.majorProcesses.push(repeated);
  candidate.majorOrigins["major-b-repeat"] = "origin-cook";

  const comparison = buildTrialComparison(candidate, baseline);
  assert.ok(comparison.majorComparisons.some(item => !item.comparable && item.reason?.includes("唯一来源标识")));
});

test("baseline comparison includes planned formula and planned text parameter differences", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  candidate.plannedData.materialWeightsKg["material-a"] = 10.2;
  candidate.plannedData.stepParameters["step-a"] = { parameter1Value: "小火", parameter2Value: "20" };
  baseline.plannedData.stepParameters["step-a"] = { parameter1Value: "中火", parameter2Value: "20" };

  const comparison = buildTrialComparison(candidate, baseline);
  assert.ok(comparison.differences.some(item => item.kind === "FORMULA" && item.before === 10 && item.after === 10.2));
  assert.ok(comparison.differences.some(item => item.kind === "PARAMETER" && item.before === "中火" && item.after === "小火"));
});

test("changed primary material or weighing-basis note makes a matched major incomparable", () => {
  const baseline = trial();
  const changedMaterial = trial({ id: "trial-b", name: "方案 B" });
  changedMaterial.plan.majorProcesses[0]!.steps[0]!.materials[0]!.formulaMaterialId = "FM-PORK";
  changedMaterial.plan.majorProcesses[0]!.steps[0]!.materials[0]!.materialCode = "PORK";
  changedMaterial.plan.majorProcesses[0]!.steps[0]!.materials[0]!.materialName = "猪肉";
  const materialComparison = buildTrialComparison(changedMaterial, baseline);
  assert.equal(materialComparison.majorComparisons[0]!.comparable, false);
  assert.match(materialComparison.majorComparisons[0]!.reason!, /主料/);

  const changedBasis = trial({ id: "trial-c", name: "方案 C" });
  changedBasis.plannedData.yieldBasisNote = "按沥水后主料";
  const basisComparison = buildTrialComparison(changedBasis, baseline);
  assert.equal(basisComparison.majorComparisons[0]!.comparable, false);
  assert.match(basisComparison.majorComparisons[0]!.reason!, /称重口径/);
});

test("batch actual remains pending when the primary flow is not continuous", () => {
  const value = trial();
  const second = structuredClone(value.plan.majorProcesses[0]!);
  second.id = "major-b";
  second.key = "major-b";
  second.sequence = 2;
  second.processName = "冷却";
  second.steps[0]!.id = "step-b";
  second.steps[0]!.key = "step-b";
  second.steps[0]!.stepCode = "COOL-1";
  second.steps[0]!.materials[0]!.id = "material-b";
  second.steps[0]!.materials[0]!.key = "material-b";
  second.steps[0]!.materials[0]!.sourceType = "EXTERNAL";
  second.steps[0]!.outputs![0]!.id = "output-b";
  second.steps[0]!.outputs![0]!.key = "output-b";
  value.plan.majorProcesses.push(second);
  value.plannedData.batchYieldTarget = 64;

  const batch = plannedActualRows(value).find(row => row.kind === "BATCH_YIELD")!;
  assert.equal(batch.actual, null);
  assert.equal(batch.complete, false);
});

test("numeric parameter delta is emitted only when parameter name and unit match", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Value = "92.5";
  baseline.plan.majorProcesses[0]!.steps[0]!.parameter1Value = "92";
  let item = buildTrialComparison(candidate, baseline).differences.find(entry => entry.kind === "PARAMETER" && entry.path.endsWith("温度"))!;
  assert.equal(item.numericDelta, 0.5);

  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Unit = "℉";
  item = buildTrialComparison(candidate, baseline).differences.find(entry => entry.kind === "PARAMETER" && entry.path.endsWith("温度"))!;
  assert.equal(item.numericDelta, null);
});
