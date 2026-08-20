import assert from "node:assert/strict";
import test from "node:test";
import { commitLatestRouteRequest } from "../apps/mobile/src/views/latestRouteRequest.ts";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

test("a late task A response cannot replace task B after an in-place route change", async () => {
  let generation = 1;
  let visibleTask = "";
  const taskA = deferred<string>();
  const taskB = deferred<string>();
  const loadA = commitLatestRouteRequest(1, () => generation, () => taskA.promise, value => { visibleTask = value; });
  generation = 2;
  const loadB = commitLatestRouteRequest(2, () => generation, () => taskB.promise, value => { visibleTask = value; });

  taskB.resolve("TASK-B");
  assert.equal(await loadB, true);
  taskA.resolve("TASK-A");
  assert.equal(await loadA, false);
  assert.equal(visibleTask, "TASK-B");
});
