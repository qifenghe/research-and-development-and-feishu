import type { ExperimentProcessStep } from "./types";

export type ProcessMaterialRole = "PRIMARY" | "AUXILIARY" | "PROCESS_WATER";
export type ProcessOutputType = "QUALIFIED" | "REUSABLE" | "TAILING" | "SAMPLE" | "WASTE" | "HOLD";
export type ProcessStepOutputType = "INTERMEDIATE" | "FINISHED" | ProcessOutputType;
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
  sourceType?: "EXTERNAL" | "STEP_OUTPUT";
  sourceStepOutputId?: string;
}

export interface StepOutputDraft {
  id?: string;
  key: string;
  sequence: number;
  outputType: ProcessStepOutputType;
  outputName: string;
  materialState: "SOLID" | "LIQUID" | "SEMI_SOLID";
  weightKg?: number;
  primaryOutput: boolean;
  continueFlow: boolean;
  remark?: string;
}

export interface ControlMeasurementDraft {
  id?: string;
  key: string;
  sequence: number;
  measuredValue?: number;
  measuredAt?: string;
  result?: "PENDING" | "PASS" | "FAIL";
  deviationAction?: string;
  retestResult?: "PENDING" | "PASS" | "FAIL";
  remark?: string;
}

export interface ControlPointDraft {
  id?: string;
  key: string;
  sequence: number;
  controlType: string;
  importance: "CRITICAL" | "IMPORTANT" | "NORMAL";
  itemName: string;
  targetValue?: number;
  lowerLimit?: number;
  upperLimit?: number;
  unit?: string;
  method?: string;
  frequency?: string;
  deviationAction?: string;
  resolved: boolean;
  confirmedBy?: string;
  measurements: ControlMeasurementDraft[];
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
  outputs?: StepOutputDraft[];
  controlPoints?: ControlPointDraft[];
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
  if (process.steps.some((step) => step.materials.length > 0 || (step.outputs?.length || 0) > 0)) {
    return calculateProcessYieldFromStepFlow(process);
  }
  const sum = (items: Array<{ weightKg?: number }>) => items.reduce((total, item) => total + Number(item.weightKg || 0), 0);
  const primary = sum(process.inputs.filter((item) => item.inputRole === "PRIMARY"));
  const totalInput = sum(process.inputs);
  const qualified = sum(process.outputs.filter((item) => item.outputType === "QUALIFIED"));
  const reusable = sum(process.outputs.filter((item) => item.outputType === "REUSABLE" || item.outputType === "TAILING"));
  const totalOutput = sum(process.outputs);
  return processYield(primary, totalInput, qualified, reusable, totalOutput,
    process.outputs.some((item) => item.outputType === "QUALIFIED" && item.weightKg != null));
}

export function calculateMinorStepYield(step: MinorProcessStepDraft): ProcessYieldResult {
  const materials = step.materials || [];
  const outputs = step.outputs || [];
  const primaryInput = materials.find((item) => item.materialRole === "PRIMARY" && item.weightKg != null)?.weightKg || 0;
  const primaryOutput = [...outputs].reverse().find((item) => item.primaryOutput && item.weightKg != null);
  return processYield(
    primaryInput,
    sumWeight(materials),
    primaryOutput?.weightKg || 0,
    sumWeight(outputs.filter((item) => item.outputType === "REUSABLE" || item.outputType === "TAILING")),
    sumWeight(outputs),
    primaryOutput != null,
  );
}

export function calculateBatchYield(plan: ProcessPlanDraft): number | null {
  const rates = plan.majorProcesses
    .filter((major) => major.yieldBasis !== "NONE")
    .map(calculateMajorProcessYield)
    .map((result) => result.mainYieldPercent)
    .filter((value): value is number => value != null);
  return rates.length ? rates.reduce((value, rate) => value * rate / 100, 100) : null;
}

function calculateProcessYieldFromStepFlow(process: MajorProcessDraft): ProcessYieldResult {
  const allMaterials = process.steps.flatMap((step) => step.materials || []);
  const primaryInput = allMaterials.find((item) => item.materialRole === "PRIMARY" && item.weightKg != null)?.weightKg || 0;
  const externalInput = sumWeight(allMaterials.filter((item) => item.sourceType !== "STEP_OUTPUT"));
  const lastOutputs = [...process.steps].reverse().find((step) => (step.outputs?.length || 0) > 0)?.outputs || [];
  const primaryOutput = process.steps.flatMap((step) => step.outputs || []).reverse()
    .find((item) => item.primaryOutput && item.weightKg != null);
  return processYield(
    primaryInput,
    externalInput,
    primaryOutput?.weightKg || 0,
    sumWeight(lastOutputs.filter((item) => item.outputType === "REUSABLE" || item.outputType === "TAILING")),
    sumWeight(lastOutputs),
    primaryOutput != null,
  );
}

function processYield(primaryInputWeightKg: number, totalInputWeightKg: number, qualifiedOutputWeightKg: number,
                      reusableOutputWeightKg: number, totalOutputWeightKg: number, hasPrimaryOutput = true): ProcessYieldResult {
  return {
    primaryInputWeightKg,
    totalInputWeightKg,
    qualifiedOutputWeightKg,
    reusableOutputWeightKg,
    totalOutputWeightKg,
    mainYieldPercent: hasPrimaryOutput && primaryInputWeightKg > 0 ? qualifiedOutputWeightKg / primaryInputWeightKg * 100 : null,
    recoveryPercent: totalInputWeightKg > 0 ? (qualifiedOutputWeightKg + reusableOutputWeightKg) / totalInputWeightKg * 100 : null,
    balanceDifferenceKg: totalInputWeightKg - totalOutputWeightKg,
  };
}

function sumWeight(items: Array<{ weightKg?: number }>) {
  return items.reduce((total, item) => total + Number(item.weightKg || 0), 0);
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
          sourceType: material.sourceType || "EXTERNAL",
        })),
        outputs: (step.outputs || []).map((output, outputIndex) => ({
          ...output, key: output.key || output.id || nextProcessKey("output"), sequence: outputIndex + 1,
        })),
        controlPoints: (step.controlPoints || []).map((point, pointIndex) => ({
          ...point, key: point.key || point.id || nextProcessKey("control"), sequence: pointIndex + 1,
          measurements: (point.measurements || []).map((measurement, measurementIndex) => ({
            ...measurement, key: measurement.key || measurement.id || nextProcessKey("measurement"), sequence: measurementIndex + 1,
          })),
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
