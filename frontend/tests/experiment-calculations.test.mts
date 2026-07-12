import assert from "node:assert/strict";
import test from "node:test";

import {
  averageUnitWeightKg,
  calculatePricingPreview,
  finishedYieldPercent,
  formulaRatios,
  processLoss,
  referenceQuantity,
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

test("calculates a pricing preview for finished bags", () => {
  assert.deepEqual(calculatePricingPreview({
    primaryMaterialWeightKg: 10,
    ingredientWeightKg: 2,
    finishedOutputWeightKg: 8.5,
    finishedOutputQuantity: 17,
  }), {
    totalInputWeightKg: 12,
    primaryMaterialYieldPercent: 85,
    averageUnitWeightKg: 0.5,
    referenceQuantity: 17,
  });
});

test("calculates average unit weight and reference quantity", () => {
  assert.equal(averageUnitWeightKg(8.5, 17), 0.5);
  assert.equal(referenceQuantity(17), 17);
});

test("calculates process loss rate after residual material", () => {
  assert.deepEqual(processLoss(10, 8, 1), { lossWeightKg: 1, lossRate: 10 });
});

test("rejects process output plus residual beyond input", () => {
  assert.throws(() => processLoss(10, 9, 2), /出成与余料不能超过投入重量/);
});
