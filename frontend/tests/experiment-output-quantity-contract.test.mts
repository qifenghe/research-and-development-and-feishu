import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { normalizePositiveIntegerQuantity } from "../packages/shared/src/experiment/calculations.ts";

const mobileForm = readFileSync(new URL("../apps/mobile/src/views/ExperimentFormView.vue", import.meta.url), "utf8");
const pcForm = readFileSync(new URL("../apps/pc/src/views/rnd/ExperimentFormView.vue", import.meta.url), "utf8");

test("maps only positive whole-number finished quantities to the API payload", () => {
  assert.equal(normalizePositiveIntegerQuantity("17"), 17);
  assert.equal(normalizePositiveIntegerQuantity(17), 17);
  assert.equal(normalizePositiveIntegerQuantity("17.5"), undefined);
  assert.equal(normalizePositiveIntegerQuantity(0), undefined);
});

test("mobile form restricts and validates finished quantity as a positive integer", () => {
  assert.match(mobileForm, /v-model="form\.finishedOutputQuantity"[^>]*type="digit"[^>]*inputmode="numeric"/);
  assert.match(mobileForm, /normalizePositiveIntegerQuantity\(form\.finishedOutputQuantity\)/);
  assert.match(mobileForm, /成品数量必须为正整数/);
});

test("PC form restricts and validates finished quantity as a positive integer", () => {
  assert.match(pcForm, /v-model:value="form\.finishedOutputQuantity"[^>]*:min="1"[^>]*:precision="0"[^>]*:step="1"/);
  assert.match(pcForm, /normalizePositiveIntegerQuantity\(form\.finishedOutputQuantity\)/);
  assert.match(pcForm, /成品数量必须为正整数/);
});
