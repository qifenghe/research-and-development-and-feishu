import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { aggregateProcessRecipe, calculateBatchYield, calculateMajorProcessYield, calculateMinorStepYield, createEmptyProcessPlan, normalizeProcessPlan, previewProcessSubmission, processPlanToLegacySteps } from "../packages/shared/src/process-plan.ts";

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
    { ...majorWithFlow("9", "9"), key: "major-none", yieldBasis: "NONE", steps: [] },
    majorWithFlow("9", "7.2"),
    majorWithFlow("7.2", "5.04"),
  );

  let previousOutput: string | undefined;
  plan.majorProcesses.forEach((major, index) => {
    major.sequence = index + 1;
    for (const step of major.steps) {
      const input = step.materials[0]!;
      input.sourceType = previousOutput ? 'STEP_OUTPUT' : 'EXTERNAL';
      input.sourceStepOutputId = previousOutput;
      const output = step.outputs![0]!;
      output.id = output.key = `output-${index}-${step.sequence}`;
      output.continueFlow = true;
      previousOutput = output.key;
    }
  });
  assert.equal(calculateMinorStepYield(plan.majorProcesses[0]!.steps[1]!).mainYieldPercent, 90);
  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, 90);
  assert.equal(calculateBatchYield(plan), 50.4);
});

test("withholds final yield while a primary flow is incomplete", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push({
    key: "major-incomplete", sequence: 1, processName: "静置", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
    steps: [{ key: "step-incomplete", sequence: 1, stepName: "静置", stepType: "NORMAL", materials: [{
      key: "material-incomplete", sequence: 1, materialRole: "PRIMARY", sourceType: "EXTERNAL", materialName: "牛肉",
      materialState: "SOLID", weightKg: 10,
    }], outputs: [], controlPoints: [] }],
  }, majorWithFlow("10", "8"));

  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, null);
  assert.equal(calculateBatchYield(plan), null);
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

test("normalizes newly loaded control and measurement IDs for trusted confirmation endpoints", () => {
  const major = majorWithFlow("10", "8");
  major.steps[0]!.controlPoints = [{
    sequence: 1, controlType: "FOOD_SAFETY", importance: "CRITICAL", itemName: "中心温度", resolved: false,
    measurements: [{ sequence: 1, measuredValue: 76, result: "PASS", retestResult: "PENDING" }],
  }];

  const normalized = normalizeProcessPlan({ versionNo: 1, status: "DRAFT", majorProcesses: [major] });
  const point = normalized.majorProcesses[0]!.steps[0]!.controlPoints![0]!;

  assert.ok(point.id);
  assert.equal(point.id, point.key);
  assert.ok(point.measurements[0]!.id);
  assert.equal(point.measurements[0]!.id, point.measurements[0]!.key);
});

test("previews recipe aggregation and submission flow errors without including intermediate inputs", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push(majorWithFlow("10", "8"));
  plan.majorProcesses[0]!.steps[0]!.materials[0]!.formulaMaterialId = "BEEF-1";
  plan.majorProcesses[0]!.steps[0]!.materials.push({
    key: "salt-1", sequence: 2, materialRole: "AUXILIARY", sourceType: "EXTERNAL", materialCode: "SALT",
    materialName: "食盐", materialState: "SOLID", weightKg: 0.18,
  });

  assert.deepEqual(aggregateProcessRecipe(plan).map((line) => [line.materialName, line.weightKg]), [["鲜牛腩", 10], ["食盐", 0.18]]);

  plan.majorProcesses[0]!.steps[0]!.materials[0] = {
    ...plan.majorProcesses[0]!.steps[0]!.materials[0]!, sourceType: "STEP_OUTPUT", sourceStepOutputId: "missing-output",
  };
  assert.ok(previewProcessSubmission(plan).errors.some((issue) => issue.code === "PRIMARY_FLOW_BROKEN"));
});

test("uses PRIMARY as the canonical recipe role when one material is auxiliary in another step", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push(majorWithFlow("10", "8"));
  const first = plan.majorProcesses[0]!.steps[0]!.materials[0]!;
  first.formulaMaterialId = "BEEF-1";
  plan.majorProcesses[0]!.steps[1]!.materials.push({
    key: "beef-aux", sequence: 2, materialRole: "AUXILIARY", sourceType: "EXTERNAL",
    formulaMaterialId: "BEEF-1", materialCode: "BEEF", materialName: "鲜牛腩", materialState: "SOLID", weightKg: 0.2,
  });

  const [line] = aggregateProcessRecipe(plan);
  assert.equal(line?.canonicalMaterialRole, "PRIMARY");
  assert.equal(line?.weightKg, 10.2);
});

test("rejects a primary output before input and unresolved individual critical measurements in previews", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push({
    key: "major-reversed", sequence: 1, processName: "热加工", yieldBasis: "PRIMARY_INPUT", remark: "", inputs: [], outputs: [],
    steps: [
      { key: "out-first", sequence: 1, stepName: "预处理", stepType: "NORMAL", materials: [], outputs: [{
        key: "out-first-id", id: "out-first-id", sequence: 1, outputType: "INTERMEDIATE", outputName: "预处理产出",
        materialState: "SOLID", weightKg: 9, primaryOutput: true, continueFlow: true,
      }], controlPoints: [] },
      { key: "input-last", sequence: 2, stepName: "投料", stepType: "NORMAL", materials: [{
        key: "input-last-id", sequence: 1, materialRole: "PRIMARY", sourceType: "EXTERNAL", materialName: "牛肉",
        materialState: "SOLID", weightKg: 10,
      }], outputs: [], controlPoints: [{
        key: "critical", sequence: 1, controlType: "FOOD_SAFETY", importance: "CRITICAL", itemName: "中心温度",
        lowerLimit: 75, resolved: true, confirmedBy: "研发", measurements: [
          { key: "pass", sequence: 1, measuredValue: 76, result: "PASS", deviationAction: "记录", retestResult: "PASS" },
          { key: "fail", sequence: 2, measuredValue: 72, result: "FAIL" },
        ],
      }] },
    ],
  });

  assert.equal(calculateMajorProcessYield(plan.majorProcesses[0]!).mainYieldPercent, null);
  const preview = previewProcessSubmission(plan);
  assert.ok(preview.errors.some((issue) => issue.code === "MAJOR_PRIMARY_FLOW_INVALID"));
  assert.ok(preview.errors.some((issue) => issue.code === "CRITICAL_CONTROL_UNRESOLVED"));
});

