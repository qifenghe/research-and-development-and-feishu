<template>
  <div class="process-tabs-editor">
    <a-tabs v-model:activeKey="activeKey" type="card">
      <a-tab-pane v-for="(step, index) in modelValue" :key="step.key" :tab="step.processName || `工序${index + 1}`">
        <a-form layout="vertical">
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="工序名称">
                <a-input v-model:value="step.processName" :disabled="readonly" placeholder="如 清洗、卤制、冷却" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="控制要点/备注">
                <a-input v-model:value="step.remark" :disabled="readonly" placeholder="可选" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="6">
              <a-form-item label="本步投入重量 kg">
                <a-input-number v-model:value="step.beforeWeightKg" :min="0" style="width: 100%" :disabled="readonly" />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="下一步出成 kg">
                <a-input-number
                  v-model:value="step.afterWeightKg"
                  :min="0"
                  style="width: 100%"
                  :disabled="readonly"
                  @change="syncNextInput(index)"
                />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="余料重量 kg">
                <a-input-number v-model:value="step.remainingWeightKg" :min="0" style="width: 100%" :disabled="readonly" />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item label="余料去向">
                <a-select v-model:value="step.remainingDisposition" :disabled="readonly" style="width: 100%">
                  <a-select-option value="REUSE">回用</a-select-option>
                  <a-select-option value="RETURN">退回</a-select-option>
                  <a-select-option value="DISCARD">废弃</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
          <div class="loss-result">
            <span>损耗重量：{{ lossOf(step).lossWeightKg.toFixed(3) }} kg</span>
            <span>损耗率：{{ lossOf(step).lossRate.toFixed(2) }}%</span>
          </div>
          <a-space v-if="!readonly" class="process-actions">
            <a-button :disabled="index === 0" @click="moveProcess(index, index - 1)">上移</a-button>
            <a-button :disabled="index === modelValue.length - 1" @click="moveProcess(index, index + 1)">下移</a-button>
            <a-button @click="copyProcess(index)">复制</a-button>
            <a-button danger :disabled="modelValue.length === 1" @click="removeProcess(index)">删除</a-button>
          </a-space>
        </a-form>
      </a-tab-pane>
    </a-tabs>
    <a-button v-if="!readonly" type="dashed" block @click="addProcess">+ 添加工序</a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { processLoss, type RemainingDisposition } from "@rnd/shared";

export interface ProcessRow {
  key: number;
  processName: string;
  beforeWeightKg: number | null;
  afterWeightKg: number | null;
  remainingWeightKg: number | null;
  remainingDisposition: RemainingDisposition;
  remark: string;
}

const props = defineProps<{
  modelValue: ProcessRow[];
  readonly?: boolean;
  createRow: () => ProcessRow;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: ProcessRow[]];
}>();

const activeKey = ref<number | string>(props.modelValue[0]?.key ?? 0);

watch(
  () => props.modelValue,
  (rows) => {
    if (!rows.some((row) => row.key === activeKey.value)) {
      activeKey.value = rows[0]?.key ?? 0;
    }
  },
);

function lossOf(step: ProcessRow) {
  try {
    return processLoss(step.beforeWeightKg, step.afterWeightKg, step.remainingWeightKg ?? 0);
  } catch {
    return { lossWeightKg: 0, lossRate: 0 };
  }
}

function syncNextInput(index: number) {
  const next = [...props.modelValue];
  const current = next[index];
  const following = next[index + 1];
  if (!current || !following || following.beforeWeightKg != null) return;
  following.beforeWeightKg = current.afterWeightKg;
  emit("update:modelValue", next);
}

function addProcess() {
  const row = props.createRow();
  emit("update:modelValue", [...props.modelValue, row]);
  activeKey.value = row.key;
}

function removeProcess(index: number) {
  const next = props.modelValue.filter((_, itemIndex) => itemIndex !== index);
  emit("update:modelValue", next.length ? next : [props.createRow()]);
}

function moveProcess(fromIndex: number, toIndex: number) {
  if (toIndex < 0 || toIndex >= props.modelValue.length) return;
  const next = [...props.modelValue];
  const [row] = next.splice(fromIndex, 1);
  if (!row) return;
  next.splice(toIndex, 0, row);
  emit("update:modelValue", next);
}

function copyProcess(index: number) {
  const source = props.modelValue[index];
  if (!source) return;
  const row = { ...source, key: props.createRow().key, processName: `${source.processName || "工序"}（副本）` };
  const next = [...props.modelValue];
  next.splice(index + 1, 0, row);
  emit("update:modelValue", next);
  activeKey.value = row.key;
}
</script>

<style scoped>
.loss-result{display:flex;gap:20px;margin:4px 0 16px;padding:12px 14px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;color:#334155}.process-actions{margin-bottom:12px}
</style>
