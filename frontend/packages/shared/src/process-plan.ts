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
  measurementTool?: string;
  frequency?: string;
  deviationAction?: string;
  resolved: boolean;
  confirmedBy?: string;
  confirmedAt?: string;
  basisOrRemark?: string;
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
  balanceToleranceKg?: number;
  legacy?: boolean;
}

export interface ProcessRecipeSourcePreview {
  majorSequence: number;
  stepSequence: number;
  materialSequence: number;
  materialRole: ProcessMaterialRole;
  materialCode?: string;
  materialName: string;
  weightKg: number;
}

export interface ProcessRecipeLinePreview {
  formulaMaterialId?: string;
  materialCode?: string;
  materialName: string;
  weightKg: number;
  ratioPercent: number | null;
  sources: ProcessRecipeSourcePreview[];
}

export interface ProcessSubmissionIssuePreview {
  code: string;
  message: string;
  majorSequence: number | null;
  stepSequence: number | null;
}

export interface ProcessSubmissionPreview {
  ready: boolean;
  errors: ProcessSubmissionIssuePreview[];
  warnings: ProcessSubmissionIssuePreview[];
}

let processKey = 1;
export const nextProcessKey = (prefix = "process") => `${prefix}-${Date.now()}-${processKey++}`;

export function createEmptyProcessPlan(): ProcessPlanDraft {
  return { versionNo: 0, status: "DRAFT", majorProcesses: [], batchYieldPercent: null, balanceToleranceKg: 0.01, legacy: false };
}

