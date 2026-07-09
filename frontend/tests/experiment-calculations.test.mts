import assert from "node:assert/strict";
import test from "node:test";

import {
  finishedYieldPercent,
  formulaRatios,
  processLoss,
} from "../packages/shared/src/experiment/calculations.ts";

test("calculates formula ratios from actual weights", () => {
  assert.deepEqual(formulaRatios([12.5, 2.5]), [83.333333, 16.666667]);
});

test("calculates process loss after residual material", () => {
  assert.deepEqual(processLoss(10, 8, 0.5), { lossWeightKg: 1.5, lossRate: 15 });
});

test("calculates yield from the primary raw material", () => {
  assert.equal(finishedYieldPercent(10, 12.5), 80);
});

test("rejects process output plus residual beyond input", () => {
  assert.throws(() => processLoss(10, 9, 2), /出成与余料不能超过投入重量/);
});
