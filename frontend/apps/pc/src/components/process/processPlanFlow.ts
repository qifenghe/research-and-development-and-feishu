import type {
  MajorProcessDraft,
  MinorProcessStepDraft,
  ProcessPlanDraft,
  ProcessStepMaterialDraft,
  StepOutputDraft,
} from "../../../../../packages/shared/src/process-plan";
import { cloneVueValue } from "./cloneVueValue.ts";

let flowKey = 0;
const nextFlowKey = (prefix: string) => `${prefix}-${Date.now()}-${++flowKey}`;

type StepRef = { major: MajorProcessDraft; step: MinorProcessStepDraft };
type OutputRef = StepRef & { output: StepOutputDraft };

export interface RemovedFlowConsumer {
  majorKey: string;
  majorName: string;
  stepKey: string;
  stepName: string;
  materialKey: string;
  materialName: string;
  sourceOutputId?: string;
  reason: "MISSING" | "DUPLICATE" | "PRECEDENCE" | "FLOW_DISABLED" | "PRIMARY_INCOMPATIBLE" | "EXTERNAL_SOURCE";
  action: "REMOVED" | "CLEARED_SOURCE";
}

export function previousFlowOutputs(
  plan: ProcessPlanDraft,
  majorKey: string,
  stepKey: string,
  primaryOnly = false,
) {
  const ordered = orderedSteps(plan);
  const counts = new Map<string, number>();
  for (const ref of ordered) {
    for (const output of ref.step.outputs || []) {
      const id = output.id || output.key;
      counts.set(id, (counts.get(id) || 0) + 1);
    }
  }
  const index = ordered.findIndex(ref => ref.major.key === majorKey && ref.step.key === stepKey);
  return ordered.slice(0, Math.max(0, index)).flatMap(ref => (ref.step.outputs || [])
    .filter(output => counts.get(output.id || output.key) === 1 && output.continueFlow && (!primaryOnly || output.primaryOutput))
    .map(output => ({ output, major: ref.major, step: ref.step })));
}

export function repairProcessPlanFlow(plan: ProcessPlanDraft): {
  plan: ProcessPlanDraft;
  removedConsumers: RemovedFlowConsumer[];
} {
  const next = cloneVueValue(plan);
  resequence(next);
  const steps = orderedSteps(next);
  const outputCounts = new Map<string, number>();
  const outputs = new Map<string, OutputRef>();
  const outputGroups = new Map<string, OutputRef[]>();
  for (const ref of steps) {
    for (const output of ref.step.outputs || []) {
      const id = output.id || output.key;
      outputCounts.set(id, (outputCounts.get(id) || 0) + 1);
      if (!outputs.has(id)) outputs.set(id, { ...ref, output });
      outputGroups.set(id, [...(outputGroups.get(id) || []), { ...ref, output }]);
    }
  }
  for (const group of outputGroups.values()) {
    for (const duplicate of group.slice(1)) {
      const id = nextFlowKey("output");
      duplicate.output.id = id;
      duplicate.output.key = id;
    }
  }

  const removedConsumers: RemovedFlowConsumer[] = [];
  for (const ref of steps) {
    ref.step.materials = ref.step.materials.filter(material => {
      if (material.sourceType === "EXTERNAL" && material.sourceStepOutputId) {
        removedConsumers.push(consumer(ref, material, "EXTERNAL_SOURCE", "CLEARED_SOURCE"));
        delete material.sourceStepOutputId;
        return true;
      }
      if (material.sourceType !== "STEP_OUTPUT") return true;
      const id = material.sourceStepOutputId;
      const producer = id ? outputs.get(id) : undefined;
      let reason: RemovedFlowConsumer["reason"] | undefined;
      if (!id || !producer) reason = "MISSING";
      else if ((outputCounts.get(id) || 0) !== 1) reason = "DUPLICATE";
      else if (!precedes(producer, ref)) reason = "PRECEDENCE";
      else if (!producer.output.continueFlow) reason = "FLOW_DISABLED";
      else if (material.materialRole === "PRIMARY" && !producer.output.primaryOutput) reason = "PRIMARY_INCOMPATIBLE";
      if (!reason) return true;
      removedConsumers.push(consumer(ref, material, reason, "REMOVED"));
      return false;
    });
  }
  return { plan: next, removedConsumers };
}