export function calculateMajorProcessYield(process: MajorProcessDraft): ProcessYieldResult {
  (process.steps || []).forEach(validateMinorStep);
  if (hasLayeredPrimaryFlowData(process)) {
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
  validateMinorStep(step);
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
  const firstPrimaryInput = process.steps.flatMap((step) => (step.materials || [])
    .filter((item) => item.materialRole === "PRIMARY" && item.weightKg != null)
    .map((material) => ({ step, material })))
    .sort((left, right) => left.step.sequence - right.step.sequence || left.material.sequence - right.material.sequence)[0];
  const lastPrimaryOutput = process.steps.flatMap((step) => (step.outputs || [])
    .filter((item) => item.primaryOutput && item.weightKg != null)
    .map((output) => ({ step, output })))
    .sort((left, right) => right.step.sequence - left.step.sequence || right.output.sequence - left.output.sequence)[0];
  const primaryInput = firstPrimaryInput?.material.weightKg || 0;
  const externalInput = sumWeight(allMaterials.filter((item) => item.sourceType !== "STEP_OUTPUT"));
  const lastOutputs = [...process.steps].reverse().find((step) => (step.outputs?.length || 0) > 0)?.outputs || [];
  const primaryOutput = lastPrimaryOutput?.output;
  const validPrimaryFlow = Boolean(firstPrimaryInput && lastPrimaryOutput
    && firstPrimaryInput.step.sequence <= lastPrimaryOutput.step.sequence);
  return processYield(
    primaryInput,
    externalInput,
    primaryOutput?.weightKg || 0,
    sumWeight(lastOutputs.filter((item) => item.outputType === "REUSABLE" || item.outputType === "TAILING")),
    sumWeight(lastOutputs),
    validPrimaryFlow,
  );
}

function hasLayeredPrimaryFlowData(process: MajorProcessDraft) {
  const steps = process.steps || [];
  return steps.some((step) => step.materials.some((item) => item.materialRole === "PRIMARY"))
    || steps.some((step) => (step.outputs || []).some((item) => item.primaryOutput));
}

function validateMinorStep(step: MinorProcessStepDraft) {
  const primaryMaterials = (step.materials || []).filter((item) => item.materialRole === "PRIMARY");
  if (primaryMaterials.length > 1) throw new Error("a step may have at most one primary material input");
  const primaryOutputs = (step.outputs || []).filter((item) => item.primaryOutput);
  if (primaryOutputs.length > 1) throw new Error("a step may have at most one primary output");
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
  const normalized = {
    ...plan,
    balanceToleranceKg: plan.balanceToleranceKg ?? 0.01,
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
        outputs: (step.outputs || []).map((output, outputIndex) => {
          const key = output.key || output.id || nextProcessKey("output");
          return { ...output, key, id: output.id || key, sequence: outputIndex + 1 };
        }),
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
  validateFlowReferences(normalized);
  return normalized;
}

/**
 * UI-only preview. The server-side ProcessSubmissionValidator remains the authority for formal submission.
 */
export function aggregateProcessRecipe(plan: ProcessPlanDraft): ProcessRecipeLinePreview[] {
  const groups = new Map<string, ProcessRecipeLinePreview>();
  for (const major of plan.majorProcesses || []) {
    for (const step of major.steps || []) {
      for (const material of step.materials || []) {
        if (material.sourceType !== "EXTERNAL") continue;
        const materialName = normalizeMaterialName(material.materialName);
        const group = recipeGroup(material.formulaMaterialId, material.materialCode, materialName);
        const current = groups.get(group.key) || {
          formulaMaterialId: material.formulaMaterialId,
          materialCode: material.materialCode,
          materialName,
          weightKg: 0,
          ratioPercent: null,
          sources: [],
        };
        current.weightKg += safeWeight(material.weightKg);
        current.sources.push({
          majorSequence: major.sequence,
          stepSequence: step.sequence,
          materialSequence: material.sequence,
          materialRole: material.materialRole,
          materialCode: material.materialCode,
          materialName,
          weightKg: safeWeight(material.weightKg),
        });
        groups.set(group.key, current);
      }
    }
  }
  const total = [...groups.values()].reduce((sum, line) => sum + line.weightKg, 0);
  return [...groups.entries()]
    .sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([, line]) => ({
      ...line,
      ratioPercent: total > 0 ? line.weightKg / total * 100 : null,
      sources: [...line.sources].sort(compareSource),
    }));
}

/**
 * UI-only preview that mirrors submission rules for immediate feedback. Formal submission must use the backend result.
 */
export function previewProcessSubmission(plan: ProcessPlanDraft): ProcessSubmissionPreview {
  const errors: ProcessSubmissionIssuePreview[] = [];
  const warnings: ProcessSubmissionIssuePreview[] = [];
  const majors = plan.majorProcesses || [];
  if (!majors.length) {
    errors.push(issue("MAJOR_PROCESS_REQUIRED", "至少需要一个大工序", null, null));
    return { ready: false, errors, warnings };
  }

  const balanceTolerance = plan.balanceToleranceKg ?? 0.01;
  const validBalanceTolerance = balanceTolerance >= 0;
  if (!validBalanceTolerance) errors.push(issue("BALANCE_TOLERANCE_INVALID", "物料平衡允许差不能为负数", null, null));

  const steps = majors.flatMap((major) => (major.steps || []).map((step) => ({ major, step })));
  previewFlow(steps, errors);
  let externalPrimary = false;
  for (const major of majors) {
    previewWeights(major, errors);
    previewYieldCompleteness(major, errors);
    previewControls(major, errors);
    if ((major.steps || []).some((step) => step.materials.some((material) => material.materialRole === "PRIMARY" && material.sourceType === "EXTERNAL"))) {
      externalPrimary = true;
    }
    if (validBalanceTolerance) previewBalance(major, balanceTolerance, errors, warnings);
  }
  if (!externalPrimary) errors.push(issue("EXTERNAL_PRIMARY_REQUIRED", "配方至少需要一项外部主料", null, null));
  return { ready: errors.length === 0, errors, warnings };
}

type StepPreviewRef = { major: MajorProcessDraft; step: MinorProcessStepDraft };
type OutputPreviewRef = StepPreviewRef & { output: StepOutputDraft };

function previewFlow(steps: StepPreviewRef[], errors: ProcessSubmissionIssuePreview[]) {
  const outputs = new Map<string, OutputPreviewRef>();
  const duplicateIds = new Set<string>();
  for (const ref of steps) {
    for (const output of ref.step.outputs || []) {
      const id = output.id || output.key;
      if (outputs.has(id)) duplicateIds.add(id);
      else outputs.set(id, { ...ref, output });
    }
  }
  const graph = new Map<string, string[]>();
  for (const ref of steps) {
    for (const material of ref.step.materials || []) {
      if (material.sourceType === "STEP_OUTPUT") {
        const source = material.sourceStepOutputId;
        if (!source || duplicateIds.has(source)) {
          errors.push(flowIssue(ref));
          continue;
        }
        const output = outputs.get(source);
        if (!output || !precedesStep(output, ref) || !output.output.continueFlow
          || (material.materialRole === "PRIMARY" && !output.output.primaryOutput)) {
          errors.push(flowIssue(ref));
          continue;
        }
        for (const target of ref.step.outputs || []) {
          const targetId = target.id || target.key;
          graph.set(source, [...(graph.get(source) || []), targetId]);
        }
      } else if (material.sourceType === "EXTERNAL" && material.sourceStepOutputId) {
        errors.push(flowIssue(ref));
      }
    }
  }
  if (hasFlowCycle(graph)) errors.push(issue("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链或循环", null, null));
}

function previewYieldCompleteness(major: MajorProcessDraft, errors: ProcessSubmissionIssuePreview[]) {
  if (major.yieldBasis === "NONE") return;
  const steps = major.steps || [];
  if (!steps.length) {
    const hasPrimaryInput = major.inputs.some((input) => input.inputRole === "PRIMARY" && input.weightKg != null);
    const hasPrimaryOutput = major.outputs.some((output) => output.outputType === "QUALIFIED" && output.weightKg != null);
    if (!hasPrimaryInput) errors.push(issue("MAJOR_PRIMARY_INPUT_REQUIRED", "需计算得率的大工序缺少首端主料投入", major.sequence, null));
    if (!hasPrimaryOutput) errors.push(issue("MAJOR_PRIMARY_OUTPUT_REQUIRED", "需计算得率的大工序缺少末端主料产出", major.sequence, null));
    return;
  }
  const firstInput = steps.flatMap((step) => step.materials
    .filter((material) => material.materialRole === "PRIMARY" && material.weightKg != null)
    .map((material) => ({ step, material })))
    .sort((left, right) => left.step.sequence - right.step.sequence || left.material.sequence - right.material.sequence)[0];
  const lastOutput = steps.flatMap((step) => (step.outputs || [])
    .filter((output) => output.primaryOutput && output.weightKg != null)
    .map((output) => ({ step, output })))
    .sort((left, right) => right.step.sequence - left.step.sequence || right.output.sequence - left.output.sequence)[0];
  if (!firstInput) errors.push(issue("MAJOR_PRIMARY_INPUT_REQUIRED", "需计算得率的大工序缺少首端主料投入", major.sequence, null));
  if (!lastOutput || (firstInput && lastOutput.step.sequence < firstInput.step.sequence)) {
    errors.push(issue("MAJOR_PRIMARY_OUTPUT_REQUIRED", "需计算得率的大工序缺少末端主料产出", major.sequence, null));
  }
  if (firstInput && lastOutput && lastOutput.step.sequence < firstInput.step.sequence) {
    errors.push(issue("MAJOR_PRIMARY_FLOW_INVALID", "主料首端投入必须早于末端主产出", major.sequence, null));
  }
}

function previewControls(major: MajorProcessDraft, errors: ProcessSubmissionIssuePreview[]) {
  for (const step of major.steps || []) {
    for (const point of step.controlPoints || []) {
      if (point.importance !== "CRITICAL") continue;
      const measurements = point.measurements || [];
      const hasMeasurement = measurements.some((measurement) => measurement.measuredValue != null);
      const outOfLimit = measurements.some((measurement) => measurement.result === "FAIL" || measurementOutside(point, measurement));
      const unhandledDeviation = measurements.some((measurement) => (measurement.result === "FAIL" || measurementOutside(point, measurement))
        && (!measurement.deviationAction?.trim() || !measurement.retestResult || measurement.retestResult === "PENDING" || measurement.retestResult === "FAIL"));
      if (!hasMeasurement || !point.confirmedBy?.trim() || (outOfLimit && (!point.resolved || unhandledDeviation))) {
        errors.push(issue("CRITICAL_CONTROL_UNRESOLVED", "极重要关键控制点未完成", major.sequence, step.sequence));
      }
    }
  }
}

function previewBalance(major: MajorProcessDraft, balanceTolerance: number, errors: ProcessSubmissionIssuePreview[], warnings: ProcessSubmissionIssuePreview[]) {
  try {
    const difference = calculateMajorProcessYield(major).balanceDifferenceKg;
    if (Math.abs(difference) > balanceTolerance) {
      warnings.push(issue("MATERIAL_BALANCE_EXCEEDED", "物料平衡差超过允许范围", major.sequence, null));
      if (!major.remark?.trim()) errors.push(issue("MATERIAL_BALANCE_UNEXPLAINED", "物料平衡差超限，请填写差异说明", major.sequence, null));
    }
  } catch {
    // Input cardinality is already surfaced by the editor; the backend remains authoritative.
  }
}

function previewWeights(major: MajorProcessDraft, errors: ProcessSubmissionIssuePreview[]) {
  for (const step of major.steps || []) {
    if ([...step.materials, ...(step.outputs || [])].some((item) => item.weightKg != null && item.weightKg < 0)) {
      errors.push(issue("PROCESS_WEIGHT_INVALID", "工艺重量不能为负数", major.sequence, step.sequence));
    }
  }
  if ([...major.inputs, ...major.outputs].some((item) => item.weightKg != null && item.weightKg < 0)) {
    errors.push(issue("PROCESS_WEIGHT_INVALID", "工艺重量不能为负数", major.sequence, null));
  }
}

function recipeGroup(formulaMaterialId: string | undefined, materialCode: string | undefined, materialName: string) {
  if (formulaMaterialId?.trim()) return { key: `0:${formulaMaterialId.trim()}` };
  if (materialCode?.trim()) return { key: `1:${materialCode.trim()}` };
  return { key: `2:${materialName}` };
}

function normalizeMaterialName(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

function safeWeight(value: number | undefined) {
  return Number(value || 0);
}

function compareSource(left: ProcessRecipeSourcePreview, right: ProcessRecipeSourcePreview) {
  return left.majorSequence - right.majorSequence || left.stepSequence - right.stepSequence || left.materialSequence - right.materialSequence;
}

function precedesStep(source: OutputPreviewRef, consumer: StepPreviewRef) {
  return source.major.sequence !== consumer.major.sequence
    ? source.major.sequence < consumer.major.sequence
    : source.step.sequence < consumer.step.sequence;
}

function hasFlowCycle(graph: Map<string, string[]>) {
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const visit = (node: string): boolean => {
    if (visited.has(node)) return false;
    if (visiting.has(node)) return true;
    visiting.add(node);
    if ((graph.get(node) || []).some(visit)) return true;
    visiting.delete(node);
    visited.add(node);
    return false;
  };
  return [...graph.keys()].some(visit);
}

function measurementOutside(point: ControlPointDraft, measurement: ControlMeasurementDraft) {
  if (measurement.measuredValue == null) return false;
  return (point.lowerLimit != null && measurement.measuredValue < point.lowerLimit)
    || (point.upperLimit != null && measurement.measuredValue > point.upperLimit);
}

function issue(code: string, message: string, majorSequence: number | null, stepSequence: number | null): ProcessSubmissionIssuePreview {
  return { code, message, majorSequence, stepSequence };
}

function flowIssue(ref: StepPreviewRef) {
  return issue("PRIMARY_FLOW_BROKEN", "主料中间产物流转存在断链或循环", ref.major.sequence, ref.step.sequence);
}

function validateFlowReferences(plan: ProcessPlanDraft) {
  const outputIds = new Set(plan.majorProcesses.flatMap((major) => major.steps)
    .flatMap((step) => step.outputs || []).map((output) => output.id));
  for (const step of plan.majorProcesses.flatMap((major) => major.steps)) {
    validateMinorStep(step);
    for (const material of step.materials || []) {
      if (material.sourceType === "STEP_OUTPUT"
        && (!material.sourceStepOutputId || !outputIds.has(material.sourceStepOutputId))) {
        throw new Error("STEP_OUTPUT material must reference a normalized step output ID");
      }
      if (material.sourceType === "EXTERNAL" && material.sourceStepOutputId) {
        throw new Error("EXTERNAL material must not reference a step output");
      }
    }
  }
}
