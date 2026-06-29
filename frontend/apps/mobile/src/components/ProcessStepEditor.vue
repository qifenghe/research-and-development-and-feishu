<template>
  <div>
    <div v-for="(step, index) in model" :key="index" class="task-card process-step-card">
      <van-field v-model="step.processName" label="工序" placeholder="清洗 / 卤制 / 包装" :readonly="readonly" />
      <van-field v-model="step.beforeWeightKg" label="前重kg" type="number" placeholder="100" :readonly="readonly" />
      <van-field v-model="step.afterWeightKg" label="后重kg" type="number" placeholder="92" :readonly="readonly" />
      <p v-if="lossRate(step)" class="process-step-card__loss">损耗率：{{ lossRate(step) }}%</p>
      <van-field v-model="step.remark" label="备注" placeholder="可选" :readonly="readonly" />
    </div>
    <van-button v-if="!readonly" block plain type="primary" size="small" @click="addStep">+ 添加工序行</van-button>
  </div>
</template>

<script setup lang="ts">
import type { ExperimentProcessStep } from "@rnd/shared";

export type EditableProcessStep = {
  processName: string;
  beforeWeightKg: string;
  afterWeightKg: string;
  remark: string;
};

const model = defineModel<EditableProcessStep[]>({ required: true });

withDefaults(defineProps<{ readonly?: boolean }>(), { readonly: false });

function blankStep(): EditableProcessStep {
  return { processName: "", beforeWeightKg: "", afterWeightKg: "", remark: "" };
}

function addStep() {
  model.value = [...model.value, blankStep()];
}

function lossRate(step: EditableProcessStep): string | null {
  const before = Number(step.beforeWeightKg);
  const after = Number(step.afterWeightKg);
  if (!before || before <= 0 || Number.isNaN(after)) return null;
  return (((before - after) / before) * 100).toFixed(1);
}

export function toProcessSteps(steps: EditableProcessStep[]): ExperimentProcessStep[] {
  return steps
    .filter((s) => s.processName.trim())
    .map((step, index) => {
      const before = Number(step.beforeWeightKg || 0);
      const after = Number(step.afterWeightKg || 0);
      const loss =
        before > 0 && !Number.isNaN(after) ? (before - after) / before : undefined;
      return {
        sequence: index + 1,
        processName: step.processName.trim(),
        beforeWeightKg: before || undefined,
        afterWeightKg: after || undefined,
        lossRate: loss,
        remark: step.remark.trim() || undefined,
      };
    });
}

export function fromProcessSteps(steps: ExperimentProcessStep[]): EditableProcessStep[] {
  if (!steps.length) return [blankStep()];
  return steps.map((step) => ({
    processName: step.processName,
    beforeWeightKg: step.beforeWeightKg != null ? String(step.beforeWeightKg) : "",
    afterWeightKg: step.afterWeightKg != null ? String(step.afterWeightKg) : "",
    remark: step.remark ?? "",
  }));
}
</script>
