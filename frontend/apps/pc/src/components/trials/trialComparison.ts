import { calculateMajorProcessYield, mainYieldUnavailableReason } from "../../../../../packages/shared/src/process-plan.ts";
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
  reason?: string | null;
}

export interface TrialComparisonResult {
  majorComparisons: MajorComparison[];
  differences: TrialDifference[];
}

export function numericDifference(planned: number | null | undefined, actual: number | null | undefined) {
  if (planned == null || actual == null) return null;
  return Math.round((actual - planned) * 1_000_000_000) / 1_000_000_000;
}

export function deviationUnit(kind: PlannedActualRow["kind"], fallback = "") {
  return kind === "MAJOR_YIELD" || kind === "BATCH_YIELD" ? "个百分点" : fallback;
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
    const reason = mainYieldUnavailableReason(major);
    if (target != null || actual != null || reason) rows.push({ key: `yield:${majorId || major.key}`, kind: "MAJOR_YIELD", label: `${major.processName} / 得率`, planned: target, actual, difference: numericDifference(target, actual), complete: actual != null, reason });
  }
  const actualBatch = actualBatchYield(trial.plan.majorProcesses);
  const batchReason = trial.plan.majorProcesses.map(mainYieldUnavailableReason).find(Boolean) || null;
  if (planned.batchYieldTarget != null || actualBatch != null || batchReason) rows.push({ key: "yield:batch", kind: "BATCH_YIELD", label: "最终得率", planned: planned.batchYieldTarget, actual: actualBatch, difference: numericDifference(planned.batchYieldTarget, actualBatch), complete: actualBatch != null, reason: batchReason });
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

  const baselineOrigins = originGroups(baseline);
  const candidateOrigins = originGroups(candidate);
  const origins = new Set([...baselineOrigins.keys(), ...candidateOrigins.keys()]);
  const majorComparisons: MajorComparison[] = [];
  for (const origin of origins) {
    const baselineGroup = baselineOrigins.get(origin) || [];
    const candidateGroup = candidateOrigins.get(origin) || [];
    const baselineMajor = baselineGroup.length === 1 ? baselineGroup[0]! : null;
    const candidateMajor = candidateGroup.length === 1 ? candidateGroup[0]! : null;
    if (!baselineMajor || !candidateMajor) {
      const label = majorNames(candidateGroup.length ? candidateGroup : baselineGroup) || "未命名大工序";
      majorComparisons.push({ candidateMajorId: candidateMajor ? nodeId(candidateMajor) : null, baselineMajorId: baselineMajor ? nodeId(baselineMajor) : null, label, comparable: false, reason: "重复或缺少唯一来源标识对应项，不能推测比较" });
      differences.push({ kind: "INCOMPARABLE", path: `${label} / 大工序来源不唯一`, before: majorNames(baselineGroup), after: majorNames(candidateGroup) });
      continue;
    }
    const label = candidateMajor?.processName || baselineMajor?.processName || "未命名大工序";
    if (textOrNull(baseline.plannedData.yieldBasisNote) !== textOrNull(candidate.plannedData.yieldBasisNote)) {
      const reason = `称重口径说明不同：${baseline.plannedData.yieldBasisNote || "未填写"} → ${candidate.plannedData.yieldBasisNote || "未填写"}`;
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason });
      differences.push({ kind: "YIELD", path: `${label} / 称重口径`, before: textOrNull(baseline.plannedData.yieldBasisNote), after: textOrNull(candidate.plannedData.yieldBasisNote) });
      continue;
    }
    const beforePrimary = primaryMaterialIdentity(baselineMajor, baseline.plan.majorProcesses);
    const afterPrimary = primaryMaterialIdentity(candidateMajor, candidate.plan.majorProcesses);
    if (!beforePrimary.stable || !afterPrimary.stable || beforePrimary.identity !== afterPrimary.identity) {
      const reason = `主料标识不唯一或不同：${beforePrimary.identity || "未识别"} → ${afterPrimary.identity || "未识别"}`;
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason });
      differences.push({ kind: "INCOMPARABLE", path: `${label} / 主料缺少唯一标识`, before: beforePrimary.identity, after: afterPrimary.identity });
      continue;
    }
    const unsupportedReason = mainYieldUnavailableReason(baselineMajor) || mainYieldUnavailableReason(candidateMajor);
    if (unsupportedReason) {
      majorComparisons.push({ candidateMajorId: nodeId(candidateMajor), baselineMajorId: nodeId(baselineMajor), label, comparable: false, reason: unsupportedReason });
      differences.push({ kind: "INCOMPARABLE", path: `${label} / 得率口径`, before: baselineMajor.yieldBasis, after: candidateMajor.yieldBasis });
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

  appendUnidentifiedMajors(baseline, candidate, majorComparisons, differences);
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
  const beforeName = textOrNull(index === 1 ? before.parameter1Name : before.parameter2Name);
  const afterName = textOrNull(index === 1 ? after.parameter1Name : after.parameter2Name);
  const beforeUnit = textOrNull(index === 1 ? before.parameter1Unit : before.parameter2Unit);
  const afterUnit = textOrNull(index === 1 ? after.parameter1Unit : after.parameter2Unit);
  const beforeValue = textOrNull(index === 1 ? before.parameter1Value : before.parameter2Value);
  const afterValue = textOrNull(index === 1 ? after.parameter1Value : after.parameter2Value);
  if (beforeName !== afterName || beforeUnit !== afterUnit) {
    differences.push({ kind: "PARAMETER", path: `${majorName} / ${after.stepName} / 参数 ${index}定义`, before: parameterDefinition(beforeName, beforeUnit), after: parameterDefinition(afterName, afterUnit) });
  }
  if (beforeValue === afterValue) return;
  const name = (index === 1 ? after.parameter1Name || before.parameter1Name : after.parameter2Name || before.parameter2Name) || `参数 ${index}`;
  differences.push({ kind: "PARAMETER", path: `${majorName} / ${after.stepName} / ${name}`, before: beforeValue, after: afterValue, numericDelta: parameterDelta(before, after, index, beforeValue, afterValue) });
}

