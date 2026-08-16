import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { calculateMajorProcessYield, createEmptyProcessPlan, processPlanToLegacySteps } from "../packages/shared/src/process-plan.ts";

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
