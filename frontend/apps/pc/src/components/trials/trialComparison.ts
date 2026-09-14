import type {
  MajorProcessDraft,
  MinorProcessStepDraft,
  ProcessInputDraft,
  ProcessStepMaterialDraft,
} from "@rnd/shared";
import type { TrialScheme } from "../../services/trialApi";

export type TrialDifferenceKind = "QUALITY" | "DIFFICULTY" | "PARAMETER" | "FORMULA" | "INPUT" | "YIELD" | "INCOMPARABLE";

export interface TrialDifference {
  kind: TrialDifferenceKind;
  path: string;
  before: string | number | null;
  after: string | number | null;
  numericDelta?: number | null;
}

export interface MajorComparison {
  candidateMajorId: string | null;
  baselineMajorId: string | null;
  label: string;
  comparable: boolean;
  reason?: string;
}

export interface PlannedActualRow {
  key: string;
  kind: "MATERIAL" | "PARAMETER" | "MAJOR_YIELD" | "BATCH_YIELD";
  label: string;
  planned: number | string | null;
  actual: number | string | null;
  difference: number | null;
  complete: boolean;
}

export interface TrialComparisonResult {
  majorComparisons: MajorComparison[];
  differences: TrialDifference[];
}

export function numericDifference(planned: number | null | undefined, actual: number | null | undefined) {
  if (planned == null || actual == null) return null;
  return Math.round((actual - planned) * 1_000_000_000) / 1_000_000_000;
}

export function plannedActualRows(trial: TrialScheme): PlannedActualRow[] {
  const rows: PlannedActualRow[] = [];
  const planned = trial.plannedData;
  for (const major of trial.plan.majorProcesses) {
    for (const step of major.steps) {
      const stepId = nodeId(step);
      const stepPlan = stepId ? planned.stepParameters[stepId] : undefined;
      for (const material of step.materials) {
        const materialId = nodeId(material);
        const target = materialId ? nullableNumber(planned.materialWeightsKg[materialId]) : null;
        const actual = nullableNumber(material.weightKg);
        if (target == null && actual == null) continue;
        rows.push({ key: `material:${materialId || material.key}`, kind: "MATERIAL", label: `${major.processName} / ${step.stepName} / ${material.materialName || "未命名物料"}`, planned: target, actual, difference: numericDifference(target, actual), complete: actual != null });
      }
      for (const [index, name, actual] of [[1, step.parameter1Name, step.parameter1Value], [2, step.parameter2Name, step.parameter2Value]] as const) {
        const target = index === 1 ? stepPlan?.parameter1Value : stepPlan?.parameter2Value;
        if (blank(target) && blank(actual)) continue;
        rows.push({ key: `parameter:${stepId || step.key}:${index}`, kind: "PARAMETER", label: `${major.processName} / ${step.stepName} / ${name || `参数 ${index}`}`, planned: textOrNull(target), actual: textOrNull(actual), difference: null, complete: !blank(actual) });
      }
    }
    const majorId = nodeId(major);
    const target = majorId ? nullableNumber(planned.majorYieldTargets[majorId]) : null;
    const actual = actualMajorYield(major);
    if (target != null || actual != null) rows.push({ key: `yield:${majorId || major.key}`, kind: "MAJOR_YIELD", label: `${major.processName} / 得率`, planned: target, actual, difference: numericDifference(target, actual), complete: actual != null });
  }
  const actualBatch = actualBatchYield(trial.plan.majorProcesses);
  if (planned.batchYieldTarget != null || actualBatch != null) rows.push({ key: "yield:batch", kind: "BATCH_YIELD", label: "最终得率", planned: planned.batchYieldTarget, actual: actualBatch, difference: numericDifference(planned.batchYieldTarget, actualBatch), complete: actualBatch != null });
  return rows;
}