function compareFormula(majorName: string, before: MinorProcessStepDraft, after: MinorProcessStepDraft, differences: TrialDifference[], baselineTrial: TrialScheme, candidateTrial: TrialScheme) {
  const beforeValues = before.materials.filter(external);
  const afterValues = after.materials.filter(external);
  reportMissingIdentities(`${majorName} / ${after.stepName} / 配方`, beforeValues, afterValues, materialIdentity, item => item.materialName, differences);
  const beforeMaterials = groupedIndex(beforeValues, materialIdentity);
  const afterMaterials = groupedIndex(afterValues, materialIdentity);
  for (const identity of new Set([...beforeMaterials.keys(), ...afterMaterials.keys()])) {
    const beforeGroup = beforeMaterials.get(identity) || [];
    const afterGroup = afterMaterials.get(identity) || [];
    if (beforeGroup.length !== 1 || afterGroup.length !== 1) {
      differences.push({ kind: "INCOMPARABLE", path: `${majorName} / ${after.stepName} / 配方 ${identity}（标识不唯一或未匹配）`, before: itemNames(beforeGroup, item => item.materialName), after: itemNames(afterGroup, item => item.materialName) });
      continue;
    }
    const beforeMaterial = beforeGroup[0]!;
    const afterMaterial = afterGroup[0]!;
    const beforeActualWeight = nullableNumber(beforeMaterial.weightKg);
    const afterActualWeight = nullableNumber(afterMaterial.weightKg);
    if (beforeActualWeight !== afterActualWeight) {
      differences.push({ kind: "FORMULA", path: `${majorName} / ${after.stepName} / ${afterMaterial.materialName || beforeMaterial.materialName || identity}`, before: beforeActualWeight, after: afterActualWeight });
    }
    const beforePlannedWeight = nullableNumber(baselineTrial.plannedData.materialWeightsKg[nodeId(beforeMaterial)]);
    const afterPlannedWeight = nullableNumber(candidateTrial.plannedData.materialWeightsKg[nodeId(afterMaterial)]);
    if (beforePlannedWeight !== afterPlannedWeight) {
      differences.push({ kind: "FORMULA", path: `${majorName} / ${after.stepName} / ${afterMaterial.materialName || beforeMaterial.materialName || identity}计划投入`, before: beforePlannedWeight, after: afterPlannedWeight });
    }
  }
}

function compareInputs(majorName: string, before: ProcessInputDraft[], after: ProcessInputDraft[], differences: TrialDifference[]) {
  reportMissingIdentities(`${majorName} / 投入`, before, after, inputIdentity, item => item.materialName, differences);
  const beforeInputs = groupedIndex(before, inputIdentity);
  const afterInputs = groupedIndex(after, inputIdentity);
  for (const identity of new Set([...beforeInputs.keys(), ...afterInputs.keys()])) {
    const beforeGroup = beforeInputs.get(identity) || [];
    const afterGroup = afterInputs.get(identity) || [];
    if (beforeGroup.length !== 1 || afterGroup.length !== 1) {
      differences.push({ kind: "INCOMPARABLE", path: `${majorName} / 投入 ${identity}（标识不唯一或未匹配）`, before: itemNames(beforeGroup, item => item.materialName), after: itemNames(afterGroup, item => item.materialName) });
      continue;
    }
    const beforeInput = beforeGroup[0]!;
    const afterInput = afterGroup[0]!;
    const beforeWeight = nullableNumber(beforeInput?.weightKg);
    const afterWeight = nullableNumber(afterInput?.weightKg);
    if (beforeWeight === afterWeight && Boolean(beforeInput) === Boolean(afterInput)) continue;
    differences.push({ kind: "INPUT", path: `${majorName} / ${afterInput?.materialName || beforeInput?.materialName || identity}投入`, before: beforeWeight, after: afterWeight });
  }
}

