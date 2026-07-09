<template>
  <div class="formula-editor">
    <div class="formula-summary">
      <span>总投入：{{ totalWeight.toFixed(3) }} kg</span>
      <span>主原料：{{ primaryMaterialName }}</span>
    </div>
    <a-table :columns="columns" :data-source="modelValue" row-key="key" :pagination="false" size="small">
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'materialCategory'">
          <a-select v-model:value="record.materialCategory" :disabled="readonly" style="width: 100%">
            <a-select-option value="RAW">原料</a-select-option>
            <a-select-option value="AUXILIARY">辅料</a-select-option>
            <a-select-option value="PACKAGING">包材</a-select-option>
          </a-select>
        </template>
        <template v-else-if="column.key === 'primaryMaterial'">
          <a-radio :checked="record.primaryMaterial" :disabled="readonly" @change="setPrimary(index)">主原料</a-radio>
        </template>
        <template v-else-if="column.key === 'materialCode'">
          <a-input v-model:value="record.materialCode" :disabled="readonly" placeholder="可选" />
        </template>
        <template v-else-if="column.key === 'materialName'">
          <a-input v-model:value="record.materialName" :disabled="readonly" placeholder="物料名称" />
        </template>
        <template v-else-if="column.key === 'formulaRatio'">
          {{ ratios[index]?.toFixed(2) ?? "0.00" }}%
        </template>
        <template v-else-if="column.key === 'weightKg'">
          <a-input-number v-model:value="record.weightKg" :min="0" style="width: 100%" :disabled="readonly" />
        </template>
        <template v-else-if="column.key === 'inputUnit'">
          <a-select v-model:value="record.inputUnit" :disabled="readonly" style="width: 82px">
            <a-select-option value="kg">kg</a-select-option>
            <a-select-option value="g">g</a-select-option>
          </a-select>
        </template>
        <template v-else-if="column.key === 'utilizationRate'">
          <a-input-number v-model:value="record.utilizationRate" :min="0" :max="100" style="width: 100%" :disabled="readonly" />
        </template>
        <template v-else-if="column.key === 'remark'">
          <a-input v-model:value="record.remark" :disabled="readonly" placeholder="可选" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" danger :disabled="readonly || modelValue.length === 1" @click="removeRow(index)">删除</a-button>
        </template>
      </template>
    </a-table>
    <a-button v-if="!readonly" type="dashed" block class="add-row" @click="addRow">+ 添加配方行</a-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { formulaRatios, type MaterialCategory } from "@rnd/shared";

export interface FormulaRow {
  key: number;
  materialCategory: MaterialCategory;
  primaryMaterial: boolean;
  materialCode: string;
  materialName: string;
  weightKg: number | null;
  inputUnit: string;
  utilizationRate: number;
  remark: string;
}

const props = defineProps<{
  modelValue: FormulaRow[];
  readonly?: boolean;
  createRow: () => FormulaRow;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: FormulaRow[]];
}>();

const columns = [
  { title: "类别", key: "materialCategory", width: 110 },
  { title: "主原料", key: "primaryMaterial", width: 96 },
  { title: "物料编码", key: "materialCode" },
  { title: "物料名称", key: "materialName" },
  { title: "比例", key: "formulaRatio", width: 86 },
  { title: "重量", key: "weightKg", width: 110 },
  { title: "单位", key: "inputUnit", width: 92 },
  { title: "利用率", key: "utilizationRate", width: 110 },
  { title: "备注", key: "remark" },
  { title: "操作", key: "action", width: 76 },
];

const weights = computed(() => props.modelValue.map((item) => item.weightKg ?? 0));
const ratios = computed(() => formulaRatios(weights.value));
const totalWeight = computed(() => weights.value.reduce((sum, weight) => sum + weight, 0));
const primaryMaterialName = computed(() => {
  const primary = props.modelValue.find((item) => item.primaryMaterial);
  return primary?.materialName?.trim() || "未选择";
});

function setPrimary(index: number) {
  const next = props.modelValue.map((item, itemIndex) => ({
    ...item,
    primaryMaterial: itemIndex === index,
  }));
  emit("update:modelValue", next);
}

function addRow() {
  emit("update:modelValue", [...props.modelValue, props.createRow()]);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, itemIndex) => itemIndex !== index);
  emit("update:modelValue", next.length ? next : [props.createRow()]);
}
</script>

<style scoped>
.formula-summary{display:flex;gap:16px;margin-bottom:12px;color:#64748b}.add-row{margin-top:12px}
</style>