export function buildTrialComparison(candidate: TrialScheme, baseline: TrialScheme): TrialComparisonResult {
  const differences: TrialDifference[] = [];
  if (candidate.qualityScore !== baseline.qualityScore || textOrNull(candidate.qualityNotes) !== textOrNull(baseline.qualityNotes)) {
    differences.push({ kind: "QUALITY", path: "质量评价", before: quality(baseline), after: quality(candidate) });
  }
  if (candidate.difficulty !== baseline.difficulty) {
    differences.push({ kind: "DIFFICULTY", path: "操作难度", before: baseline.difficulty, after: candidate.difficulty });
  }
  if (candidate.plannedData.batchYieldTarget !== baseline.plannedData.batchYieldTarget) {
    differences.push({ kind: "YIELD", path: "最终目标得率", before: baseline.plannedData.batchYieldTarget, after: candidate.plannedData.batchYieldTarget });
  }

  const baselineOrigins = uniqueOriginIndex(baseline);
  const candidateOrigins = uniqueOriginIndex(candidate);
  const origins = new Set([...baselineOrigins.keys(), ...candidateOrigins.keys()]);
  const majorComparisons: MajorComparison[] = [];
  for (const origin of origins) {
    const baselineMajor = baselineOrigins.get(origin) || null;
    const candidateMajor = candidateOrigins.get(origin) || null;
    const label = candidateMajor?.processName || baselineMajor?.processName || "未命名大工序";
    if (!baselineMajor || !candidateMajor) {
      majorComparisons.push({ candidateMajorId: candidateMajor ? nodeId(candidateMajor) : null, baselineMajorId: baselineMajor ? nodeId(baselineMajor) : null, label, comparable: false, reason: "缺少唯一来源标识对应项，不能按名称或行号推测" });
      differences.push({ kind: "INCOMPARABLE", path: label, before: baselineMajor?.processName || null, after: candidateMajor?.processName || null });
      continue;
    }
    if (textOrNull(baseline.plannedData.yieldBasisNote) !== textOrNull(candidate.plannedData.yieldBasisNote)) {
      const reason = `称重口径说明不同：${baseline.plannedData.yieldBasisNote || "未填写"} → ${candidate.plannedData.yieldBasisNote || "未填写"}`;
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason });
      differences.push({ kind: "YIELD", path: `${label} / 称重口径`, before: textOrNull(baseline.plannedData.yieldBasisNote), after: textOrNull(candidate.plannedData.yieldBasisNote) });
      continue;
    }
    const beforePrimary = primaryMaterialIdentity(baselineMajor);
    const afterPrimary = primaryMaterialIdentity(candidateMajor);
    if (beforePrimary !== afterPrimary) {
      const reason = `主料标识不同：${beforePrimary || "未识别"} → ${afterPrimary || "未识别"}`;
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason });
      differences.push({ kind: "INPUT", path: `${label} / 主料`, before: beforePrimary, after: afterPrimary });
      continue;
    }
    if (baselineMajor.yieldBasis !== candidateMajor.yieldBasis) {
      const reason = `得率口径不同：${baselineMajor.yieldBasis} → ${candidateMajor.yieldBasis}`;
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason });
      differences.push({ kind: "YIELD", path: `${label} / 得率口径`, before: baselineMajor.yieldBasis, after: candidateMajor.yieldBasis });
      continue;
    }
    majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: true });
    compareMajor(candidateMajor, baselineMajor, differences, candidate, baseline);
  }

  for (const major of [...baseline.plan.majorProcesses, ...candidate.plan.majorProcesses]) {
    const id = nodeId(major);
    if (id && (baseline.majorOrigins[id] || candidate.majorOrigins[id])) continue;
    majorComparisons.push({ candidateMajorId: candidate.plan.majorProcesses.includes(major) ? id : null, baselineMajorId: baseline.plan.majorProcesses.includes(major) ? id : null, label: major.processName || "未命名大工序", comparable: false, reason: "缺少唯一来源标识，重复工序不可按名称或行号比较" });
  }
  return { majorComparisons, differences };
}

