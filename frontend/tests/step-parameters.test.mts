import assert from "node:assert/strict";
import test from "node:test";
import { effectiveStepParameters } from "../packages/shared/src/step-parameters.ts";

const legacy = { parameter1Name: "温度", parameter1Value: "97", parameter1Unit: "℃", parameter2Name: "计时起点", parameter2Value: "沸腾后" };
test("legacy parameter adapter preserves names values and numeric versus text types", () => {
  assert.deepEqual(effectiveStepParameters(legacy).map(p => [p.key,p.name,p.actualValue,p.valueType]), [["legacy-1","温度","97","NUMBER"],["legacy-2","计时起点","沸腾后","TEXT"]]);
});
test("explicit empty list overrides legacy data and arbitrary parameters survive", () => {
  assert.deepEqual(effectiveStepParameters({...legacy, parameters:[]}), []);
  const parameters = ["转速","压力","运行时间","暂停时间"].map((name,i)=>({key:String(i),name,valueType:"NUMBER" as const,actualValue:["8","-0.08","20","5"][i],showInSummary:false}));
  assert.deepEqual(effectiveStepParameters({...legacy,parameters}), parameters);
});
test("nameless recorded legacy value is not hidden", () => {
  assert.equal(effectiveStepParameters({parameter1Value:"97"})[0].actualValue,"97");
});
