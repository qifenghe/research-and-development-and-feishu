import assert from "node:assert/strict";
import test from "node:test";
import { calculateBatchYield, type ProcessPlanDraft } from "../packages/shared/src/process-plan.ts";
import type { TrialScheme } from "../apps/pc/src/services/trialApi.ts";
import {
  buildTrialComparison,
  numericDifference,
  plannedActualRows,
  deviationUnit,
} from "../apps/pc/src/components/trials/trialComparison.ts";

function trial(overrides: Partial<TrialScheme> = {}): TrialScheme {
  const plan: ProcessPlanDraft = {
    versionNo: 0, status: "DRAFT", majorProcesses: [{
      id: "major-a", key: "major-a", sequence: 1, processCode: "COOK", processName: "热加工", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
      steps: [{
        id: "step-a", key: "step-a", sequence: 1, stepCode: "COOK-1", stepName: "煮制", stepType: "NORMAL",
        parameter1Name: "温度", parameter1Value: "92", parameter1Unit: "℃", parameter2Name: "时间", parameter2Value: "20", parameter2Unit: "min",
        materials: [{ id: "material-a", key: "material-a", sequence: 1, materialRole: "PRIMARY", materialCode: "BEEF", formulaMaterialId: "FM-BEEF", materialName: "牛肉", materialState: "SOLID", weightKg: 10, sourceType: "EXTERNAL" }],
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

test("duplicate and missing material identities are explicit incomparable rows", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  const duplicate = structuredClone(candidate.plan.majorProcesses[0]!.steps[0]!.materials[0]!);
  duplicate.id = "material-salt-1";
  duplicate.key = "material-salt-1";
  duplicate.materialRole = "AUXILIARY";
  duplicate.formulaMaterialId = "FM-SALT";
  duplicate.materialCode = "SALT";
  duplicate.materialName = "盐";
  duplicate.weightKg = 0.1;
  const duplicate2 = { ...duplicate, id: "material-salt-2", key: "material-salt-2", weightKg: 0.2 };
  candidate.plan.majorProcesses[0]!.steps[0]!.materials.push(duplicate, duplicate2);
  const ambiguous = buildTrialComparison(candidate, baseline);
  assert.ok(ambiguous.differences.some(item => item.kind === "INCOMPARABLE" && item.path.includes("配方")));

  candidate.plan.majorProcesses[0]!.steps[0]!.materials = [{
    ...candidate.plan.majorProcesses[0]!.steps[0]!.materials[0]!,
    formulaMaterialId: undefined,
    materialCode: undefined,
  }];
  const missing = buildTrialComparison(candidate, baseline);
  assert.ok(missing.differences.some(item => item.kind === "INCOMPARABLE" && item.path.includes("缺少唯一标识")));
});

test("duplicate major origins on both sides cannot disappear as no difference", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  for (const value of [baseline, candidate]) {
    const repeated = structuredClone(value.plan.majorProcesses[0]!);
    repeated.id = `${value.id}-major-2`;
    repeated.key = repeated.id;
    repeated.sequence = 2;
    value.plan.majorProcesses.push(repeated);
    value.majorOrigins[repeated.id] = "origin-cook";
  }
  const comparison = buildTrialComparison(candidate, baseline);
  assert.ok(comparison.majorComparisons.some(item => !item.comparable));
  assert.ok(comparison.differences.some(item => item.kind === "INCOMPARABLE"));
});

test("an external primary material without stable identity is incomparable even when display names match", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  for (const value of [baseline, candidate]) {
    const material = value.plan.majorProcesses[0]!.steps[0]!.materials[0]!;
    material.formulaMaterialId = undefined;
    material.materialCode = undefined;
    material.materialName = "同名主料";
  }
  const comparison = buildTrialComparison(candidate, baseline);
  assert.equal(comparison.majorComparisons[0]!.comparable, false);
  assert.match(comparison.majorComparisons[0]!.reason!, /主料/);
});

test("parameter definition changes remain visible when values are unchanged", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Unit = "℉";
  let definition = buildTrialComparison(candidate, baseline).differences.find(item => item.path.includes("参数 1定义"));
  assert.ok(definition);
  assert.equal(definition!.numericDelta, undefined);
  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Unit = "℃";
  candidate.plan.majorProcesses[0]!.steps[0]!.parameter1Name = "中心温度";
  definition = buildTrialComparison(candidate, baseline).differences.find(item => item.path.includes("参数 1定义"));
  assert.ok(definition);
});

test("yield deviations use percentage points while actual rates retain percent", () => {
  assert.equal(deviationUnit("MAJOR_YIELD", "%"), "个百分点");
  assert.equal(deviationUnit("BATCH_YIELD", "%"), "个百分点");
  assert.equal(deviationUnit("MATERIAL", "kg"), "kg");
});

test("downstream primary identity traces through remapped outputs to the unique external source", () => {
  const baseline = trial();
  const candidate = trial({ id: "trial-b", name: "方案 B" });
  for (const [value, suffix] of [[baseline, "base"], [candidate, "candidate"]] as const) {
    const upstream = value.plan.majorProcesses[0]!;
    upstream.steps[0]!.outputs![0]!.id = `up-output-${suffix}`;
    upstream.steps[0]!.outputs![0]!.key = `up-output-${suffix}`;
    upstream.steps[0]!.outputs![0]!.continueFlow = true;
    const downstream = structuredClone(upstream);
    downstream.id = `major-down-${suffix}`;
    downstream.key = downstream.id;
    downstream.sequence = 2;
    downstream.processName = "冷却";
    downstream.steps[0]!.id = `step-down-${suffix}`;
    downstream.steps[0]!.key = downstream.steps[0]!.id;
    downstream.steps[0]!.stepCode = "COOL-1";
    downstream.steps[0]!.outputs![0]!.id = `down-output-${suffix}`;
    downstream.steps[0]!.outputs![0]!.key = `down-output-${suffix}`;
    const input = downstream.steps[0]!.materials[0]!;
    input.id = `material-down-${suffix}`;
    input.key = input.id;
    input.formulaMaterialId = undefined;
    input.materialCode = undefined;
    input.sourceType = "STEP_OUTPUT";
    input.sourceStepOutputId = `up-output-${suffix}`;
    value.plan.majorProcesses.push(downstream);
    value.majorOrigins[downstream.id] = "origin-cool";
  }
  let comparison = buildTrialComparison(candidate, baseline);
  assert.equal(comparison.majorComparisons.find(item => item.label === "冷却")!.comparable, true);
  candidate.plan.majorProcesses[0]!.steps[0]!.materials[0]!.formulaMaterialId = "FM-PORK";
  comparison = buildTrialComparison(candidate, baseline);
  assert.equal(comparison.majorComparisons.find(item => item.label === "冷却")!.comparable, false);
});

test("a measured zero terminal output remains a complete 0% trial actual", () => {
  const value = trial();
  value.plan.majorProcesses[0]!.steps[0]!.outputs![0]!.weightKg = 0;
  const row = plannedActualRows(value).find(item => item.kind === "MAJOR_YIELD")!;
  const batch = plannedActualRows(value).find(item => item.kind === "BATCH_YIELD")!;
  assert.equal(row.actual, 0);
  assert.equal(row.complete, true);
  assert.equal(batch.actual, 0);
  assert.equal(batch.complete, true);
  assert.equal(calculateBatchYield(value.plan), 0);
  value.plan.majorProcesses[0]!.steps[0]!.outputs![0]!.weightKg = undefined;
  const missing = plannedActualRows(value).find(item => item.kind === "MAJOR_YIELD")!;
  assert.equal(missing.actual, null);
  assert.equal(missing.complete, false);
});

test("legacy total-input basis is pending and explicitly incomparable with or without auxiliary actuals", () => {
  for (const auxiliaryWeight of [10, undefined]) {
    const baseline = trial();
    const candidate = trial({ id: "trial-b", name: "方案 B" });
    for (const value of [baseline, candidate]) {
      const major = value.plan.majorProcesses[0]!;
      major.yieldBasis = "TOTAL_INPUT";
      major.steps[0]!.materials.push({ id: "aux", key: "aux", sequence: 2, materialRole: "AUXILIARY", materialCode: "SALT", materialName: "辅料", materialState: "SOLID", weightKg: auxiliaryWeight });
    }
    const rows = plannedActualRows(candidate);
    const majorRow = rows.find(item => item.kind === "MAJOR_YIELD")!;
    const batchRow = rows.find(item => item.kind === "BATCH_YIELD")!;
    assert.equal(majorRow.actual, null);
    assert.equal(majorRow.complete, false);
    assert.match(majorRow.reason || "", /旧式总投入.*主料/);
    assert.equal(batchRow.actual, null);
    assert.equal(batchRow.complete, false);
    assert.match(batchRow.reason || "", /旧式总投入.*主料/);
    const comparison = buildTrialComparison(candidate, baseline);
    assert.equal(comparison.majorComparisons[0]!.comparable, false);
    assert.match(comparison.majorComparisons[0]!.reason || "", /旧式总投入.*主料/);
  }
});
