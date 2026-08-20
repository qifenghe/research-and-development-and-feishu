import assert from "node:assert/strict";
import test from "node:test";
import { clearBrokenFlowReferences, previousFlowOutputs } from "../apps/pc/src/components/process/processPlanFlow.ts";
import { createEmptyProcessPlan } from "../packages/shared/src/process-plan.ts";

test("offers continuing outputs from earlier majors and repairs a deleted producer reference", () => {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses = [
    { key:"m1",sequence:1,processName:"前段",yieldBasis:"PRIMARY_INPUT",inputs:[],outputs:[],steps:[{key:"s1",sequence:1,stepName:"产出",stepType:"NORMAL",materials:[],outputs:[{key:"o1",id:"o1",sequence:1,outputType:"INTERMEDIATE",outputName:"中间料",materialState:"SOLID",primaryOutput:true,continueFlow:true}],controlPoints:[]}] },
    { key:"m2",sequence:2,processName:"后段",yieldBasis:"PRIMARY_INPUT",inputs:[],outputs:[],steps:[{key:"s2",sequence:1,stepName:"使用",stepType:"NORMAL",materials:[{key:"x",sequence:1,materialRole:"PRIMARY",materialName:"中间料",materialState:"SOLID",sourceType:"STEP_OUTPUT",sourceStepOutputId:"o1"}],outputs:[],controlPoints:[]}] },
  ];
  assert.equal(previousFlowOutputs(plan,"m2","s2",true)[0]?.output.id,"o1");
  plan.majorProcesses[0]!.steps[0]!.outputs = [];
  const repaired = clearBrokenFlowReferences(plan);
  assert.equal(repaired.cleared,1);
  assert.equal(repaired.plan.majorProcesses[1]!.steps[0]!.materials[0]!.sourceType,"EXTERNAL");
});
