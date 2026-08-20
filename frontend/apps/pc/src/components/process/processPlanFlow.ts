import type { ProcessPlanDraft } from "../../../../../packages/shared/src/process-plan";

export function previousFlowOutputs(plan: ProcessPlanDraft, majorKey: string, stepKey: string, primaryOnly = false) {
  const normalized = structuredClone(plan);
  const ordered = [...normalized.majorProcesses].sort((left,right)=>left.sequence-right.sequence).flatMap(major => [...major.steps].sort((left,right)=>left.sequence-right.sequence).map(step => ({ major, step })));
  const index = ordered.findIndex(ref => ref.major.key === majorKey && ref.step.key === stepKey);
  return ordered.slice(0, Math.max(0, index)).flatMap(ref => (ref.step.outputs || [])
    .filter(output => output.continueFlow && (!primaryOnly || output.primaryOutput))
    .map(output => ({ output, major: ref.major, step: ref.step })));
}

export function clearBrokenFlowReferences(plan: ProcessPlanDraft): { plan: ProcessPlanDraft; cleared: number } {
  const next = structuredClone(plan);
  const ids = new Set(next.majorProcesses.flatMap(major => major.steps.flatMap(step => (step.outputs || []).filter(output => output.continueFlow).map(output => output.id || output.key))));
  let cleared = 0;
  next.majorProcesses.forEach(major => major.steps.forEach(step => step.materials.forEach(material => {
    if (material.sourceType === "STEP_OUTPUT" && (!material.sourceStepOutputId || !ids.has(material.sourceStepOutputId))) {
      material.sourceType = "EXTERNAL";
      delete material.sourceStepOutputId;
      cleared++;
    }
  })));
  return { plan: next, cleared };
}
