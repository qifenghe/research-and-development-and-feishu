import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeFinishedOutput,switchFinishedOutputUnit} from '../packages/shared/src/finished-output.ts';
test('kg uses precise weight once and preserves missing values',()=>{
 assert.deepEqual(normalizeFinishedOutput(12.345,99,'kg'),{weightKg:12.345,quantity:12.345,unit:'kg'});
 assert.equal(normalizeFinishedOutput(null,99,'kg').quantity,null);
 assert.equal(normalizeFinishedOutput(89,178,'袋').quantity,178);
});
test('switching kg to counted packages never copies kilograms into bag count',()=>{
 assert.equal(switchFinishedOutputUnit({weightKg:12.345,quantity:12.345,unit:'kg'},'袋').quantity,null);
 assert.equal(switchFinishedOutputUnit({weightKg:12.345,quantity:178,unit:'袋'},'kg').quantity,12.345);
});
