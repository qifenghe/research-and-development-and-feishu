import type { ExperimentProcessStep } from "./types";

export type ProcessMaterialRole = "PRIMARY" | "AUXILIARY" | "PROCESS_WATER";
export type ProcessOutputType = "QUALIFIED" | "REUSABLE" | "TAILING" | "SAMPLE" | "WASTE" | "HOLD";
export type ProcessStepType = "NORMAL" | "WEIGH" | "MATERIAL_CHANGE" | "SAMPLE" | "WASTE";

export interface ProcessStepMaterialDraft {
  id?: string;
  key: string;
  sequence: number;
  materialRole: ProcessMaterialRole;
  materialCode?: string;
  materialName: string;
  materialState: "SOLID" | "LIQUID" | "SEMI_SOLID";
  weightKg?: number;
  formulaMaterialId?: string;
  remark?: string;
}

export interface MinorProcessStepDraft {
  id?: string;
  key: string;
  sequence: number;
  stepCode?: string;
  stepName: string;
  stepType: ProcessStepType;
  parameter1Name?: string;
  parameter1Value?: string;
  parameter1Unit?: string;
  parameter2Name?: string;
  parameter2Value?: string;
  parameter2Unit?: string;
  equipment?: string;
  instruction?: string;
  materials: ProcessStepMaterialDraft[];
}

export interface ProcessInputDraft {
  id?: string;
  key: string;
  sequence: number;
  inputRole: ProcessMaterialRole;
  materialCode?: string;
  materialName: string;
  weightKg?: number;
  sourceStepMaterialId?: string;
}

export interface ProcessOutputDraft {
  id?: string;
  key: string;
  sequence: number;
  outputType: ProcessOutputType;
  weightKg?: number;
  remark?: string;
}

export interface MajorProcessDraft {
  id?: string;
  key: string;
  sequence: number;
  processCode?: string;
  processName: string;
  description?: string;
  yieldBasis: "PRIMARY_INPUT" | "TOTAL_INPUT" | "NONE";
  remark?: string;
  steps: MinorProcessStepDraft[];
  inputs: ProcessInputDraft[];
  outputs: ProcessOutputDraft[];
  yield?: ProcessYieldResult;
}

export interface ProcessYieldResult {
  primaryInputWeightKg: number;
  totalInputWeightKg: number;
  qualifiedOutputWeightKg: number;
  reusableOutputWeightKg: number;
  totalOutputWeightKg: number;
  mainYieldPercent: number | null;
  recoveryPercent: number | null;
  balanceDifferenceKg: number;
}

export interface ProcessPlanDraft {
  id?: string;
  experimentFormId?: string;
  versionNo: number;
  status: "DRAFT" | "LOCKED";
  majorProcesses: MajorProcessDraft[];
  batchYieldPercent?: number | null;
  legacy?: boolean;
}

let processKey = 1;
export const nextProcessKey = (prefix = "process") => `${prefix}-${Date.now()}-${processKey++}`;

export function createEmptyProcessPlan(): ProcessPlanDraft {
  return { versionNo: 0, status: "DRAFT", majorProcesses: [], batchYieldPercent: null, legacy: false };
}

export function calculateMajorProcessYield(process: MajorProcessDraft): ProcessYieldResult {
  const sum = (items: Array<{ weightKg?: number }>) => items.reduce((total, item) => total + Number(item.weightKg || 0), 0);
  const primary = sum(process.inputs.filter((item) => item.inputRole === "PRIMARY"));
  const totalInput = sum(process.inputs);
  const qualified = sum(process.outputs.filter((item) => item.outputType === "QUALIFIED"));
  const reusable = sum(process.outputs.filter((item) => item.outputType === "REUSABLE" || item.outputType === "TAILING"));
  const totalOutput = sum(process.outputs);
  return {
    primaryInputWeightKg: primary,
    totalInputWeightKg: totalInput,
    qualifiedOutputWeightKg: qualified,
    reusableOutputWeightKg: reusable,
    totalOutputWeightKg: totalOutput,
    mainYieldPercent: primary > 0 ? qualified / primary * 100 : null,
    recoveryPercent: totalInput > 0 ? (qualified + reusable) / totalInput * 100 : null,
    balanceDifferenceKg: totalInput - totalOutput,
  };
}

export function processPlanToLegacySteps(plan: ProcessPlanDraft): ExperimentProcessStep[] {
  return plan.majorProcesses.map((process, index) => {
    const result = calculateMajorProcessYield(process);
    const positiveLoss = Math.max(0, result.balanceDifferenceKg);
    return {
      sequence: index + 1,
      processName: process.processName,
      beforeWeightKg: result.totalInputWeightKg || undefined,
      afterWeightKg: result.qualifiedOutputWeightKg || undefined,
      remainingWeightKg: result.reusableOutputWeightKg || undefined,
      remainingDisposition: result.reusableOutputWeightKg > 0 ? "REUSE" : undefined,
      lossWeightKg: positiveLoss || undefined,
      lossRate: result.totalInputWeightKg > 0 ? positiveLoss / result.totalInputWeightKg : undefined,
      remark: process.remark || process.description,
    };
  });
}

export function normalizeProcessPlan(plan: ProcessPlanDraft): ProcessPlanDraft {
  return {
    ...plan,
    majorProcesses: (plan.majorProcesses || []).map((major, majorIndex) => ({
      ...major,
      key: major.key || major.id || nextProcessKey("major"),
      sequence: majorIndex + 1,
      steps: (major.steps || []).map((step, stepIndex) => ({
        ...step,
        key: step.key || step.id || nextProcessKey("step"),
        sequence: stepIndex + 1,
        materials: (step.materials || []).map((material, materialIndex) => ({
          ...material, key: material.key || material.id || nextProcessKey("material"), sequence: materialIndex + 1,
        })),
      })),
      inputs: (major.inputs || []).map((input, inputIndex) => ({
        ...input, key: input.key || input.id || nextProcessKey("input"), sequence: inputIndex + 1,
      })),
      outputs: (major.outputs || []).map((output, outputIndex) => ({
        ...output, key: output.key || output.id || nextProcessKey("output"), sequence: outputIndex + 1,
      })),
    })),
  };
}
