<template>
 <div class="sop-snapshot">
  <article v-for="major in plan.majorProcesses" :key="major.key||major.id||major.sequence">
   <h2>{{ major.sequence }}. {{ major.processName }}</h2><p v-if="major.description">{{ major.description }}</p>
   <section v-for="step in major.steps" :key="step.key||step.id||step.sequence">
    <h3>{{ major.sequence }}.{{ step.sequence }} {{ step.stepName }}</h3>
    <p v-if="step.equipment"><b>设备：</b>{{ step.equipment }}</p>
    <p class="instruction"><b>操作要求：</b>{{ step.instruction || '待补充' }}</p>
    <div v-if="parameters(step).length" class="parameters"><div v-for="(p,index) in parameters(step)" :key="p.key||index"><b>{{ p.name }}：</b>{{ p.actualValue || '待补充' }} {{ p.unit }}<span v-if="p.measurementBasis">（{{ basis[p.measurementBasis]||p.measurementBasis }}）</span><span v-if="p.lowerLimit||p.upperLimit"> · 范围 {{ p.lowerLimit||'无下限' }}～{{ p.upperLimit||'无上限' }}</span><span v-if="p.remark"> · {{ p.remark }}</span></div></div>
    <div v-if="step.materials?.length" class="materials"><h4>本步投料与流转</h4><div v-for="(m,index) in step.materials" :key="m.key||index"><strong>{{ m.materialName }}</strong> · {{ role[m.materialRole]||m.materialRole }} · {{ m.weightKg??'待补充' }} kg <a-tag>{{ materialSource(m) }}</a-tag><span v-if="m.additionTiming">｜{{ m.additionTiming }}</span><span v-if="m.additionMethod"> · {{ m.additionMethod }}</span></div></div>
    <div v-if="step.outputs?.length"><h4>产出</h4><p v-for="(o,index) in step.outputs" :key="o.key||index">{{ o.outputName }} · {{ o.weightKg??'待补充' }} kg<span v-if="o.primaryOutput"> · 主料产出</span><span v-if="o.continueFlow"> · 继续流转</span></p></div>
    <div v-if="step.controlPoints?.length" class="controls"><h4>关键控制要求</h4><div v-for="(c,index) in step.controlPoints" :key="c.key||index"><a-tag :color="c.importance==='CRITICAL'?'red':'orange'">{{ importance[c.importance]||c.importance }}</a-tag><b>{{ c.itemName }}</b> · 目标 {{ c.targetValue??'待补充' }} {{ c.unit }}<span v-if="c.lowerLimit!=null||c.upperLimit!=null"> · 范围 {{ c.lowerLimit??'无下限' }}～{{ c.upperLimit??'无上限' }}</span><p>检测：{{ [c.method,c.measurementTool,c.frequency].filter(Boolean).join(' · ')||'待补充' }}</p><p v-if="c.deviationAction">偏差处理：{{ c.deviationAction }}</p><p v-if="c.basisOrRemark">依据 / 备注：{{ c.basisOrRemark }}</p></div></div>
   </section>
  </article>
 </div>
</template>
<script setup lang="ts">
import {effectiveStepParameters,type ProcessPlanDraft,type ProcessStepMaterialDraft} from '@rnd/shared';
const props=defineProps<{plan:ProcessPlanDraft}>();
const parameters=effectiveStepParameters;
const role:Record<string,string>={PRIMARY:'主料',AUXILIARY:'辅料',PROCESS_WATER:'工艺用水'};
const basis:Record<string,string>={GAUGE:'表压',ABSOLUTE:'绝对压力',DEVICE_PERCENT:'设备显示百分比'};
const importance:Record<string,string>={CRITICAL:'极重要',IMPORTANT:'重要',NORMAL:'一般'};
function materialSource(material:ProcessStepMaterialDraft){
 if(material.sourceType!=='STEP_OUTPUT')return '外部新投料';
 if(!material.sourceStepOutputId)return '中间产物 · 来源待核对';
 for(const major of props.plan.majorProcesses)for(const step of major.steps){
  if(step.outputs?.some(output=>output.id===material.sourceStepOutputId))return `中间产物 · 来自 ${major.sequence}.${step.sequence} ${step.stepName}`;
 }
 return '中间产物 · 来源待核对';
}
</script>
<style scoped>
.sop-snapshot{max-height:65vh;overflow:auto;line-height:1.8;color:#344054}article{margin-bottom:24px}h2{font-size:18px;background:#eef4ff;padding:10px 14px;border-radius:6px}h3{font-size:16px;margin:0 0 10px}section{margin:12px 0;padding:16px;border:1px solid #e5e6eb;border-radius:8px}h4{margin:12px 0 4px;font-size:14px}p{margin:4px 0;white-space:pre-wrap;overflow-wrap:anywhere}.parameters{background:#f7f8fa;padding:10px;margin-top:10px}.controls{border-left:3px solid #faad14;padding-left:12px}.controls>div{margin:8px 0}
</style>
