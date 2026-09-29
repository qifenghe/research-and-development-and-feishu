import assert from "node:assert/strict";
import test from "node:test";

import { normalizePositiveIntegerQuantity } from "../packages/shared/src/experiment/calculations.ts";


test("maps only positive whole-number finished quantities to the API payload", () => {
  assert.equal(normalizePositiveIntegerQuantity("17"), 17);
  assert.equal(normalizePositiveIntegerQuantity(17), 17);
  assert.equal(normalizePositiveIntegerQuantity("17.5"), undefined);
  assert.equal(normalizePositiveIntegerQuantity(0), undefined);
});
