<template>
  <section class="comparison">
    <header>
      <div><b>计划 / 实际与 A/B 差异</b><small>缺失实测保持“待补充”，不会按 0 参与排序</small></div>
      <a-select v-model:value="baselineId" allow-clear placeholder="选择基线方案" :options="baselineOptions" style="width: 220px" />
    </header>

    <a-collapse ghost>
      <a-collapse-panel key="all-values" header="全部计划 / 实际（展开查看）">
        <a-table :columns="plannedColumns" :data-source="plannedRows" row-key="key" size="small" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'planned'">{{ record.planned == null ? "未设计划" : display(record.planned, record.kind) }}</template>
            <template v-else-if="column.key === 'actual'"><span :class="{ missing: !record.complete }">{{ record.complete ? display(record.actual, record.kind) : record.reason || "待补充" }}</span></template>
            <template v-else-if="column.key === 'difference'">{{ record.difference == null ? (!record.complete ? "待补充" : record.planned == null ? "未设计划" : record.kind === 'PARAMETER' ? (record.planned === record.actual ? "一致" : `${record.planned} → ${record.actual}`) : "—") : signed(record.difference, record.kind, record.unit) }}</template>
          </template>
        </a-table>
      </a-collapse-panel>
    </a-collapse>

    <template v-if="comparison">
      <div class="overview" aria-label="方案并排概览">
        <article v-for="item in overview" :key="item.id">
          <b>{{ item.name }}</b>
          <dl><dt>目的</dt><dd>{{ item.summary.purpose }}</dd><dt>变量</dt><dd>{{ item.summary.variables }}</dd><dt>状态 / 结论</dt><dd>{{ item.summary.status }} / {{ item.summary.conclusion }}</dd><dt>结论说明</dt><dd>{{ item.summary.reason }}</dd><dt>外部实际总投入</dt><dd>{{ item.summary.externalInputKg == null ? "待补充" : `${item.summary.externalInputKg}kg` }}</dd><dt>最终实际得率</dt><dd>{{ item.summary.finalYield == null ? "待补充 / 口径待确认" : `${Number(item.summary.finalYield.toFixed(4))}%` }}</dd></dl>
        </article>
      </div>
      <div class="major-status">
        <a-tag v-for="item in comparison.majorComparisons" :key="`${item.baselineMajorId}-${item.candidateMajorId}-${item.label}`" :color="item.comparable ? 'green' : 'orange'">
          {{ item.label }} · {{ item.comparable ? "可比较" : item.reason }}
        </a-tag>
      </div>
      <a-empty v-if="!comparison.differences.length" :image="false" description="与基线方案无可见差异" />
      <a-collapse ghost><a-collapse-panel key="differences" header="详细参数 / 配方 / 其他差异（展开查看）">
      <div v-for="(item, index) in comparison.differences" :key="`${item.kind}-${item.path}-${index}`" class="difference">
        <a-tag :color="item.kind === 'INCOMPARABLE' ? 'orange' : 'blue'">{{ kindLabel(item.kind) }}</a-tag>
        <span><b>{{ item.path }}</b><small>{{ display(item.before) }} → {{ display(item.after) }}<template v-if="item.numericDelta != null"> · 差异 {{ item.numericDelta > 0 ? "+" : "" }}{{ item.numericDelta }}</template></small></span>
      </div>
      </a-collapse-panel></a-collapse>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { TrialScheme } from "../../services/trialApi";
import { buildTrialComparison, deviationUnit, plannedActualRows, trialOverview, type PlannedActualRow, type TrialDifferenceKind } from "./trialComparison";

const props = defineProps<{ trial: TrialScheme; trials: TrialScheme[]; baselineTrialId?: string }>();
const emit = defineEmits<{ "update:baselineTrialId": [value: string | undefined] }>();
const baselineId = computed({ get: () => props.baselineTrialId, set: value => emit("update:baselineTrialId", value) });
const plannedRows = computed(() => plannedActualRows(props.trial));
const baselineOptions = computed(() => props.trials.filter(item => item.id !== props.trial.id).map(item => ({ value: item.id, label: `${item.name}${item.archived ? "（已归档）" : ""}` })));
const baseline = computed(() => props.trials.find(item => item.id === baselineId.value));
const comparison = computed(() => baseline.value ? buildTrialComparison(props.trial, baseline.value) : null);
const overview = computed(() => baseline.value ? [baseline.value, props.trial].map(item => ({ id: item.id, name: item.name, summary: trialOverview(item) })) : []);
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
function signed(value: number, kind: PlannedActualRow["kind"], unit?: string) { return `${value > 0 ? "+" : ""}${Number(value.toFixed(4))}${deviationUnit(kind, kind === "PARAMETER" ? unit || "" : "kg")}`; }
function kindLabel(kind: TrialDifferenceKind) { return ({ OVERVIEW: "概览", QUALITY: "质量", DIFFICULTY: "难度", PARAMETER: "参数", FORMULA: "配方", INPUT: "投入", YIELD: "得率", INCOMPARABLE: "不可比较" })[kind]; }
</script>

<style scoped>
.comparison { display: grid; gap: 10px; padding: 13px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.comparison { min-width: 0; overflow-wrap: anywhere; }
.comparison header { flex-wrap: wrap; }
.overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.overview article { min-width: 0; padding: 10px; background: #f6f9ff; border-radius: 8px; }
.overview dl { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 4px; margin: 8px 0 0; font-size: 12px; }
.overview dt { color: #86909c; }.overview dd { margin: 0; }
.comparison header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.comparison header small, .difference small { display: block; color: #86909c; font-size: 12px; }
.missing { color: #d46b08; }
.major-status { display: flex; flex-wrap: wrap; gap: 6px; }
.difference { display: flex; gap: 8px; align-items: start; padding-top: 7px; border-top: 1px solid #f0f0f0; }
.difference span, .difference b { display: block; }
</style>
