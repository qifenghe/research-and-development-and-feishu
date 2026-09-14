<template>
  <section class="comparison">
    <header>
      <div><b>计划 / 实际与 A/B 差异</b><small>缺失实测保持“待补充”，不会按 0 参与排序</small></div>
      <a-select v-model:value="baselineId" allow-clear placeholder="选择基线方案" :options="baselineOptions" style="width: 220px" />
    </header>

    <a-table :columns="plannedColumns" :data-source="plannedRows" row-key="key" size="small" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'planned'">{{ display(record.planned, record.kind) }}</template>
        <template v-else-if="column.key === 'actual'"><span :class="{ missing: !record.complete }">{{ record.complete ? display(record.actual, record.kind) : "待补充" }}</span></template>
        <template v-else-if="column.key === 'difference'">{{ record.difference == null ? "—" : signed(record.difference, record.kind) }}</template>
      </template>
    </a-table>

    <template v-if="comparison">
      <div class="major-status">
        <a-tag v-for="item in comparison.majorComparisons" :key="`${item.baselineMajorId}-${item.candidateMajorId}-${item.label}`" :color="item.comparable ? 'green' : 'orange'">
          {{ item.label }} · {{ item.comparable ? "可比较" : item.reason }}
        </a-tag>
      </div>
      <a-empty v-if="!comparison.differences.length" :image="false" description="与基线方案无可见差异" />
      <div v-for="item in comparison.differences" :key="`${item.kind}-${item.path}`" class="difference">
        <a-tag :color="item.kind === 'INCOMPARABLE' ? 'orange' : 'blue'">{{ kindLabel(item.kind) }}</a-tag>
        <span><b>{{ item.path }}</b><small>{{ display(item.before) }} → {{ display(item.after) }}<template v-if="item.numericDelta != null"> · 差异 {{ item.numericDelta > 0 ? "+" : "" }}{{ item.numericDelta }}</template></small></span>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { TrialScheme } from "../../services/trialApi";
import { buildTrialComparison, plannedActualRows, type TrialDifferenceKind } from "./trialComparison";

const props = defineProps<{ trial: TrialScheme; trials: TrialScheme[]; baselineTrialId?: string }>();
const emit = defineEmits<{ "update:baselineTrialId": [value: string | undefined] }>();
const baselineId = computed({ get: () => props.baselineTrialId, set: value => emit("update:baselineTrialId", value) });
const plannedRows = computed(() => plannedActualRows(props.trial));
const baselineOptions = computed(() => props.trials.filter(item => item.id !== props.trial.id).map(item => ({ value: item.id, label: `${item.name}${item.archived ? "（已归档）" : ""}` })));
const baseline = computed(() => props.trials.find(item => item.id === baselineId.value));
const comparison = computed(() => baseline.value ? buildTrialComparison(props.trial, baseline.value) : null);
const plannedColumns = [
  { title: "项目", dataIndex: "label", key: "label" },
  { title: "计划", key: "planned", width: 120 },
  { title: "实际", key: "actual", width: 120 },
  { title: "差异", key: "difference", width: 110 },
];

function display(value: unknown, kind?: string) {
  if (value == null || value === "") return "—";
  if (typeof value === "number") return `${Number(value.toFixed(4))}${kind?.includes("YIELD") ? "%" : kind === "MATERIAL" ? "kg" : ""}`;
  return String(value);
}
function signed(value: number, kind: string) { return `${value > 0 ? "+" : ""}${Number(value.toFixed(4))}${kind.includes("YIELD") ? "%" : "kg"}`; }
function kindLabel(kind: TrialDifferenceKind) { return ({ QUALITY: "质量", DIFFICULTY: "难度", PARAMETER: "参数", FORMULA: "配方", INPUT: "投入", YIELD: "得率", INCOMPARABLE: "不可比较" })[kind]; }
</script>

<style scoped>
.comparison { display: grid; gap: 10px; padding: 13px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.comparison header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.comparison header small, .difference small { display: block; color: #86909c; font-size: 12px; }
.missing { color: #d46b08; }
.major-status { display: flex; flex-wrap: wrap; gap: 6px; }
.difference { display: flex; gap: 8px; align-items: start; padding-top: 7px; border-top: 1px solid #f0f0f0; }
.difference span, .difference b { display: block; }
</style>