test("allows confirmed passing critical controls but blocks unresolved deviations in previews", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses.push(majorWithFlow("10", "8"));
  const firstStep = plan.majorProcesses[0]!.steps[0]!;
  firstStep.controlPoints = [{
    key: "passing-critical", sequence: 1, controlType: "FOOD_SAFETY", importance: "CRITICAL", itemName: "中心温度",
    lowerLimit: 75, resolved: false, confirmedBy: "研发", measurements: [
      { key: "passing-measurement", sequence: 1, measuredValue: 76, result: "PASS" },
    ],
  }];

  assert.ok(!previewProcessSubmission(plan).errors.some((issue) => issue.code === "CRITICAL_CONTROL_UNRESOLVED"));

  firstStep.controlPoints = [{
    key: "unresolved-critical", sequence: 1, controlType: "FOOD_SAFETY", importance: "CRITICAL", itemName: "中心温度",
    lowerLimit: 75, resolved: false, confirmedBy: "研发", measurements: [
      { key: "failed-measurement", sequence: 1, measuredValue: 72, result: "FAIL", deviationAction: "继续加热", retestResult: "PASS" },
    ],
  }];
  assert.ok(previewProcessSubmission(plan).errors.some((issue) => issue.code === "CRITICAL_CONTROL_UNRESOLVED"));
});

test("major balance includes terminal side outputs from earlier steps", () => {
  const major = majorWithFlow("10", "8");
  major.steps[0]!.outputs!.push({
    key: "waste-early", id: "waste-early", sequence: 2, outputType: "WASTE", outputName: "前段损耗",
    materialState: "SOLID", weightKg: 1, primaryOutput: false, continueFlow: false,
  });
  major.steps[1]!.outputs!.push({
    key: "waste-last", id: "waste-last", sequence: 2, outputType: "WASTE", outputName: "后段损耗",
    materialState: "SOLID", weightKg: 1, primaryOutput: false, continueFlow: false,
  });

  assert.equal(calculateMajorProcessYield(major).balanceDifferenceKg, 0);
});

test("formal preview blocks forks, zero primary weights, and undocumented empty NONE majors", () => {
  const forked = createEmptyProcessPlan();
  const major = majorWithFlow("10", "8");
  major.steps.push({
    key: "fork-step", sequence: 3, stepName: "分叉", stepType: "NORMAL", materials: [{
      key: "fork-input", sequence: 1, materialRole: "PRIMARY", materialName: "牛肉", materialState: "SOLID",
      weightKg: 9, sourceType: "STEP_OUTPUT", sourceStepOutputId: major.steps[0]!.outputs![0]!.key,
    }], outputs: [{ key: "fork-output", id: "fork-output", sequence: 1, outputType: "FINISHED", outputName: "分叉产出",
      materialState: "SOLID", weightKg: 7, primaryOutput: true, continueFlow: false }], controlPoints: [],
  });
  forked.majorProcesses.push(major);
  assert.ok(previewProcessSubmission(forked).errors.some((error) => error.code === "PRIMARY_FLOW_BROKEN"));

  const zero = createEmptyProcessPlan();
  zero.majorProcesses.push(majorWithFlow("0", "1"));
  assert.ok(previewProcessSubmission(zero).errors.some((error) => error.code === "MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED"));

  const emptyNone = createEmptyProcessPlan();
  emptyNone.majorProcesses.push(majorWithFlow("10", "8"), {
    key: "empty-none", sequence: 2, processCode: "HOLD", processName: "静置", yieldBasis: "NONE", remark: "",
    steps: [], inputs: [], outputs: [],
  });
  const noneErrors = previewProcessSubmission(emptyNone).errors.map((error) => error.code);
  assert.ok(noneErrors.includes("MAJOR_STEP_REQUIRED"));
  assert.ok(noneErrors.includes("MAJOR_YIELD_EXCLUSION_REASON_REQUIRED"));

  const zeroLegacy = createEmptyProcessPlan();
  zeroLegacy.majorProcesses.push(majorWithFlow("10", "8"), {
    key: "legacy-zero", sequence: 2, processCode: "LEGACY", processName: "旧工序", yieldBasis: "PRIMARY_INPUT", remark: "旧数据补录",
    steps: [],
    inputs: [{ key: "legacy-in", sequence: 1, inputRole: "PRIMARY", materialName: "牛肉", weightKg: 0 }],
    outputs: [{ key: "legacy-out", sequence: 1, outputType: "QUALIFIED", weightKg: 0 }],
  });
  const legacyErrors = previewProcessSubmission(zeroLegacy).errors.map((error) => error.code);
  assert.ok(legacyErrors.includes("MAJOR_PRIMARY_INPUT_WEIGHT_REQUIRED"));
  assert.ok(legacyErrors.includes("MAJOR_PRIMARY_OUTPUT_WEIGHT_REQUIRED"));
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