function compareMajor(candidate: MajorProcessDraft, baseline: MajorProcessDraft, differences: TrialDifference[], candidateTrial: TrialScheme, baselineTrial: TrialScheme) {
  const beforeYield = actualMajorYield(baseline);
  const afterYield = actualMajorYield(candidate);
  if (beforeYield !== afterYield) differences.push({ kind: "YIELD", path: `${candidate.processName} / 实际得率`, before: beforeYield, after: afterYield });
  const beforeTarget = baselineTrial.plannedData.majorYieldTargets[nodeId(baseline)];
  const afterTarget = candidateTrial.plannedData.majorYieldTargets[nodeId(candidate)];
  if (beforeTarget !== afterTarget) differences.push({ kind: "YIELD", path: `${candidate.processName} / 目标得率`, before: nullableNumber(beforeTarget), after: nullableNumber(afterTarget) });
  compareInputs(candidate.processName, baseline.inputs, candidate.inputs, differences);

  const baselineSteps = groupedIndex(baseline.steps, stepIdentity);
  const candidateSteps = groupedIndex(candidate.steps, stepIdentity);
  for (const step of baseline.steps.filter(item => !stepIdentity(item))) {
    differences.push({ kind: "INCOMPARABLE", path: `${candidate.processName} / 小步骤 #${step.sequence}（缺少唯一编号）`, before: step.stepName || null, after: null });
  }
  for (const step of candidate.steps.filter(item => !stepIdentity(item))) {
    differences.push({ kind: "INCOMPARABLE", path: `${candidate.processName} / 小步骤 #${step.sequence}（缺少唯一编号）`, before: null, after: step.stepName || null });
  }
  for (const identity of new Set([...baselineSteps.keys(), ...candidateSteps.keys()])) {
    const beforeGroup = baselineSteps.get(identity) || [];
    const afterGroup = candidateSteps.get(identity) || [];
    if (beforeGroup.length !== 1 || afterGroup.length !== 1) {
      differences.push({ kind: "INCOMPARABLE", path: `${candidate.processName} / 小步骤 ${identity}（编号不唯一或未匹配）`, before: stepNames(beforeGroup), after: stepNames(afterGroup) });
      continue;
    }
    const before = beforeGroup[0]!;
    const after = afterGroup[0]!;
    compareTextParameter(candidate.processName, before, after, 1, differences);
    compareTextParameter(candidate.processName, before, after, 2, differences);
    comparePlannedParameter(candidate.processName, baselineTrial, candidateTrial, before, after, 1, differences);
    comparePlannedParameter(candidate.processName, baselineTrial, candidateTrial, before, after, 2, differences);
    compareFormula(candidate.processName, before, after, differences, baselineTrial, candidateTrial);
  }
}

function comparePlannedParameter(majorName: string, baselineTrial: TrialScheme, candidateTrial: TrialScheme, before: MinorProcessStepDraft, after: MinorProcessStepDraft, index: 1 | 2, differences: TrialDifference[]) {
  const beforePlan = baselineTrial.plannedData.stepParameters[nodeId(before)];
  const afterPlan = candidateTrial.plannedData.stepParameters[nodeId(after)];
  const beforeValue = textOrNull(index === 1 ? beforePlan?.parameter1Value : beforePlan?.parameter2Value);
  const afterValue = textOrNull(index === 1 ? afterPlan?.parameter1Value : afterPlan?.parameter2Value);
  if (beforeValue === afterValue) return;
  const name = (index === 1 ? after.parameter1Name || before.parameter1Name : after.parameter2Name || before.parameter2Name) || `参数 ${index}`;
  differences.push({ kind: "PARAMETER", path: `${majorName} / ${after.stepName} / ${name}计划值`, before: beforeValue, after: afterValue, numericDelta: parameterDelta(before, after, index, beforeValue, afterValue) });
}

