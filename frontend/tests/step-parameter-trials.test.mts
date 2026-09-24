import assert from "node:assert/strict";
import test from "node:test";
import { compareParameter } from "../apps/pc/src/components/trials/trialComparison.ts";
import { createStructureOnlyTrialSource, pruneParameterPlans } from "../apps/pc/src/components/trials/trialDraft.ts";
import { createEmptyProcessPlan } from "../packages/shared/src/process-plan.ts";

test("pruning migrates legacy plans before deleting their last parameter",()=>{
  const plan:any=createEmptyProcessPlan();
  plan.majorProcesses=[{key:"m",steps:[{key:"s",parameters:[],materials:[]}]}];
  assert.deepEqual(pruneParameterPlans(plan,null,{s:{parameter1Value:"90"}}),{s:{}});
});

const pressure = {key:"p",name:"真空压力",valueType:"NUMBER" as const,unit:"MPa",measurementBasis:"GAUGE",actualValue:"-0.08",showInSummary:true};
test("pressure comparisons require matching known basis and device percentage definition", () => {
  assert.equal(compareParameter(pressure,{...pressure,measurementBasis:"ABSOLUTE"}).comparable,false);
  assert.equal(compareParameter({...pressure,measurementBasis:undefined},{...pressure,measurementBasis:undefined}).comparable,false);
  assert.equal(compareParameter({...pressure,unit:"%",measurementBasis:"DEVICE_PERCENT"},{...pressure,unit:"%",measurementBasis:"DEVICE_PERCENT"}).comparable,false);
  assert.equal(compareParameter(pressure,{...pressure,actualValue:"-0.06"}).delta,0.02);
  assert.equal(compareParameter(pressure,{...pressure,actualValue:""}).delta,null);
  assert.equal(compareParameter({...pressure,name:"介质温度",unit:"℃"},{...pressure,name:"中心温度",unit:"℃"}).comparable,false);
});
test("structure copy clears all actual parameters and keeps plans; pruning removes deleted keys", () => {
  const plan=createEmptyProcessPlan();
  const major={key:"major",sequence:1,processCode:"TUMBLE",processName:"滚揉",yieldBasis:"PRIMARY_INPUT",inputs:[],outputs:[],steps:[]};
  const step={key:"step",sequence:1,stepCode:"S1",stepName:"滚揉",stepType:"NORMAL",materials:[],outputs:[],controlPoints:[],parameters:[]};
  step.parameters=[pressure,{...pressure,key:"rpm",name:"转速",actualValue:"0",unit:"rpm"}];
  major.steps=[step];plan.majorProcesses=[major];
  const copy=createStructureOnlyTrialSource(plan);
  assert.equal(copy.plan.majorProcesses[0].steps[0].parameters[0].actualValue,undefined);
  assert.equal(copy.plannedData.parameterValues[step.key].rpm,"0");
  copy.plan.majorProcesses[0].steps[0].parameters.splice(0,1);
  assert.deepEqual(pruneParameterPlans(copy.plan,copy.plannedData.parameterValues),{[step.key]:{rpm:"0"}});
});
