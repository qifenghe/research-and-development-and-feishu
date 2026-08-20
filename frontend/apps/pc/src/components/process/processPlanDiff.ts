import type {
  ControlMeasurementDraft,
  ControlPointDraft,
  MajorProcessDraft,
  MinorProcessStepDraft,
  ProcessInputDraft,
  ProcessOutputDraft,
  ProcessPlanDraft,
  ProcessStepMaterialDraft,
  StepOutputDraft,
} from "../../../../../packages/shared/src/process-plan";

export type ProcessPlanDifferenceType = "ADDED" | "REMOVED" | "CHANGED";

export interface ProcessPlanDifference {
  type: ProcessPlanDifferenceType;
  path: string;
  before?: string;
  after?: string;
}

type Difference = ProcessPlanDifference;
type Entity = { id?: string; key: string; sequence: number };
type DiffContext = {
  outputReferences: Map<string, string>;
  materialReferences: Map<string, string>;
};
type ContextPair = { before: DiffContext; after: DiffContext };

export function diffProcessPlans(source: ProcessPlanDraft, current: ProcessPlanDraft): ProcessPlanDifference[] {
  const differences: Difference[] = [];
  const contexts = { before: buildContext(source), after: buildContext(current) };
  changed(differences, "工艺方案 / 物料平衡允许差", source.balanceToleranceKg, current.balanceToleranceKg, weight);
  compareEntities(
    differences,
    "大工序",
    source.majorProcesses || [],
    current.majorProcesses || [],
    major => `${major.sequence}. ${major.processName || "未命名大工序"}`,
    major => semanticKey(major.processCode, major.processName),
    compareMajor,
    contexts,
  );
  return differences.sort((left, right) => left.path.localeCompare(right.path, "zh-CN")
    || rank(left.type) - rank(right.type)
    || String(left.before).localeCompare(String(right.before), "zh-CN")
    || String(left.after).localeCompare(String(right.after), "zh-CN"));
}

function compareMajor(differences: Difference[], path: string, before: MajorProcessDraft, after: MajorProcessDraft, contexts: ContextPair) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["工序代码", "processCode"], ["工序名称", "processName"],
    ["工序说明", "description"], ["得率口径", "yieldBasis"], ["差异说明/备注", "remark"],
  ]);
  compareEntities(differences, `${path} / 旧版投入`, before.inputs || [], after.inputs || [], inputSummary, input => semanticKey(input.inputRole, input.materialCode, input.materialName), compareInput, contexts);
  compareEntities(differences, `${path} / 旧版产出`, before.outputs || [], after.outputs || [], outputSummary, output => semanticKey(output.outputType), compareMajorOutput, contexts);
  compareEntities(differences, `${path} / 小步骤`, before.steps || [], after.steps || [], step => `${step.sequence}. ${step.stepName || "未命名步骤"}`, step => semanticKey(step.stepCode, step.stepName, step.stepType), compareStep, contexts);
}

function compareInput(differences: Difference[], path: string, before: ProcessInputDraft, after: ProcessInputDraft, contexts: ContextPair) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["角色", "inputRole"], ["物料编码", "materialCode"],
    ["物料名称", "materialName"], ["重量", "weightKg", weight],
  ]);
  changed(differences, `${path} / 来源投料`, resolveReference(contexts.before.materialReferences, before.sourceStepMaterialId), resolveReference(contexts.after.materialReferences, after.sourceStepMaterialId));
}

function compareMajorOutput(differences: Difference[], path: string, before: ProcessOutputDraft, after: ProcessOutputDraft) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["类型", "outputType"], ["重量", "weightKg", weight], ["备注", "remark"],
  ]);
}

function compareStep(differences: Difference[], path: string, before: MinorProcessStepDraft, after: MinorProcessStepDraft, contexts: ContextPair) {
  const beforeWithRemark = before as MinorProcessStepDraft & { remark?: string };
  const afterWithRemark = after as MinorProcessStepDraft & { remark?: string };
  fields(differences, path, beforeWithRemark, afterWithRemark, [
    ["顺序", "sequence"], ["步骤代码", "stepCode"], ["步骤名称", "stepName"], ["步骤类型", "stepType"],
    ["参数 1 名称", "parameter1Name"], ["参数 1 值", "parameter1Value"], ["参数 1 单位", "parameter1Unit"],
    ["参数 2 名称", "parameter2Name"], ["参数 2 值", "parameter2Value"], ["参数 2 单位", "parameter2Unit"],
    ["设备", "equipment"], ["操作要求", "instruction"], ["步骤备注", "remark"],
  ]);
  compareEntities(differences, `${path} / 投料`, before.materials || [], after.materials || [], materialSummary, material => semanticKey(material.sourceType, material.materialRole, material.materialCode, material.materialName), compareMaterial, contexts);
  compareEntities(differences, `${path} / 产出`, before.outputs || [], after.outputs || [], stepOutputSummary, output => semanticKey(output.outputType, output.outputName, output.materialState), compareStepOutput, contexts);
  compareEntities(differences, `${path} / 关键控制点`, before.controlPoints || [], after.controlPoints || [], controlSummary, control => semanticKey(control.controlType, control.importance, control.itemName), compareControl, contexts);
}

