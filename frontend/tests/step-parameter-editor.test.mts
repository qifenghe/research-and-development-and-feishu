import assert from "node:assert/strict";
import test from "node:test";
import { parameterTemplates, summaryParameters } from "../apps/pc/src/components/process/stepParameterTemplates.ts";
test("operation templates provide flexible definitions, never production settings", () => {
  const tumbling = parameterTemplates("滚揉");
  assert.ok(["滚筒转速","真空压力","运行时间","暂停时间"].every(name => tumbling.some(p => p.name === name)));
  assert.ok(tumbling.every(p => p.actualValue == null && p.lowerLimit == null && p.upperLimit == null));
  assert.equal(tumbling.find(p => p.name === "真空压力").measurementBasis,undefined);
  assert.equal(summaryParameters(tumbling.map(p => ({...p,showInSummary:true}))).length,3);
  assert.ok(parameterTemplates("切块").some(p => p.name === "块高" && p.unit === "mm"));
  assert.notEqual(parameterTemplates("滚揉")[0].key,tumbling[0].key);
});
