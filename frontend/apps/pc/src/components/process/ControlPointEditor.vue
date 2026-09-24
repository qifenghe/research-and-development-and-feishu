<template>
  <section class="control-points">
    <header>
      <div><b>关键控制点</b><small>极重要项目未闭环时将阻止正式提交</small></div>
      <a-button v-if="!readonly" size="small" type="dashed" @click="addPoint">添加控制点</a-button>
    </header>
    <div v-for="(point, index) in points" :key="point.key" class="control-card" :class="{ blocking: isBlocking(point) }">
      <div class="point-heading">
        <a-select v-model:value="point.importance" :disabled="readonly" size="small" style="width:84px" :options="importanceOptions" @update:value="publish" />
        <a-tag :color="isBlocking(point) ? 'red' : point.importance === 'CRITICAL' ? 'orange' : 'blue'">{{ importanceLabel(point.importance) }}</a-tag>
        <a-input v-model:value="point.itemName" :disabled="readonly" placeholder="控制项目，如中心温度" @update:value="publish" />
        <a-button v-if="!readonly" type="text" danger @click="removePoint(index)">删除</a-button>
      </div>
      <div class="grid three">
        <a-select v-model:value="point.controlType" :disabled="readonly" :options="controlTypes" @update:value="publish" />
        <a-input-number v-model:value="point.targetValue" :disabled="readonly" placeholder="目标" @update:value="publish" />
        <a-input v-model:value="point.unit" :disabled="readonly" placeholder="单位" @update:value="publish" />
      </div>
      <div class="grid four">
        <a-input-number v-model:value="point.lowerLimit" :disabled="readonly" placeholder="下限" @update:value="publish" />
        <a-input-number v-model:value="point.upperLimit" :disabled="readonly" placeholder="上限" @update:value="publish" />
        <a-input v-model:value="point.method" :disabled="readonly" placeholder="检测方法" @update:value="publish" />
        <a-input v-model:value="point.measurementTool" :disabled="readonly" placeholder="工具" @update:value="publish" />
      </div>
      <div class="grid two">
        <a-input v-model:value="point.frequency" :disabled="readonly" placeholder="检测频次" @update:value="publish" />
        <a-input v-model:value="point.deviationAction" :disabled="readonly" placeholder="偏差处理要求" @update:value="publish" />
      </div>
      <div class="measurements">
        <div class="measure-title"><b>实测记录</b><a-button v-if="!readonly" size="small" type="link" @click="addMeasurement(index)">＋ 添加实测</a-button></div>
        <div v-for="(measurement, measurementIndex) in point.measurements" :key="measurement.key" class="measurement-row">
          <a-input-number v-model:value="measurement.measuredValue" :disabled="readonly" placeholder="实测值" @update:value="publish" />
          <a-date-picker v-model:value="measurement.measuredAt" :disabled="readonly" value-format="YYYY-MM-DD HH:mm:ss" show-time placeholder="测量时间" @update:value="publish" />
          <a-select v-model:value="measurement.result" :disabled="readonly" :options="results" @update:value="publish" />
          <a-input v-model:value="measurement.deviationAction" :disabled="readonly" placeholder="处理/复测说明" @update:value="publish" />
          <a-select v-model:value="measurement.retestResult" :disabled="readonly" :options="results" @update:value="publish" />
          <a-button v-if="!readonly" type="text" danger @click="removeMeasurement(index, measurementIndex)">删除</a-button>
        </div>
      </div>
      <div class="grid two">
        <div class="confirmation">
          <span v-if="point.confirmedBy">已由 {{ displayConfirmedBy(point.confirmedBy) }}确认<span v-if="point.confirmedAt"> · {{ point.confirmedAt }}</span></span>
          <span v-else>尚未确认；确认身份与时间由系统记录</span>
          <a-button v-if="!readonly && point.importance === 'CRITICAL' && !hasDeviation(point) && !point.confirmedBy" size="small" :disabled="!point.measurements.length || point.measurements.some(item => item.measuredValue == null || item.result !== 'PASS')" title="全部实测记录判定合格后可确认" @click="requestConfirmation(index)">本人确认</a-button>
          <a-button v-if="!readonly && point.importance === 'CRITICAL' && hasDeviation(point) && !point.resolved" size="small" danger @click="requestDeviationConfirmation(point)">负责人确认偏差</a-button>
        </div>
        <a-input v-model:value="point.basisOrRemark" :disabled="readonly" placeholder="依据或备注" @update:value="publish" />
      </div>
      <span v-if="isBlocking(point)" class="block-note">{{ missingFields(point) }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { nextProcessKey, type ControlMeasurementDraft, type ControlPointDraft } from "@rnd/shared";
import { cloneVueValue } from "./cloneVueValue";

const props = withDefaults(defineProps<{ modelValue: ControlPointDraft[]; readonly?: boolean; confirmationMode?: "legacy-sentinel" | "external" }>(), {
  confirmationMode: "legacy-sentinel",
});
const emit = defineEmits<{ "update:modelValue": [value: ControlPointDraft[]]; "confirm-pass": [point: ControlPointDraft]; "confirm-deviation": [point: ControlPointDraft] }>();
const clonePoints = (value: ControlPointDraft[]) => cloneVueValue(value);
const points = ref<ControlPointDraft[]>(clonePoints(props.modelValue || []));
watch(() => props.modelValue, value => { points.value = clonePoints(value || []); });

const controlTypes = [{ label: "食品安全关键点", value: "FOOD_SAFETY" }, { label: "研发品质关键点", value: "QUALITY" }, { label: "普通工艺参数", value: "PROCESS" }];
const importanceOptions = [{ label: "极重要", value: "CRITICAL" }, { label: "重要", value: "IMPORTANT" }, { label: "一般", value: "NORMAL" }];
const results = [{ label: "待判定", value: "PENDING" }, { label: "合格", value: "PASS" }, { label: "不合格", value: "FAIL" }];

function publish() { emit("update:modelValue", clonePoints(points.value)); }
function addPoint() { const key = nextProcessKey("control"); points.value.push({ id: key, key, sequence: points.value.length + 1, controlType: "PROCESS", importance: "NORMAL", itemName: "", resolved: false, measurements: [] }); publish(); }
function removePoint(index: number) { points.value.splice(index, 1); publish(); }
function addMeasurement(pointIndex: number) { const point = points.value[pointIndex]; if (!point) return; const key = nextProcessKey("measurement"); point.measurements.push({ id: key, key, sequence: point.measurements.length + 1, result: "PENDING", retestResult: "PENDING" } as ControlMeasurementDraft); publish(); }
function removeMeasurement(pointIndex: number, measurementIndex: number) { points.value[pointIndex]?.measurements.splice(measurementIndex, 1); publish(); }
function outside(point: ControlPointDraft, value?: number) { return value != null && ((point.lowerLimit != null && value < point.lowerLimit) || (point.upperLimit != null && value > point.upperLimit)); }
function hasDeviation(point: ControlPointDraft) { return point.measurements.some(item => item.result === "FAIL" || outside(point, item.measuredValue)); }
function unresolvedDeviation(point: ControlPointDraft) { return point.measurements.some(item => (item.result === "FAIL" || outside(point, item.measuredValue)) && (!item.deviationAction?.trim() || !item.retestResult || item.retestResult === "PENDING" || item.retestResult === "FAIL")); }
function isBlocking(point: ControlPointDraft) { return point.importance === "CRITICAL" && (!point.measurements.some(item => item.measuredValue != null) || !point.confirmedBy?.trim() || (hasDeviation(point) && (!point.resolved || unresolvedDeviation(point)))); }
function missingFields(point: ControlPointDraft) { const missing = []; if (!point.measurements.some(item => item.measuredValue != null)) missing.push("实测值"); if (!point.confirmedBy?.trim()) missing.push("确认人"); if (hasDeviation(point) && !point.resolved) missing.push("偏差闭环"); if (unresolvedDeviation(point)) missing.push("偏差处理/复测"); return `需补充：${missing.join("、")}`; }
function requestConfirmation(index: number) {
  const point = points.value[index];
  if (!point) return;
  if (props.confirmationMode === "external") emit("confirm-pass", clonePoints([point])[0]!);
  else { point.confirmedBy = "__SESSION_CONFIRMATION_REQUESTED__"; publish(); }
}
function requestDeviationConfirmation(point: ControlPointDraft) { emit("confirm-deviation", clonePoints([point])[0]!); }
function displayConfirmedBy(value: string) { return value === "__SESSION_CONFIRMATION_REQUESTED__" ? "当前登录人（待保存）" : value; }
function importanceLabel(value: ControlPointDraft["importance"]) { return value === "CRITICAL" ? "极重要" : value === "IMPORTANT" ? "重要" : "一般"; }
</script>

<style scoped>
.control-points { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 10px; } .control-points header, .measure-title, .point-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; } .control-points header { justify-content: space-between; } .control-points small { display: block; color: #86909c; font-size: 12px; }
.control-card { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 8px; padding: 12px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; } .control-card.blocking { border-color: #ff7875; background: #fff8f7; } .point-heading .ant-input { flex: 1 1 150px; min-width: 0; }
.grid { display: grid; gap: 8px; min-width: 0; } .two { grid-template-columns: repeat(2, minmax(0, 1fr)); } .three { grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, .6fr); } .four { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.grid > *, .measurement-row > * { min-width: 0; width: 100%; }
.measurements { min-width: 0; padding: 8px; border-radius: 8px; background: #f7f8fa; } .measure-title { justify-content: space-between; margin-bottom: 6px; } .measurement-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) minmax(0, .8fr) minmax(0, 1.3fr) minmax(0, .8fr) 44px; gap: 6px; margin-top: 6px; } .block-note { color: #cf1322; font-size: 12px; }
.confirmation { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-height: 32px; color: #595959; font-size: 12px; overflow-wrap: anywhere; }
@media (max-width: 1100px) { .three, .four, .measurement-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