function compareMaterial(differences: Difference[], path: string, before: ProcessStepMaterialDraft, after: ProcessStepMaterialDraft, contexts: ContextPair) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["来源类型", "sourceType"],
    ["角色", "materialRole"], ["配方物料 ID", "formulaMaterialId"], ["物料编码", "materialCode"],
    ["物料名称", "materialName"], ["物料状态", "materialState"], ["重量", "weightKg", weight], ["备注", "remark"],
  ]);
  changed(differences, `${path} / 来源产出`, resolveReference(contexts.before.outputReferences, before.sourceStepOutputId), resolveReference(contexts.after.outputReferences, after.sourceStepOutputId));
}

function compareStepOutput(differences: Difference[], path: string, before: StepOutputDraft, after: StepOutputDraft) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["类型", "outputType"], ["名称", "outputName"], ["物料状态", "materialState"],
    ["重量", "weightKg", weight], ["主产出", "primaryOutput", yesNo], ["继续流转", "continueFlow", yesNo], ["备注", "remark"],
  ]);
}

function compareControl(differences: Difference[], path: string, before: ControlPointDraft, after: ControlPointDraft, contexts: ContextPair) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["控制类型", "controlType"], ["重要性", "importance"], ["项目", "itemName"],
    ["目标值", "targetValue"], ["下限", "lowerLimit"], ["上限", "upperLimit"], ["单位", "unit"],
    ["方法", "method"], ["工具", "measurementTool"], ["频次", "frequency"], ["偏差要求", "deviationAction"],
    ["偏差已闭环", "resolved", yesNo], ["确认人", "confirmedBy"], ["确认时间", "confirmedAt"], ["依据/备注", "basisOrRemark"],
  ]);
  compareEntities(differences, `${path} / 实测`, before.measurements || [], after.measurements || [], measurementSummary, () => "", compareMeasurement, contexts);
}

function compareMeasurement(differences: Difference[], path: string, before: ControlMeasurementDraft, after: ControlMeasurementDraft) {
  fields(differences, path, before, after, [
    ["顺序", "sequence"], ["实测值", "measuredValue"], ["测量时间", "measuredAt"], ["判定", "result"],
    ["偏差处理", "deviationAction"], ["复测结果", "retestResult"], ["备注", "remark"],
  ]);
}

type Field<T> = readonly [string, keyof T, formatter?: (value: unknown) => string];

function fields<T extends object>(differences: Difference[], path: string, before: T, after: T, descriptors: Field<T>[]) {
  for (const [label, key, formatter] of descriptors) {
    changed(differences, `${path} / ${label}`, before[key], after[key], formatter);
  }
}

function compareEntities<T extends Entity>(
  differences: Difference[],
  path: string,
  before: T[],
  after: T[],
  summary: (entity: T) => string,
  semanticIdentity: (entity: T) => string,
  compare: (differences: Difference[], path: string, before: T, after: T, contexts: ContextPair) => void,
  contexts: ContextPair,
) {
  const previous = stableOrder(before);
  const next = stableOrder(after);
  const pairs = semanticPairs(previous, next, semanticIdentity);
  for (const pair of pairs) {
    const entity = pair.after || pair.before!;
    const itemPath = `${path} [${entity.sequence}]`;
    if (!pair.before && pair.after) {
      differences.push({ type: "ADDED", path: itemPath, after: summary(pair.after) });
    } else if (pair.before && !pair.after) {
      differences.push({ type: "REMOVED", path: itemPath, before: summary(pair.before) });
    } else if (pair.before && pair.after) {
      compare(differences, `${itemPath} ${summary(pair.after)}`, pair.before, pair.after, contexts);
    }
  }
}

function stableOrder<T extends Entity>(entities: T[]) {
  return entities.map((entity, index) => ({ entity, index }))
    .sort((left, right) => left.entity.sequence - right.entity.sequence || left.index - right.index)
    .map(item => item.entity);
}

