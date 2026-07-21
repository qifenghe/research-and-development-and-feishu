import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const mobile = fs.readFileSync(new URL("../apps/mobile/src/views/ExperimentFormView.vue", import.meta.url), "utf8");
const pc = fs.readFileSync(new URL("../apps/pc/src/views/rnd/ExperimentFormView.vue", import.meta.url), "utf8");

for (const [name, source] of [["mobile", mobile], ["pc", pc]] as const) {
  test(`${name} experiment form removes quick sampling`, () => {
    assert.doesNotMatch(source, /快速打样|startQuickMode/);
  });

  test(`${name} experiment form supports generalized yield modes`, () => {
    assert.match(source, /yieldCalculationMode/);
    assert.match(source, /TOTAL_PICKING_WEIGHT/);
    assert.match(source, /研发参考得率/);
  });

  test(`${name} experiment form persists local drafts`, () => {
    assert.match(source, /writeExperimentDraft/);
    assert.match(source, /readExperimentDraft/);
    assert.match(source, /autoSave/);
  });
}
