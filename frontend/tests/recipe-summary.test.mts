import assert from "node:assert/strict";
import test from "node:test";
import { createEmptyProcessPlan } from "../packages/shared/src/process-plan.ts";
import { buildRecipeSummary } from "../apps/pc/src/components/process/recipeSummary.ts";

function fixture() {
  const plan = createEmptyProcessPlan();
  plan.majorProcesses = [{ key:"m",sequence:1,processCode:"M",processName:"预处理",yieldBasis:"PRIMARY_INPUT",inputs:[],outputs:[],steps:[
    {key:"s1",sequence:1,stepCode:"S1",stepName:"加入",stepType:"NORMAL",materials:[
      {key:"a",sequence:1,sourceType:"EXTERNAL",materialRole:"PRIMARY",materialCode:"A",materialName:"牛肉",weightKg:2,additionTiming:"水沸后",additionMethod:"分批加入"},
    ],outputs:[],controlPoints:[]},
    {key:"s2",sequence:2,stepCode:"S2",stepName:"再次加入",stepType:"NORMAL",materials:[
      {key:"b",sequence:1,sourceType:"EXTERNAL",materialRole:"PRIMARY",materialCode:"A",materialName:"牛肉",weightKg:3},
      {key:"c",sequence:2,sourceType:"STEP_OUTPUT",materialRole:"PRIMARY",materialName:"牛肉",weightKg:5},
    ],outputs:[],controlPoints:[]},
  ]}];
  return plan;
}
test("recipe groups repeated external additions with traceability, excludes intermediate transfer", () => {
  const summary=buildRecipeSummary(fixture());
  assert.equal(summary.externalTotalKg,5);
  assert.equal(summary.rows[0].weightKg,5);
  assert.equal(summary.rows[0].proportionPercent,100);
  assert.equal(summary.rows[0].occurrences.length,2);
  assert.equal(summary.rows[0].occurrences[0].additionTiming,"水沸后");
  assert.equal(summary.rows[0].occurrences[0].additionMethod,"分批加入");
});
test("recipe keeps distinct material identities and never replaces missing weight with zero", () => {
  const plan=fixture();
  const material=plan.majorProcesses[0].steps[1].materials[0];
  material.materialCode="B";
  material.weightKg=undefined;
  const summary=buildRecipeSummary(plan);
  assert.equal(summary.rows.length,2);
  assert.equal(summary.externalTotalKg,null);
  assert.equal(summary.rows[1].weightKg,null);
  assert.ok(summary.rows.every(row=>row.proportionPercent===null));
  material.weightKg=0;
  assert.equal(buildRecipeSummary(plan).rows[1].weightKg,0);
  assert.equal(buildRecipeSummary(plan).externalTotalKg,2);
});
test("empty recipe and ambiguous source sequences do not claim complete totals", () => {
  assert.equal(buildRecipeSummary(createEmptyProcessPlan()).externalTotalKg,null);
  const plan=fixture(); plan.majorProcesses[0].steps[1].sequence=1;
  assert.equal(buildRecipeSummary(plan).rows[0].weightKg,null);
});
