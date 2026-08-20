import assert from "node:assert/strict";
import test from "node:test";
import { diffProcessPlans } from "../apps/pc/src/components/process/processPlanDiff.ts";
import { createEmptyProcessPlan, type ProcessPlanDraft } from "../packages/shared/src/process-plan.ts";

function plan(): ProcessPlanDraft {
  return {
    ...createEmptyProcessPlan(),
    majorProcesses: [{
      key: "major-1",
      sequence: 1,
      processCode: "HEAT",
      processName: "热加工",
      description: "旧说明",
      yieldBasis: "PRIMARY_INPUT",
      remark: "旧差异说明",
      inputs: [],
      outputs: [],
      steps: [{
        key: "step-1",
        sequence: 1,
        stepCode: "BOIL",
        stepName: "煮制",
        stepType: "NORMAL",
        instruction: "加热",
        materials: [{
          key: "material-1",
          sequence: 1,
          materialRole: "PRIMARY",
          materialCode: "M001",
          materialName: "牛肉",
          materialState: "SOLID",
          sourceType: "EXTERNAL",
          weightKg: 10,
        }],
        outputs: [{
          id: "output-1",
          key: "output-1",
          sequence: 1,
          outputType: "FINISHED",
          outputName: "熟牛肉",
          materialState: "SOLID",
          weightKg: 8,
          primaryOutput: true,
          continueFlow: false,
        }],
        controlPoints: [],
      }],
    }],
  };
}

test("returns stable human-readable added, removed and changed plan differences", () => {
  const source = plan();
  const current = structuredClone(source);
  current.majorProcesses[0]!.description = "新说明";
  current.majorProcesses[0]!.steps[0]!.materials[0]!.weightKg = 9.5;
  current.majorProcesses[0]!.steps[0]!.outputs = [];
  current.majorProcesses.push({
    key: "major-2",
    sequence: 2,
    processName: "包装",
    yieldBasis: "NONE",
    inputs: [],
    outputs: [],
    steps: [],
  });

  const first = diffProcessPlans(source, current);
  const second = diffProcessPlans(
    JSON.parse(JSON.stringify(source)),
    JSON.parse(JSON.stringify(current)),
  );

  assert.deepEqual(second, first);
  assert.ok(first.some(item => item.type === "CHANGED" && item.path.includes("工序说明") && item.before === "旧说明" && item.after === "新说明"));
  assert.ok(first.some(item => item.type === "CHANGED" && item.path.includes("投料") && item.path.includes("重量") && item.after === "9.5 kg"));
  assert.ok(first.some(item => item.type === "REMOVED" && item.path.includes("产出") && item.before?.includes("熟牛肉")));
  assert.ok(first.some(item => item.type === "ADDED" && item.path.includes("大工序") && item.after?.includes("包装")));
});

test("ignores transport metadata while comparing immutable process content", () => {
  const source = plan();
  const current = structuredClone(source);
  source.id = "plan-old";
  source.versionNo = 1;
  source.status = "SUBMITTED";
  current.id = "plan-new";
  current.versionNo = 2;
  current.status = "DRAFT";
  current.sourceRevisionId = "revision-1";
  current.changeReason = "修订";

  assert.deepEqual(diffProcessPlans(source, current), []);
});

test("treats regenerated IDs at every nested level as the same semantic snapshot", () => {
  const source = plan();
  const step = source.majorProcesses[0]!.steps[0]!;
  source.majorProcesses[0]!.id = "major-old";
  step.id = "step-old";
  step.materials[0]!.id = "material-old";
  step.outputs![0]!.id = "output-old";
  step.outputs![0]!.key = "output-old";
  step.controlPoints = [{
    id: "control-old", key: "control-old", sequence: 1, controlType: "TEMPERATURE",
    importance: "CRITICAL", itemName: "中心温度", targetValue: "85", unit: "℃",
    measurements: [{ id: "measure-old", key: "measure-old", sequence: 1, measuredValue: "86", result: "PASS" }],
  }];
  step.materials.push({
    id: "consumer-old", key: "consumer-old", sequence: 2, materialRole: "AUXILIARY",
    materialName: "回用料", materialState: "SOLID", sourceType: "STEP_OUTPUT",
    sourceStepOutputId: "output-old", weightKg: 1,
  });

  const current = structuredClone(source);
  current.majorProcesses[0]!.id = "major-new";
  current.majorProcesses[0]!.key = "major-new";
  const currentStep = current.majorProcesses[0]!.steps[0]!;
  currentStep.id = "step-new";
  currentStep.key = "step-new";
  currentStep.materials[0]!.id = "material-new";
  currentStep.materials[0]!.key = "material-new";
  currentStep.outputs![0]!.id = "output-new";
  currentStep.outputs![0]!.key = "output-new";
  currentStep.materials[1]!.id = "consumer-new";
  currentStep.materials[1]!.key = "consumer-new";
  currentStep.materials[1]!.sourceStepOutputId = "output-new";
  currentStep.controlPoints[0]!.id = "control-new";
  currentStep.controlPoints[0]!.key = "control-new";
  currentStep.controlPoints[0]!.measurements[0]!.id = "measure-new";
  currentStep.controlPoints[0]!.measurements[0]!.key = "measure-new";

  assert.deepEqual(diffProcessPlans(source, current), []);

  currentStep.controlPoints[0]!.measurements[0]!.measuredValue = "84";
  const changed = diffProcessPlans(source, current);
  assert.equal(changed.length, 1);
  assert.equal(changed[0]?.type, "CHANGED");
  assert.match(changed[0]?.path || "", /关键控制点.*实测.*实测值/);
  assert.equal(changed[0]?.before, "86");
  assert.equal(changed[0]?.after, "84");
});