function originGroups(trial: TrialScheme) {
  const groups = new Map<string, MajorProcessDraft[]>();
  for (const major of trial.plan.majorProcesses) {
    const id = nodeId(major);
    const origin = id ? trial.majorOrigins[id] : undefined;
    if (origin) groups.set(origin, [...groups.get(origin) || [], major]);
  }
  return groups;
}

function appendUnidentifiedMajors(baseline: TrialScheme, candidate: TrialScheme, comparisons: MajorComparison[], differences: TrialDifference[]) {
  for (const [side, trial] of [["before", baseline], ["after", candidate]] as const) {
    for (const major of trial.plan.majorProcesses) {
      const id = nodeId(major);
      if (id && trial.majorOrigins[id]) continue;
      const label = major.processName || "未命名大工序";
      comparisons.push({ candidateMajorId: side === "after" ? id : null, baselineMajorId: side === "before" ? id : null, label, comparable: false, reason: "缺少唯一来源标识，不可按名称或行号比较" });
      differences.push({ kind: "INCOMPARABLE", path: `${label} / 大工序缺少唯一来源标识`, before: side === "before" ? label : null, after: side === "after" ? label : null });
    }
  }
}

function reportMissingIdentities<T>(path: string, before: T[], after: T[], identity: (value: T) => string | null, name: (value: T) => string, differences: TrialDifference[]) {
  for (const item of before.filter(value => !identity(value))) {
    differences.push({ kind: "INCOMPARABLE", path: `${path} / 缺少唯一标识`, before: name(item) || null, after: null });
  }
  for (const item of after.filter(value => !identity(value))) {
    differences.push({ kind: "INCOMPARABLE", path: `${path} / 缺少唯一标识`, before: null, after: name(item) || null });
  }
}

function itemNames<T>(items: T[], name: (value: T) => string) {
  return items.length ? items.map(item => name(item) || "未命名").join("、") : null;
}

function majorNames(majors: MajorProcessDraft[]) { return itemNames(majors, major => major.processName); }

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

function parameterDefinition(name: string | null, unit: string | null) {
  return name ? `${name}${unit ? `（${unit}）` : ""}` : unit ? `未命名（${unit}）` : null;
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

function primaryMaterialIdentity(major: MajorProcessDraft, allMajors: MajorProcessDraft[], visited = new Set<string>()): { identity: string | null; stable: boolean } {
  const majorId = nodeId(major);
  if (visited.has(majorId)) return { identity: null, stable: false };
  visited.add(majorId);
  const primary = [...major.steps].sort((left, right) => left.sequence - right.sequence)
    .flatMap(step => [...step.materials].sort((left, right) => left.sequence - right.sequence))
    .filter(material => material.materialRole === "PRIMARY");
  if (!primary.length) return { identity: null, stable: true };
  const externalPrimary = primary.filter(material => material.sourceType !== "STEP_OUTPUT");
  if (externalPrimary.length) {
    if (externalPrimary.length !== 1) return { identity: null, stable: false };
    const identity = materialIdentity(externalPrimary[0]!);
    return { identity, stable: Boolean(identity) };
  }
  const sourceId = primary[0]!.sourceStepOutputId;
  if (!sourceId) return { identity: null, stable: false };
  const producers = allMajors.filter(candidate => candidate.steps.some(step => (step.outputs || []).some(output => nodeId(output) === sourceId)));
  if (producers.length !== 1) return { identity: null, stable: false };
  return primaryMaterialIdentity(producers[0]!, allMajors, visited);
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
  return major.yieldBasis === "NONE" ? null : calculateMajorProcessYield(major).mainYieldPercent;
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
  const consumedOutputIds = new Set(orderedMajors.flatMap(major => major.steps.flatMap(step => step.materials
    .filter(item => item.sourceType === "STEP_OUTPUT" && item.sourceStepOutputId)
    .map(item => item.sourceStepOutputId!))));
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
      const terminal = !consumedOutputIds.has(nodeId(output));
      if (nullableNumber(input.weightKg) == null || nullableNumber(output.weightKg) == null || Number(input.weightKg) <= 0 || Number(output.weightKg) < 0 || (!terminal && Number(output.weightKg) === 0)) return false;
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
