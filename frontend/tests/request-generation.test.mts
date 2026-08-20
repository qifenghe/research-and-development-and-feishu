import assert from "node:assert/strict";
import test from "node:test";
import { RequestGeneration } from "../apps/pc/src/components/process/requestGeneration.ts";

test("a new route generation invalidates every earlier await continuation", () => {
  const requests = new RequestGeneration();
  const routeA = requests.next();
  assert.equal(requests.isCurrent(routeA), true);
  const routeB = requests.next();
  assert.equal(requests.isCurrent(routeA), false);
  assert.equal(requests.isCurrent(routeB), true);
  requests.invalidate();
  assert.equal(requests.isCurrent(routeB), false);
});
