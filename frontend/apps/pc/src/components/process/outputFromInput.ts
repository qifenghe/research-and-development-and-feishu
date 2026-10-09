import { nextProcessKey, type ProcessStepMaterialDraft, type StepOutputDraft } from '../../../../../packages/shared/src/process-plan.ts';
export function outputFromInput(input: ProcessStepMaterialDraft, sequence: number, hasPrimaryOutput: boolean): StepOutputDraft {
  const key = nextProcessKey('output');
  return { id:key, key, sequence, outputType:'INTERMEDIATE', outputName:input.materialName,
    materialState:input.materialState, primaryOutput:input.materialRole === 'PRIMARY' && !hasPrimaryOutput,
    continueFlow:true };
}
