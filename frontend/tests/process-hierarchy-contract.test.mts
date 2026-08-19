import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { calculateBatchYield, calculateMajorProcessYield, calculateMinorStepYield, createEmptyProcessPlan, normalizeProcessPlan, processPlanToLegacySteps } from "../packages/shared/src/process-plan.ts";

test("calculates major process yield and legacy summary", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push({
    key: "major-1", sequence: 1, processCode: "HEAT", processName: "热加工", description: "",
    yieldBasis: "PRIMARY_INPUT", remark: "", steps: [],
    inputs: [{ key: "in-1", sequence: 1, inputRole: "PRIMARY", materialCode: "BEEF", materialName: "牛肉", weightKg: 10 }],
    outputs: [{ key: "out-1", sequence: 1, outputType: "QUALIFIED", weightKg: 8, remark: "" }],
  });
  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, 80);
  assert.deepEqual(processPlanToLegacySteps(plan).map((row) => [row.processName, row.beforeWeightKg, row.afterWeightKg]), [["热加工", 10, 8]]);
});

test("calculates chained batch yield from primary material flow while skipping NONE", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push(
    majorWithFlow("10", "9"),
    { ...majorWithFlow("9", "1"), key: "major-none", yieldBasis: "NONE" },
    majorWithFlow("9", "7.2"),
    majorWithFlow("7.2", "5.04"),
  );

  assert.equal(calculateMinorStepYield(plan.majorProcesses[0]!.steps[1]!).mainYieldPercent, 90);
  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, 90);
  assert.equal(calculateBatchYield(plan), 50.4);
});

test("skips an incomplete primary flow instead of treating it as zero yield", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push({
    key: "major-incomplete", sequence: 1, processName: "静置", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
    steps: [{ key: "step-incomplete", sequence: 1, stepName: "静置", stepType: "NORMAL", materials: [{
      key: "material-incomplete", sequence: 1, materialRole: "PRIMARY", sourceType: "EXTERNAL", materialName: "牛肉",
      materialState: "SOLID", weightKg: 10,
    }], outputs: [], controlPoints: [] }],
  }, majorWithFlow("10", "8"));

  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, null);
  assert.equal(calculateBatchYield(plan), 80);
});

test("keeps historical major summary yields when a layered step only contains auxiliary material", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push({
    key: "major-historical", sequence: 1, processName: "煮制", yieldBasis: "PRIMARY_INPUT",
    inputs: [{ key: "historical-in", sequence: 1, inputRole: "PRIMARY", materialName: "牛肉", weightKg: 10 }],
    outputs: [{ key: "historical-out", sequence: 1, outputType: "QUALIFIED", weightKg: 8 }],
    steps: [{ key: "historical-step", sequence: 1, stepName: "加盐", stepType: "NORMAL", materials: [{
      key: "historical-salt", sequence: 1, materialRole: "AUXILIARY", materialName: "盐", materialState: "SOLID", weightKg: 0.2,
    }] }],
  });

  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, 80);
});

test("rejects multiple primary inputs or outputs in one step", () => {
  const duplicateInput = majorWithFlow("10", "8").steps[0]!;
  duplicateInput.materials.push({ ...duplicateInput.materials[0]!, key: "second-primary", materialName: "鸡肉" });
  assert.throws(() => calculateMinorStepYield(duplicateInput), /at most one primary material/);

  const duplicateOutput = majorWithFlow("10", "8").steps[0]!;
  duplicateOutput.outputs!.push({ ...duplicateOutput.outputs![0]!, key: "second-primary-output", outputName: "另一个产出" });
  assert.throws(() => calculateMinorStepYield(duplicateOutput), /at most one primary output/);
});

test("normalizes output IDs so STEP_OUTPUT references persist as stable IDs", () => {
  const normalized = normalizeProcessPlan({ versionNo: 1, status: "DRAFT", majorProcesses: [majorWithFlow("10", "8")] });
  const [first, second] = normalized.majorProcesses[0]!.steps;

  assert.equal(first!.outputs![0]!.id, first!.outputs![0]!.key);
  assert.equal(second!.materials[0]!.sourceStepOutputId, first!.outputs![0]!.id);
});

function majorWithFlow(inputWeight: string, outputWeight: string) {
  return {
    key: `major-${inputWeight}-${outputWeight}`, sequence: 1, processCode: "HEAT", processName: "热加工", description: "",
    yieldBasis: "PRIMARY_INPUT" as const, remark: "", inputs: [], outputs: [],
    steps: [
      {
        key: `step-start-${inputWeight}`, sequence: 1, stepName: "修割", stepType: "NORMAL" as const, materials: [{
          key: `material-start-${inputWeight}`, sequence: 1, materialRole: "PRIMARY" as const, sourceType: "EXTERNAL" as const,
          materialName: "鲜牛腩", materialState: "SOLID" as const, weightKg: Number(inputWeight),
        }], outputs: [{
          key: `output-start-${inputWeight}`, sequence: 1, outputType: "INTERMEDIATE", outputName: "修割牛腩",
          materialState: "SOLID" as const, weightKg: Number(inputWeight), primaryOutput: true, continueFlow: true,
        }], controlPoints: [],
      },
      {
        key: `step-end-${outputWeight}`, sequence: 2, stepName: "熟制", stepType: "NORMAL" as const, materials: [{
          key: `material-end-${outputWeight}`, sequence: 1, materialRole: "PRIMARY" as const, sourceType: "STEP_OUTPUT" as const,
          sourceStepOutputId: `output-start-${inputWeight}`, materialName: "修割牛腩", materialState: "SOLID" as const,
          weightKg: Number(inputWeight),
        }], outputs: [{
          key: `output-end-${outputWeight}`, sequence: 1, outputType: "FINISHED", outputName: "熟制牛腩",
          materialState: "SOLID" as const, weightKg: Number(outputWeight), primaryOutput: true, continueFlow: true,
        }], controlPoints: [],
      },
    ],
  };
}

test("PC editor exposes layered drag, edit, material and yield entrances", () => {
  const source = fs.readFileSync(new URL("../apps/pc/src/components/ProcessHierarchyEditor.vue", import.meta.url), "utf8");
  for (const marker of ["工艺模板库", "大工序", "小步骤", "添加主料/辅料", "投入产出", "整批得率", "draggable"]) {
    assert.ok(source.includes(marker), `missing ${marker}`);
  }
});

test("task API exposes process plan endpoints", () => {
  const source = fs.readFileSync(new URL("../packages/shared/src/api/task.ts", import.meta.url), "utf8");
  assert.match(source, /getProcessPlan/);
  assert.match(source, /saveProcessPlan/);
  assert.match(source, /experiment-forms\/\$\{formId\}\/process-plan/);
});
