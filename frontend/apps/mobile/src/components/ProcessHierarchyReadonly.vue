<template>
  <div class="mobile-process-plan" aria-label="手机端只读工艺">
    <div class="plan-summary">
      <span>大工序<strong>{{ plan.majorProcesses.length }}</strong></span>
      <span>小步骤<strong>{{ stepCount }}</strong></span>
      <span>成品得率<strong>{{ percent(batchYield) }}</strong></span>
    </div>
    <van-collapse v-model="opened">
      <van-collapse-item v-for="(major,index) in plan.majorProcesses" :key="major.key" :name="major.key">
        <template #title>
          <div class="major-heading">
            <span>{{ String(index+1).padStart(2,'0') }}</span>
            <div><b>{{ major.processName }}</b><small>{{ major.steps.length }}个小步骤 · 大工序得率 {{ percent(yieldOf(major).mainYieldPercent) }}</small></div>
          </div>
        </template>
        <div class="yield-row">
          <span>主料 {{ kg(yieldOf(major).primaryInputWeightKg) }}</span>
          <span>合格产出 {{ kg(yieldOf(major).qualifiedOutputWeightKg) }}</span>
          <span :class="{warning:Math.abs(yieldOf(major).balanceDifferenceKg)>.01}">平衡差 {{ kg(yieldOf(major).balanceDifferenceKg) }}</span>
        </div>
        <div v-for="(step,stepIndex) in major.steps" :key="step.key" class="minor-step">
          <div class="step-title"><b>{{ stepIndex+1 }}. {{ step.stepName }}</b><span>{{ parameters(step) }}</span></div>
          <p v-if="step.equipment || step.instruction">{{ [step.equipment,step.instruction].filter(Boolean).join(' · ') }}</p>
          <div v-if="step.materials.length" class="materials">
            <span v-for="material in step.materials" :key="material.key">
              {{ roleLabel(material.materialRole) }} · {{ material.materialName }} {{ kg(material.weightKg||0) }}
              <template v-if="material.sourceType==='STEP_OUTPUT'"> · 承接 {{ material.sourceStepOutputId || '前序中间产物' }}</template>
            </span>
          </div>
          <div v-if="step.outputs?.length" class="detail-block flow-block">
            <b>中间流转</b>
            <span v-for="output in step.outputs" :key="output.key">{{ output.outputName }} · {{ kg(output.weightKg||0) }} · {{ output.continueFlow ? '继续流转' : '本步结束' }}</span>
          </div>
          <div v-if="step.controlPoints?.length" class="detail-block control-block">
            <b>关键控制点</b>
            <span v-for="control in step.controlPoints" :key="control.key">{{ importanceLabel(control.importance) }} · {{ control.itemName }} · {{ controlRange(control) }} · {{ control.resolved ? '已完成' : '待处理' }}</span>
          </div>
        </div>
        <van-empty v-if="!major.steps.length" image="search" description="历史大工序暂无小步骤明细" />
      </van-collapse-item>
    </van-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { calculateBatchYield, calculateMajorProcessYield, type ControlPointDraft, type MajorProcessDraft, type MinorProcessStepDraft, type ProcessMaterialRole, type ProcessPlanDraft } from "@rnd/shared";

const props=defineProps<{plan:ProcessPlanDraft}>();
const opened=ref<string[]>(props.plan.majorProcesses[0]?.key?[props.plan.majorProcesses[0].key]:[]);
const stepCount=computed(()=>props.plan.majorProcesses.reduce((sum,item)=>sum+item.steps.length,0));
const batchYield=computed(()=>calculateBatchYield(props.plan));

function yieldOf(major:MajorProcessDraft){return calculateMajorProcessYield(major)}
function kg(value:number){return `${value.toFixed(3)}kg`}
function percent(value:number|null){return value==null?"—":`${value.toFixed(2)}%`}
function parameters(step:MinorProcessStepDraft){const a=[step.parameter1Value,step.parameter1Unit].filter(Boolean).join("");const b=[step.parameter2Value,step.parameter2Unit].filter(Boolean).join("");return [a,b].filter(Boolean).join(" · ")||"未设置关键参数"}
function roleLabel(role:ProcessMaterialRole){return role==="PRIMARY"?"主料":role==="PROCESS_WATER"?"加工用水":"辅料"}
function importanceLabel(value:ControlPointDraft["importance"]){return value==="CRITICAL"?"极重要":value==="IMPORTANT"?"重要":"一般"}
function controlRange(control:ControlPointDraft){const range=control.lowerLimit!=null||control.upperLimit!=null?`${control.lowerLimit??"—"}~${control.upperLimit??"—"}${control.unit||""}`:"";const target=control.targetValue!=null?`目标 ${control.targetValue}${control.unit||""}`:"";return range||target||"未设定限值"}
</script>

<style scoped>
.plan-summary{display:grid;grid-template-columns:repeat(3,1fr);margin:0 12px 12px;border:1px solid #e7ebf2;border-radius:8px;overflow:hidden}.plan-summary span{display:flex;flex-direction:column;gap:3px;padding:10px;background:#f8faff;color:#64748b;font-size:11px}.plan-summary strong{color:#172033;font-size:15px}.major-heading{display:flex;align-items:center;gap:9px}.major-heading>span{width:28px;height:28px;display:grid;place-items:center;border-radius:7px;background:#eaf2ff;color:#246bfe;font-size:11px}.major-heading b,.major-heading small{display:block}.major-heading small{margin-top:2px;color:#64748b;font-size:11px}.yield-row{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px;color:#64748b;font-size:11px}.warning{color:#d84a4a}.minor-step{margin-bottom:8px;padding:10px;border:1px solid #e7ebf2;border-radius:8px;background:#fbfcfe}.step-title{display:flex;justify-content:space-between;gap:10px}.step-title span,.minor-step p{color:#64748b;font-size:11px}.minor-step p{margin:6px 0 0}.materials{display:flex;gap:5px;flex-wrap:wrap;margin-top:7px}.materials span{padding:4px 6px;border-radius:5px;background:#eef4ff;color:#315a9d;font-size:10px}.detail-block{display:flex;flex-direction:column;gap:5px;margin-top:8px;padding:8px;border-radius:7px;background:#f5f7fa;color:#526071;font-size:10px}.detail-block b{color:#172033;font-size:11px}.control-block{background:#fff8ed}.detail-block span{line-height:1.45}
</style>
