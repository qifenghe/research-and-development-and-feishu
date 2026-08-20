<template>
  <section class="control-points">
    <header><div><b>关键控制点</b><small>极重要项目未闭环时将阻止正式提交</small></div><a-button v-if="!readonly" size="small" type="dashed" @click="addPoint">添加控制点</a-button></header>
    <div v-for="(point, index) in points" :key="point.key" class="control-card" :class="{ blocking: isBlocking(point) }">
      <div class="point-heading"><a-select v-model:value="point.importance" :disabled="readonly" size="small" style="width:84px" :options="importanceOptions" /><a-tag :color="isBlocking(point) ? 'red' : point.importance === 'CRITICAL' ? 'orange' : 'blue'">{{ importanceLabel(point.importance) }}</a-tag><a-input v-model:value="point.itemName" :disabled="readonly" placeholder="控制项目，如中心温度" /><a-button v-if="!readonly" type="text" danger @click="removePoint(index)">删除</a-button></div>
      <div class="grid three"><a-select v-model:value="point.controlType" :disabled="readonly" :options="controlTypes" /><a-input-number v-model:value="point.targetValue" :disabled="readonly" placeholder="目标" /><a-input v-model:value="point.unit" :disabled="readonly" placeholder="单位" /></div>
      <div class="grid four"><a-input-number v-model:value="point.lowerLimit" :disabled="readonly" placeholder="下限" /><a-input-number v-model:value="point.upperLimit" :disabled="readonly" placeholder="上限" /><a-input v-model:value="point.method" :disabled="readonly" placeholder="检测方法" /><a-input v-model:value="point.measurementTool" :disabled="readonly" placeholder="工具" /></div>
      <div class="grid two"><a-input v-model:value="point.frequency" :disabled="readonly" placeholder="检测频次" /><a-input v-model:value="point.deviationAction" :disabled="readonly" placeholder="偏差处理要求" /></div>
      <div class="measurements"><div class="measure-title"><b>实测记录</b><a-button v-if="!readonly" size="small" type="link" @click="addMeasurement(point)">＋ 添加实测</a-button></div>
        <div v-for="(measurement, measurementIndex) in point.measurements" :key="measurement.key" class="measurement-row"><a-input-number v-model:value="measurement.measuredValue" :disabled="readonly" placeholder="实测值" /><a-date-picker v-model:value="measurement.measuredAt" :disabled="readonly" value-format="YYYY-MM-DD HH:mm:ss" show-time placeholder="测量时间" /><a-select v-model:value="measurement.result" :disabled="readonly" :options="results" /><a-input v-model:value="measurement.deviationAction" :disabled="readonly" placeholder="处理/复测说明" /><a-select v-model:value="measurement.retestResult" :disabled="readonly" :options="results" /><a-button v-if="!readonly" type="text" danger @click="point.measurements.splice(measurementIndex, 1)">删除</a-button></div>
      </div>
      <div class="grid two"><a-input v-model:value="point.confirmedBy" :disabled="readonly" placeholder="确认人" /><a-input v-model:value="point.basisOrRemark" :disabled="readonly" placeholder="依据或备注" /></div>
      <a-checkbox v-model:checked="point.resolved" :disabled="readonly || !isCriticalFailed(point)">偏差已闭环</a-checkbox><span v-if="isBlocking(point)" class="block-note">需要填写实测、完成偏差闭环并由负责人确认。</span>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { nextProcessKey, type ControlPointDraft, type ControlMeasurementDraft } from "@rnd/shared";
const props = defineProps<{ modelValue: ControlPointDraft[]; readonly?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ControlPointDraft[]] }>();
const points = computed({ get: () => props.modelValue || [], set: value => emit("update:modelValue", value) });
const controlTypes=[{label:"食品安全关键点",value:"FOOD_SAFETY"},{label:"研发品质关键点",value:"QUALITY"},{label:"普通工艺参数",value:"PROCESS"}];
const importanceOptions=[{label:"极重要",value:"CRITICAL"},{label:"重要",value:"IMPORTANT"},{label:"一般",value:"NORMAL"}];
const results=[{label:"待判定",value:"PENDING"},{label:"合格",value:"PASS"},{label:"不合格",value:"FAIL"}];
function addPoint(){ points.value=[...points.value,{key:nextProcessKey("control"),sequence:points.value.length+1,controlType:"PROCESS",importance:"NORMAL",itemName:"",resolved:false,measurements:[]}]; }
function removePoint(index:number){points.value=points.value.filter((_, i)=>i!==index);}
function addMeasurement(point:ControlPointDraft){point.measurements.push({key:nextProcessKey("measurement"),sequence:point.measurements.length+1,result:"PENDING",retestResult:"PENDING"} as ControlMeasurementDraft);}
function isCriticalFailed(point:ControlPointDraft){return point.measurements.some(item=>item.result==="FAIL" && item.retestResult!=="PASS");}
function isBlocking(point:ControlPointDraft){return point.importance==="CRITICAL" && (!point.measurements.some(item=>item.measuredValue != null && item.result==="PASS") || isCriticalFailed(point) || !point.resolved && isCriticalFailed(point) || !point.confirmedBy);}
function importanceLabel(value:ControlPointDraft["importance"]){return value==="CRITICAL"?"极重要":value==="IMPORTANT"?"重要":"一般";}
</script>
<style scoped>
.control-points{display:grid;gap:10px}.control-points header,.measure-title,.point-heading{display:flex;align-items:center;gap:8px}.control-points header{justify-content:space-between}.control-points small{display:block;color:#86909c;font-size:12px}.control-card{display:grid;gap:8px;padding:12px;border:1px solid #e5e6eb;border-radius:10px;background:#fff}.control-card.blocking{border-color:#ff7875;background:#fff8f7}.point-heading .ant-input{flex:1}.grid{display:grid;gap:8px}.two{grid-template-columns:repeat(2,minmax(0,1fr))}.three{grid-template-columns:1.2fr 1fr .6fr}.four{grid-template-columns:repeat(4,minmax(0,1fr))}.measurements{padding:8px;border-radius:8px;background:#f7f8fa}.measure-title{justify-content:space-between;margin-bottom:6px}.measurement-row{display:grid;grid-template-columns:1fr 1.4fr .8fr 1.3fr .8fr 44px;gap:6px;margin-top:6px}.block-note{color:#cf1322;font-size:12px}@media(max-width:900px){.four,.measurement-row{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
