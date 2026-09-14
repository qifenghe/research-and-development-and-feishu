import test from 'node:test';
import assert from 'node:assert/strict';
import {calculateMajorProcessYield, calculateBatchYield, previewProcessSubmission} from '../packages/shared/src/process-plan.ts';
const major = (sequence: number, source: string | undefined, input: number, output: number) => ({
  key: `g${sequence}`, sequence, processName: '工序', yieldBasis: 'PRIMARY_INPUT' as const, remark: '工艺损耗', inputs: [], outputs: [],
  steps: [{key: `s${sequence}`, sequence: 1, stepName: '操作', stepType: 'NORMAL',
    materials: [{key: `m${sequence}`, sequence: 1, materialRole: 'PRIMARY' as const, materialName: '肉', materialState: 'SOLID' as const, weightKg: input, sourceType: source ? 'STEP_OUTPUT' as const : 'EXTERNAL' as const, sourceStepOutputId: source}],
    outputs: [{key: `o${sequence}`, sequence: 1, outputType: 'FINISHED' as const, outputName: '肉', materialState: 'SOLID' as const, weightKg: output, primaryOutput: true, continueFlow: true}], controlPoints: []}]
});
test('missing terminal weight never falls back to an earlier yield', () => {
  const first = major(1, undefined, 100, 90);
  const tail = major(2, 'o1', 90, 72).steps[0];
  tail.sequence = 2;
  const combined = {...first, steps: [first.steps[0], {...tail, outputs: [{...tail.outputs[0], weightKg: undefined}]}]};
  const plan = {versionNo:1,status:'DRAFT',majorProcesses:[combined]};
  assert.equal(previewProcessSubmission(plan).ready, false);
  assert.equal(calculateBatchYield(plan), null);
  assert.equal(calculateMajorProcessYield(combined).mainYieldPercent, null);
});
test('counted majors must participate in layered main-material flow', () => {
  const legacy = {...major(2, undefined, 100, 80), steps: []};
  const plan = {versionNo:1,status:'DRAFT',majorProcesses:[major(1,undefined,100,90),legacy]};
  assert.ok(previewProcessSubmission(plan).errors.some(item => item.code === 'MAJOR_PRIMARY_CHAIN_REQUIRED'));
});
test('transfers count at the major boundary and valid continuous yield is 72%', () => {
  const second = major(2, 'o1', 90, 72);
  assert.equal(calculateMajorProcessYield(second).totalInputWeightKg, 90);
  assert.equal(calculateMajorProcessYield(second).balanceDifferenceKg, 18);
  assert.equal(calculateBatchYield({versionNo: 1, status:'DRAFT', majorProcesses:[major(1,undefined,100,90),second]}),72);
});
test('mismatched transfers and independent routes cannot publish a misleading yield', () => {
  for(const [second, code] of [[major(2,'o1',100,72),'FLOW_WEIGHT_MISMATCH'],[major(2,undefined,100,80),'BATCH_PRIMARY_CHAIN_REQUIRED']] as const) {
    const plan = {versionNo:1,status:'DRAFT',majorProcesses:[major(1,undefined,100,90),second]};
    assert.ok(previewProcessSubmission(plan).errors.some(item => item.code === code));
    assert.equal(calculateBatchYield(plan),null);
  }
});