function compareTextParameter(majorName: string, before: MinorProcessStepDraft, after: MinorProcessStepDraft, index: 1 | 2, differences: TrialDifference[]) {
  const beforeValue = textOrNull(index === 1 ? before.parameter1Value : before.parameter2Value);
  const afterValue = textOrNull(index === 1 ? after.parameter1Value : after.parameter2Value);
  if (beforeValue === afterValue) return;
  const name = (index === 1 ? after.parameter1Name || before.parameter1Name : after.parameter2Name || before.parameter2Name) || `参数 ${index}`;
  differences.push({ kind: "PARAMETER", path: `${majorName} / ${after.stepName} / ${name}`, before: beforeValue, after: afterValue, numericDelta: parameterDelta(before, after, index, beforeValue, afterValue) });
}

function compareFormula(majorName: string, before: MinorProcessStepDraft, after: MinorProcessStepDraft, differences: TrialDifference[], baselineTrial: TrialScheme, candidateTrial: TrialScheme) {
  const beforeMaterials = uniqueIndex(before.materials.filter(external), materialIdentity);
  const afterMaterials = uniqueIndex(after.materials.filter(external), materialIdentity);
  for (const identity of new Set([...beforeMaterials.keys(), ...afterMaterials.keys()])) {
    const beforeMaterial = beforeMaterials.get(identity);
    const afterMaterial = afterMaterials.get(identity);
    const beforeWeight = nullableNumber(beforeMaterial?.weightKg);
    const afterWeight = nullableNumber(afterMaterial?.weightKg);
    if (beforeWeight === afterWeight && Boolean(beforeMaterial) === Boolean(afterMaterial)) continue;
    differences.push({ kind: "FORMULA", path: `${majorName} / ${after.stepName} / ${afterMaterial?.materialName || beforeMaterial?.materialName || identity}`, before: beforeWeight, after: afterWeight });
    continue;
  }
  for (const identity of new Set([...beforeMaterials.keys(), ...afterMaterials.keys()])) {
    const beforeMaterial = beforeMaterials.get(identity);
    const afterMaterial = afterMaterials.get(identity);
    const beforeWeight = beforeMaterial ? nullableNumber(baselineTrial.plannedData.materialWeightsKg[nodeId(beforeMaterial)]) : null;
    const afterWeight = afterMaterial ? nullableNumber(candidateTrial.plannedData.materialWeightsKg[nodeId(afterMaterial)]) : null;
    if (beforeWeight === afterWeight) continue;
    differences.push({ kind: "FORMULA", path: `${majorName} / ${after.stepName} / ${afterMaterial?.materialName || beforeMaterial?.materialName || identity}计划投入`, before: beforeWeight, after: afterWeight });
  }
}

function compareInputs(majorName: string, before: ProcessInputDraft[], after: ProcessInputDraft[], differences: TrialDifference[]) {
  const beforeInputs = uniqueIndex(before, inputIdentity);
  const afterInputs = uniqueIndex(after, inputIdentity);
  for (const identity of new Set([...beforeInputs.keys(), ...afterInputs.keys()])) {
    const beforeInput = beforeInputs.get(identity);
    const afterInput = afterInputs.get(identity);
    const beforeWeight = nullableNumber(beforeInput?.weightKg);
    const afterWeight = nullableNumber(afterInput?.weightKg);
    if (beforeWeight === afterWeight && Boolean(beforeInput) === Boolean(afterInput)) continue;
    differences.push({ kind: "INPUT", path: `${majorName} / ${afterInput?.materialName || beforeInput?.materialName || identity}投入`, before: beforeWeight, after: afterWeight });
  }
}

function uniqueOriginIndex(trial: TrialScheme) {
  const entries: Array<[string, MajorProcessDraft]> = [];
  for (const major of trial.plan.majorProcesses) {
    const id = nodeId(major);
    const origin = id ? trial.majorOrigins[id] : undefined;
    if (origin) entries.push([origin, major]);
  }
  return uniquePairs(entries);
}

