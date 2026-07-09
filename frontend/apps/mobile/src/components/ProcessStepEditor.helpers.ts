import type { ExperimentProcessStep, RemainingDisposition } from "@rnd/shared";

export type EditableProcessStep = {
  processName: string;
  beforeWeightKg: string;
  afterWeightKg: string;
  remainingWeightKg: string;
  remainingDisposition: RemainingDisposition;
  remark: string;
};

export function blankProcessStep(): EditableProcessStep {
  return {
    processName: "",
    beforeWeightKg: "",
    afterWeightKg: "",
    remainingWeightKg: "",
    remainingDisposition: "REUSE",
    remark: "",
  };
}

export function moveProcessStep(
  steps: EditableProcessStep[],
  fromIndex: number,
  toIndex: number,
): EditableProcessStep[] {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= steps.length || toIndex >= steps.length) {
    return [...steps];
  }
  const next = [...steps];
  const [step] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, step);
  return next;
}

export function copyProcessStep(steps: EditableProcessStep[], index: number): EditableProcessStep[] {
  const source = steps[index];
  if (!source) return [...steps];
  const next = [...steps];
  next.splice(index + 1, 0, { ...source, processName: `${source.processName}（副本）` });
  return next;
}

export function removeProcessStep(steps: EditableProcessStep[], index: number): EditableProcessStep[] {
  if (steps.length <= 1 || index < 0 || index >= steps.length) return [...steps];
  return steps.filter((_, currentIndex) => currentIndex !== index);
}

export function lossRatePercent(step: EditableProcessStep): string | null {
  const before = Number(step.beforeWeightKg);
  const after = Number(step.afterWeightKg);
  const residual = Number(step.remainingWeightKg || 0);
  if (!before || before <= 0 || Number.isNaN(after) || Number.isNaN(residual)) return null;
  try {
    return calculateProcessLoss(before, after, residual).lossRate.toFixed(1);
  } catch {
    return null;
  }
}

export function lossWeightKg(step: EditableProcessStep): string | null {
  const before = Number(step.beforeWeightKg);
  const after = Number(step.afterWeightKg);
  const residual = Number(step.remainingWeightKg || 0);
  if (!before || before <= 0 || Number.isNaN(after) || Number.isNaN(residual)) return null;
  try {
    return calculateProcessLoss(before, after, residual).lossWeightKg.toFixed(3);
  } catch {
    return null;
  }
}

export function toProcessSteps(steps: EditableProcessStep[]): ExperimentProcessStep[] {
  return steps
    .filter((s) => s.processName.trim())
    .map((step, index) => {
      const before = Number(step.beforeWeightKg || 0);
      const after = Number(step.afterWeightKg || 0);
      const residual = Number(step.remainingWeightKg || 0);
      const loss =
        before > 0 && !Number.isNaN(after) && !Number.isNaN(residual)
          ? calculateProcessLoss(before, after, residual)
          : undefined;
      return {
        sequence: index + 1,
        processName: step.processName.trim(),
        beforeWeightKg: before || undefined,
        afterWeightKg: after || undefined,
        remainingWeightKg: residual || undefined,
        remainingDisposition: step.remainingDisposition,
        lossWeightKg: loss?.lossWeightKg,
        lossRate: loss?.lossRate,
        remark: step.remark.trim() || undefined,
      };
    });
}

function calculateProcessLoss(inputWeight: number, outputWeight: number, residualWeight = 0) {
  const lossWeightKg = inputWeight - outputWeight - residualWeight;
  if (lossWeightKg < 0) {
    throw new Error("下一步出成与余料不能大于本步投入");
  }
  return {
    lossWeightKg,
    lossRate: inputWeight > 0 ? (lossWeightKg / inputWeight) * 100 : 0,
  };
}

export function fromProcessSteps(steps: ExperimentProcessStep[]): EditableProcessStep[] {
  if (!steps.length) return [blankProcessStep()];
  return steps.map((step) => ({
    processName: step.processName,
    beforeWeightKg: step.beforeWeightKg != null ? String(step.beforeWeightKg) : "",
    afterWeightKg: step.afterWeightKg != null ? String(step.afterWeightKg) : "",
    remainingWeightKg: step.remainingWeightKg != null ? String(step.remainingWeightKg) : "",
    remainingDisposition: step.remainingDisposition ?? "REUSE",
    remark: step.remark ?? "",
  }));
}
