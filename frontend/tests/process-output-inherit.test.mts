import assert from 'node:assert/strict';
import test from 'node:test';
import { outputFromInput } from '../apps/pc/src/components/process/outputFromInput.ts';
test('inheriting input preserves unchanged name and state without inventing measured weight', () => {
 const input={key:'m',sequence:1,materialRole:'PRIMARY' as const,materialName:'牛肉',materialState:'SOLID' as const,weightKg:100,sourceType:'EXTERNAL' as const,materialCode:'BEEF',catalogMaterialId:'MC1'};
 const output=outputFromInput(input,2,false);
 assert.equal(output.outputName,'牛肉');
 assert.equal(output.materialState,'SOLID');
 assert.equal(output.primaryOutput,true);
 assert.equal(output.continueFlow,true);
 assert.equal(output.weightKg,undefined);
 assert.equal(output.sequence,2);
 assert.equal('catalogMaterialId' in output,false);
 assert.notEqual(outputFromInput(input,3,true).key,output.key);
 assert.equal(outputFromInput(input,3,true).primaryOutput,false);
 assert.equal(input.weightKg,100);
});