export function copyMajorProcess(plan: ProcessPlanDraft, majorKey: string) {
  const next = cloneVueValue(plan);
  const index = next.majorProcesses.findIndex(major => major.key === majorKey);
  if (index < 0) return { plan: next, removedConsumers: [] as RemovedFlowConsumer[] };
  const item = cloneVueValue(next.majorProcesses[index]!);
  const internalOutputIds = new Map<string, string>();
  item.id = undefined;
  item.key = nextFlowKey("major");
  item.processName = `${item.processName}（副本）`;
  item.inputs.forEach(input => {
    input.id = undefined;
    input.key = nextFlowKey("input");
  });
  item.outputs.forEach(output => {
    output.id = undefined;
    output.key = nextFlowKey("output");
  });
  item.steps.forEach(step => {
    step.id = undefined;
    step.key = nextFlowKey("step");
    (step.outputs || []).forEach(output => {
      const oldId = output.id || output.key;
      const newId = nextFlowKey("output");
      output.id = newId;
      output.key = newId;
      internalOutputIds.set(oldId, newId);
    });
  });
  item.steps.forEach(step => {
    step.materials.forEach(material => {
      material.id = undefined;
      material.key = nextFlowKey("material");
      if (material.sourceStepOutputId && internalOutputIds.has(material.sourceStepOutputId)) {
        material.sourceStepOutputId = internalOutputIds.get(material.sourceStepOutputId);
      }
    });
    (step.controlPoints || []).forEach(point => {
      point.id = undefined;
      point.key = nextFlowKey("control");
      point.measurements.forEach(measurement => {
        measurement.id = undefined;
        measurement.key = nextFlowKey("measurement");
      });
    });
  });
  next.majorProcesses.splice(index + 1, 0, item);
  return repairProcessPlanFlow(next);
}

export async function confirmProcessPlanRepair(
  candidate: ProcessPlanDraft,
  confirm: (consumers: RemovedFlowConsumer[]) => boolean | Promise<boolean>,
): Promise<{ accepted: boolean; plan: ProcessPlanDraft; removedConsumers: RemovedFlowConsumer[] }> {
  const original = cloneVueValue(candidate);
  const repaired = repairProcessPlanFlow(candidate);
  if (repaired.removedConsumers.length && !await confirm(repaired.removedConsumers)) {
    return { accepted: false, plan: original, removedConsumers: repaired.removedConsumers };
  }
  return { accepted: true, ...repaired };
}

function orderedSteps(plan: ProcessPlanDraft): StepRef[] {
  return [...plan.majorProcesses]
    .sort((left, right) => left.sequence - right.sequence)
    .flatMap(major => [...major.steps]
      .sort((left, right) => left.sequence - right.sequence)
      .map(step => ({ major, step })));
}

function precedes(producer: StepRef, consumer: StepRef) {
  return producer.major.sequence !== consumer.major.sequence
    ? producer.major.sequence < consumer.major.sequence
    : producer.step.sequence < consumer.step.sequence;
}

function resequence(plan: ProcessPlanDraft) {
  plan.majorProcesses.forEach((major, majorIndex) => {
    major.sequence = majorIndex + 1;
    major.steps.forEach((step, stepIndex) => {
      step.sequence = stepIndex + 1;
      step.materials.forEach((material, materialIndex) => { material.sequence = materialIndex + 1; });
      (step.outputs || []).forEach((output, outputIndex) => { output.sequence = outputIndex + 1; });
    });
  });
}

function consumer(
  ref: StepRef,
  material: ProcessStepMaterialDraft,
  reason: RemovedFlowConsumer["reason"],
  action: RemovedFlowConsumer["action"],
): RemovedFlowConsumer {
  return {
    majorKey: ref.major.key,
    majorName: ref.major.processName,
    stepKey: ref.step.key,
    stepName: ref.step.stepName,
    materialKey: material.key,
    materialName: material.materialName,
    sourceOutputId: material.sourceStepOutputId,
    reason,
    action,
  };
}

/** @deprecated Use repairProcessPlanFlow so broken flow is removed, never externalized. */
export function clearBrokenFlowReferences(plan: ProcessPlanDraft) {
  const repaired = repairProcessPlanFlow(plan);
  return { plan: repaired.plan, cleared: repaired.removedConsumers.length };
}
