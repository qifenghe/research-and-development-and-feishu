import { calculateMajorProcessYield, type MajorProcessDraft, type ProcessPlanDraft } from "../../../../../packages/shared/src/process-plan.ts";

export function observedTotal(items: Array<{ weightKg?: number }>): number | null {
  return items.length && items.every(item => item.weightKg != null && Number.isFinite(item.weightKg) && item.weightKg >= 0)
    ? items.reduce((sum, item) => sum + item.weightKg!, 0) : null;
}
export function externalActualTotal(plan: ProcessPlanDraft) {
  return observedTotal(plan.majorProcesses.flatMap(major => major.steps.flatMap(step => step.materials.filter(material => material.sourceType !== "STEP_OUTPUT"))));
}
export function majorActualSummary(major: MajorProcessDraft) {
  const result = calculateMajorProcessYield(major);
  const steps = [...major.steps].sort((a, b) => a.sequence - b.sequence);
  const layered = steps.some(step => step.materials.some(m => m.materialRole === "PRIMARY") || step.outputs?.some(o => o.primaryOutput));
  const primarySteps = steps.filter(step => step.materials.some(m => m.materialRole === "PRIMARY") || step.outputs?.some(o => o.primaryOutput));
  const primary = layered ? primarySteps[0]?.materials.filter(m => m.materialRole === "PRIMARY") || [] : major.inputs.filter(i => i.inputRole === "PRIMARY");
  const terminal = layered ? primarySteps.at(-1)?.outputs?.filter(o => o.primaryOutput) || [] : major.outputs.filter(o => o.outputType === "QUALIFIED");
  const allInputs = layered ? steps.flatMap(step => step.materials) : major.inputs;
  const allOutputs = layered ? steps.flatMap(step => step.outputs || []) : major.outputs;
  return { ...result, primaryInputWeightKg: observedTotal(primary), qualifiedOutputWeightKg: observedTotal(terminal),
    balanceDifferenceKg: observedTotal(allInputs) == null || observedTotal(allOutputs) == null ? null : result.balanceDifferenceKg };
}
export function actualKg(value: number | null) { return value == null ? "待补充" : `${Number(value.toFixed(8))}kg`; }