function uniqueIndex<T>(values: T[], identity: (value: T) => string | null) {
  return uniquePairs(values.map(value => [identity(value), value] as const).filter((entry): entry is [string, T] => Boolean(entry[0])));
}

function groupedIndex<T>(values: T[], identity: (value: T) => string | null) {
  const groups = new Map<string, T[]>();
  for (const value of values) {
    const key = identity(value);
    if (key) groups.set(key, [...groups.get(key) || [], value]);
  }
  return groups;
}

function stepNames(steps: MinorProcessStepDraft[]) {
  return steps.length ? steps.map(step => step.stepName || `#${step.sequence}`).join("、") : null;
}

function uniquePairs<T>(pairs: ReadonlyArray<readonly [string, T]>) {
  const counts = new Map<string, number>();
  for (const [key] of pairs) counts.set(key, (counts.get(key) || 0) + 1);
  return new Map(pairs.filter(([key]) => counts.get(key) === 1));
}

function stepIdentity(step: MinorProcessStepDraft) { return textOrNull(step.stepCode); }
function materialIdentity(material: ProcessStepMaterialDraft) { return textOrNull(material.formulaMaterialId) || textOrNull(material.materialCode); }
function inputIdentity(input: ProcessInputDraft) { return textOrNull(input.sourceStepMaterialId) || textOrNull(input.materialCode); }
function external(material: ProcessStepMaterialDraft) { return material.sourceType !== "STEP_OUTPUT"; }
function nodeId(value: { id?: string; key: string }) { return value.id || value.key; }
function blank(value: unknown) { return value == null || String(value).trim() === ""; }
function textOrNull(value: unknown) { return blank(value) ? null : String(value).trim(); }
function nullableNumber(value: unknown) { return typeof value === "number" && Number.isFinite(value) ? value : null; }
function quality(trial: TrialScheme) { return [trial.qualityScore, textOrNull(trial.qualityNotes)].filter(value => value != null).join(" · ") || null; }

function primaryMaterialIdentity(major: MajorProcessDraft) {
  const primary = [...major.steps].sort((left, right) => left.sequence - right.sequence)
    .flatMap(step => [...step.materials].sort((left, right) => left.sequence - right.sequence))
    .find(material => material.materialRole === "PRIMARY" && material.sourceType !== "STEP_OUTPUT");
  return primary ? materialIdentity(primary) || textOrNull(primary.materialName) : null;
}

function parameterDelta(before: MinorProcessStepDraft, after: MinorProcessStepDraft, index: 1 | 2, beforeValue: string | null, afterValue: string | null) {
  const beforeName = textOrNull(index === 1 ? before.parameter1Name : before.parameter2Name);
  const afterName = textOrNull(index === 1 ? after.parameter1Name : after.parameter2Name);
  const beforeUnit = textOrNull(index === 1 ? before.parameter1Unit : before.parameter2Unit);
  const afterUnit = textOrNull(index === 1 ? after.parameter1Unit : after.parameter2Unit);
  if (beforeName !== afterName || beforeUnit !== afterUnit || beforeValue == null || afterValue == null) return null;
  if (!strictNumber(beforeValue) || !strictNumber(afterValue)) return null;
  return numericDifference(Number(beforeValue), Number(afterValue));
}

function strictNumber(value: string) { return /^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/.test(value.trim()); }

