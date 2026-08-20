import assert from "node:assert/strict";
import test from "node:test";
import { reactive, shallowReactive } from "../apps/pc/node_modules/vue/index.mjs";
import { cloneVueValue } from "../apps/pc/src/components/process/cloneVueValue.ts";

test("clones a Vue reactive graph into an independent transport value", () => {
  const source = reactive({ plan: { majorProcesses: [{ processName: "服务端大工序" }] } });

  const cloned = cloneVueValue(source);

  assert.deepEqual(cloned, { plan: { majorProcesses: [{ processName: "服务端大工序" }] } });
  cloned.plan.majorProcesses[0]!.processName = "本地修改";
  assert.equal(source.plan.majorProcesses[0]!.processName, "服务端大工序");
});

test("deeply unwraps nested Vue proxies before structured cloning", () => {
  const nested = reactive({ processName: "嵌套代理" });
  const source = shallowReactive({ majorProcesses: [nested] });

  assert.deepEqual(cloneVueValue(source), { majorProcesses: [{ processName: "嵌套代理" }] });
});