function semanticPairs<T extends Entity>(before: T[], after: T[], identity: (entity: T) => string) {
  const pairs: Array<{ before?: T; after?: T }> = [];
  const usedBefore = new Set<number>();
  const usedAfter = new Set<number>();
  const beforeIdentities = identityCounts(before, identity);
  const afterIdentities = identityCounts(after, identity);
  before.forEach((entity, beforeIndex) => {
    const key = identity(entity);
    if (!key || beforeIdentities.get(key) !== 1 || afterIdentities.get(key) !== 1) return;
    const afterIndex = after.findIndex(item => identity(item) === key);
    if (afterIndex >= 0) {
      pairs.push({ before: entity, after: after[afterIndex] });
      usedBefore.add(beforeIndex);
      usedAfter.add(afterIndex);
    }
  });
  before.forEach((entity, beforeIndex) => {
    if (usedBefore.has(beforeIndex)) return;
    let afterIndex = after.findIndex((item, index) => !usedAfter.has(index) && item.sequence === entity.sequence);
    if (afterIndex < 0) afterIndex = after.findIndex((_, index) => !usedAfter.has(index));
    if (afterIndex < 0) {
      pairs.push({ before: entity });
      usedBefore.add(beforeIndex);
      return;
    }
    pairs.push({ before: entity, after: after[afterIndex] });
    usedBefore.add(beforeIndex);
    usedAfter.add(afterIndex);
  });
  after.forEach((entity, index) => {
    if (!usedAfter.has(index)) pairs.push({ after: entity });
  });
  return pairs.sort((left, right) => (left.after || left.before)!.sequence - (right.after || right.before)!.sequence);
}

function identityCounts<T>(entities: T[], identity: (entity: T) => string) {
  const counts = new Map<string, number>();
  entities.forEach(entity => {
    const key = identity(entity);
    if (key) counts.set(key, (counts.get(key) || 0) + 1);
  });
  return counts;
}

function buildContext(plan: ProcessPlanDraft): DiffContext {
  const outputReferences = new Map<string, string>();
  const materialReferences = new Map<string, string>();
  stableOrder(plan.majorProcesses || []).forEach(major => {
    stableOrder(major.steps || []).forEach(step => {
      const stepPath = `大工序 ${major.sequence} / 小步骤 ${step.sequence}`;
      stableOrder(step.outputs || []).forEach(output => {
        const reference = `${stepPath} / 产出 ${output.sequence}`;
        if (output.id) outputReferences.set(output.id, reference);
        outputReferences.set(output.key, reference);
      });
      stableOrder(step.materials || []).forEach(material => {
        const reference = `${stepPath} / 投料 ${material.sequence}`;
        if (material.id) materialReferences.set(material.id, reference);
        materialReferences.set(material.key, reference);
      });
    });
  });
  return { outputReferences, materialReferences };
}

function resolveReference(references: Map<string, string>, value?: string) {
  if (!value) return undefined;
  return references.get(value) || `未知引用（${value}）`;
}

function semanticKey(...values: unknown[]) {
  return values.map(value => display(value).trim().toLocaleLowerCase("zh-CN")).join("|");
}

function changed(
  differences: Difference[],
  path: string,
  before: unknown,
  after: unknown,
  formatter: (value: unknown) => string = display,
) {
  const previous = formatter(before);
  const next = formatter(after);
  if (previous !== next) differences.push({ type: "CHANGED", path, before: previous, after: next });
}

function inputSummary(value: ProcessInputDraft) { return `${value.sequence}. ${value.materialName || "未命名投入"}`; }
function outputSummary(value: ProcessOutputDraft) { return `${value.sequence}. ${value.outputType || "未命名产出"}`; }
function materialSummary(value: ProcessStepMaterialDraft) { return `${value.sequence}. ${value.materialName || "未命名投料"}`; }
function stepOutputSummary(value: StepOutputDraft) { return `${value.sequence}. ${value.outputName || "未命名产出"}`; }
function controlSummary(value: ControlPointDraft) { return `${value.sequence}. ${value.itemName || "未命名控制点"}`; }
function measurementSummary(value: ControlMeasurementDraft) { return `${value.sequence}. ${display(value.measuredValue)}`; }
function display(value: unknown) { return value == null || value === "" ? "未填写" : String(value); }
function weight(value: unknown) { return value == null || value === "" ? "未填写" : `${Number(value)} kg`; }
function yesNo(value: unknown) { return value ? "是" : "否"; }
function rank(type: ProcessPlanDifferenceType) { return type === "REMOVED" ? 0 : type === "ADDED" ? 1 : 2; }