function actualMajorYield(major: MajorProcessDraft): number | null {
  if (major.yieldBasis === "NONE") return null;
  const materials = major.steps.flatMap(step => step.materials || []);
  const outputs = major.steps.flatMap(step => step.outputs || []);
  let denominator: number | null;
  if (materials.length) {
    if (major.yieldBasis === "TOTAL_INPUT") {
      const externalMaterials = materials.filter(external);
      denominator = externalMaterials.length && externalMaterials.every(item => nullableNumber(item.weightKg) != null)
        ? externalMaterials.reduce((sum, item) => sum + (item.weightKg as number), 0)
        : null;
    } else {
      const primary = materials.find(item => item.materialRole === "PRIMARY");
      denominator = nullableNumber(primary?.weightKg);
    }
    const primaryOutput = [...outputs].reverse().find(item => item.primaryOutput);
    const numerator = nullableNumber(primaryOutput?.weightKg);
    return denominator != null && denominator > 0 && numerator != null ? numerator / denominator * 100 : null;
  }
  const inputs = major.yieldBasis === "TOTAL_INPUT" ? major.inputs : major.inputs.filter(item => item.inputRole === "PRIMARY");
  const qualified = major.outputs.filter(item => item.outputType === "QUALIFIED");
  if (!inputs.length || !qualified.length || inputs.some(item => nullableNumber(item.weightKg) == null) || qualified.some(item => nullableNumber(item.weightKg) == null)) return null;
  denominator = inputs.reduce((sum, item) => sum + (item.weightKg as number), 0);
  const numerator = qualified.reduce((sum, item) => sum + (item.weightKg as number), 0);
  return denominator > 0 ? numerator / denominator * 100 : null;
}

function actualBatchYield(majors: MajorProcessDraft[]) {
  const included = majors.filter(major => major.yieldBasis !== "NONE");
  if (!included.length || !hasContinuousPrimaryFlow(majors)) return null;
  const rates = included.map(actualMajorYield);
  if (rates.some(rate => rate == null)) return null;
  return (rates as number[]).reduce((value, rate) => value * rate / 100, 100);
}

function hasContinuousPrimaryFlow(majors: MajorProcessDraft[]) {
  const orderedMajors = [...majors].sort((left, right) => left.sequence - right.sequence);
  const outputs = new Map<string, { output: NonNullable<MinorProcessStepDraft["outputs"]>[number]; majorSequence: number }>();
  for (const major of orderedMajors) for (const step of major.steps) for (const output of step.outputs || []) {
    const id = nodeId(output);
    if (outputs.has(id)) return false;
    outputs.set(id, { output, majorSequence: major.sequence });
  }
  let currentTip: string | null = null;
  let started = false;
  for (const major of orderedMajors) {
    const steps = [...major.steps].sort((left, right) => left.sequence - right.sequence);
    if (major.yieldBasis !== "NONE" && !steps.some(step => step.materials.some(item => item.materialRole === "PRIMARY"))) return false;
    for (const step of steps) {
      const inputs = step.materials.filter(item => item.materialRole === "PRIMARY");
      const stepOutputs = (step.outputs || []).filter(item => item.primaryOutput);
      if (!inputs.length && !stepOutputs.length) continue;
      if (inputs.length !== 1 || stepOutputs.length !== 1) return false;
      const input = inputs[0]!;
      const output = stepOutputs[0]!;
      if (nullableNumber(input.weightKg) == null || nullableNumber(output.weightKg) == null || Number(input.weightKg) <= 0 || Number(output.weightKg) <= 0) return false;
      if (!started) {
        if (input.sourceType === "STEP_OUTPUT") {
          const source = outputs.get(input.sourceStepOutputId || "");
          if (!source || source.majorSequence >= major.sequence || !source.output.continueFlow || !source.output.primaryOutput) return false;
        } else if (input.sourceType && input.sourceType !== "EXTERNAL") return false;
        started = true;
      } else {
        if (input.sourceType !== "STEP_OUTPUT" || input.sourceStepOutputId !== currentTip) return false;
        const source = outputs.get(currentTip || "");
        if (!source?.output.continueFlow || !source.output.primaryOutput || Math.abs(Number(input.weightKg) - Number(source.output.weightKg)) > 0.00000001) return false;
      }
      currentTip = nodeId(output);
    }
  }
  return started;
}
