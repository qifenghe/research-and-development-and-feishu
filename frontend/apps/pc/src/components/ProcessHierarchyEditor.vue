<template>
  <div class="process-hierarchy-editor">
    <div class="process-toolbar">
      <div><strong>分层工艺编排</strong><span>先大后小，模板可重复拖拽</span></div>
      <a-space>
        <a-statistic title="大工序" :value="plan.majorProcesses.length" />
        <a-statistic title="小步骤" :value="stepCount" />
        <a-statistic title="整批得率" :value="batchYield ?? 0" suffix="%" :precision="2" />
      </a-space>
    </div>

    <div class="process-layout">
      <aside class="process-library">
        <h3>工艺模板库</h3>
        <a-segmented v-model:value="libraryMode" :options="['大工序', '小步骤']" block />
        <a-input v-model:value="keyword" allow-clear placeholder="搜索模板" class="library-search" />
        <div v-if="libraryMode === '大工序'" class="template-list">
          <button v-for="item in filteredMajorTemplates" :key="item.name" draggable="true" class="template-card"
            @dragstart="startTemplateDrag($event, 'major', item.name)" @dblclick="addMajor(item.name)">
            <b>{{ item.name }}</b><small>{{ item.description }}</small><span>＋</span>
          </button>
          <a-button block type="dashed" :disabled="readonly" @click="openCreateMajor">＋ 新建大工序</a-button>
        </div>
        <div v-else class="template-list">
          <button v-for="name in filteredStepTemplates" :key="name" draggable="true" class="template-card compact"
            @dragstart="startTemplateDrag($event, 'step', name)" @dblclick="addStep(name)">
            <b>{{ name }}</b><span>＋</span>
          </button>
          <a-button block type="dashed" :disabled="readonly || selectedMajorIndex < 0" @click="openCreateStep">＋ 新建小步骤</a-button>
        </div>
      </aside>

      <section class="process-canvas">
        <div v-if="!plan.majorProcesses.length" class="empty-route" @dragover.prevent @drop="dropAtEnd">
          <b>还没有大工序</b><span>从左侧拖入模板，或点击新建大工序</span>
        </div>
        <article v-for="(major, majorIndex) in plan.majorProcesses" :key="major.key"
          class="major-card" :class="{ selected: selectedMajorIndex === majorIndex }" draggable="true"
          @dragstart="startMajorDrag($event, majorIndex)" @dragover.prevent @drop="dropOnMajor($event, majorIndex)">
          <header @click="selectedMajorIndex = majorIndex">
            <span class="drag-handle">⠿</span><span class="sequence">{{ String(majorIndex + 1).padStart(2, '0') }}</span>
            <div class="major-title"><b>{{ major.processName || '未命名大工序' }}</b><small>{{ major.description || '点击编辑工序说明' }} · {{ major.steps.length }} 个小步骤</small></div>
            <div class="yield-summary"><small>主料 / 合格产出</small><b>{{ kg(yieldOf(major).primaryInputWeightKg) }} / {{ kg(yieldOf(major).qualifiedOutputWeightKg) }}</b></div>
            <div class="yield-summary"><small>工序得率</small><b :class="yieldTone(major)">{{ percent(yieldOf(major).mainYieldPercent) }}</b></div>
            <a-space v-if="!readonly" @click.stop>
              <a-button size="small" @click="editMajor(majorIndex)">编辑</a-button>
              <a-button size="small" @click="selectedMajorIndex=majorIndex;openCreateStep()">添加步骤</a-button>
              <a-button size="small" type="primary" ghost @click="editYield(majorIndex)">投入产出</a-button>
            </a-space>
          </header>
          <div class="minor-route" @dragover.prevent @drop="dropStepAtEnd($event, majorIndex)">
            <template v-for="(step, stepIndex) in major.steps" :key="step.key">
              <span v-if="stepIndex" class="route-arrow">→</span>
              <button class="minor-card" draggable="true" @dragstart.stop="startStepDrag($event, majorIndex, stepIndex)"
                @dragover.prevent @drop.stop="dropStepBefore($event, majorIndex, stepIndex)" @click="editStep(majorIndex, stepIndex)">
                <b>{{ step.stepName }}</b>
                <small>{{ stepParameter(step) }}<template v-if="step.materials.length"> · {{ step.materials.length }}项料</template></small>
              </button>
            </template>
            <span v-if="!major.steps.length" class="minor-empty">将小步骤拖入这里</span>
          </div>
          <footer>
            <span>全部投入 <b>{{ kg(yieldOf(major).totalInputWeightKg) }}</b></span>
            <span>综合回收率 <b>{{ percent(yieldOf(major).recoveryPercent) }}</b></span>
            <span :class="{ warning: Math.abs(yieldOf(major).balanceDifferenceKg) > .01 }">平衡差 <b>{{ kg(yieldOf(major).balanceDifferenceKg) }}</b></span>
          </footer>
        </article>
        <button v-if="!readonly" class="add-major-zone" @click="openCreateMajor" @dragover.prevent @drop="dropAtEnd">＋ 添加或拖入大工序</button>
      </section>
    </div>

    <a-drawer v-model:open="drawerOpen" :title="drawerTitle" width="620" destroy-on-close>
      <template v-if="drawerMode === 'major' && editingMajor">
        <a-form layout="vertical">
          <a-form-item label="大工序名称"><a-input v-model:value="editingMajor.processName" /></a-form-item>
          <a-form-item label="工序说明"><a-textarea v-model:value="editingMajor.description" :rows="2" /></a-form-item>
          <a-form-item label="标准工序代码"><a-input v-model:value="editingMajor.processCode" placeholder="如 HEAT_PROCESS" /></a-form-item>
          <a-form-item label="得率口径"><a-select v-model:value="editingMajor.yieldBasis" :options="yieldBasisOptions" /></a-form-item>
          <a-form-item label="差异说明/备注"><a-textarea v-model:value="editingMajor.remark" :rows="2" /></a-form-item>
        </a-form>
      </template>
      <template v-else-if="drawerMode === 'step' && editingStep">
        <a-form layout="vertical">
          <a-row :gutter="12"><a-col :span="12"><a-form-item label="小步骤名称"><a-input v-model:value="editingStep.stepName" /></a-form-item></a-col><a-col :span="12"><a-form-item label="步骤类型"><a-select v-model:value="editingStep.stepType" :options="stepTypeOptions" /></a-form-item></a-col></a-row>
          <a-row :gutter="12"><a-col :span="8"><a-form-item label="关键参数1"><a-input v-model:value="editingStep.parameter1Name" placeholder="温度" /></a-form-item></a-col><a-col :span="8"><a-form-item label="参数值"><a-input v-model:value="editingStep.parameter1Value" placeholder="95" /></a-form-item></a-col><a-col :span="8"><a-form-item label="单位"><a-input v-model:value="editingStep.parameter1Unit" placeholder="℃" /></a-form-item></a-col></a-row>
          <a-row :gutter="12"><a-col :span="8"><a-form-item label="关键参数2"><a-input v-model:value="editingStep.parameter2Name" placeholder="时间" /></a-form-item></a-col><a-col :span="8"><a-form-item label="参数值"><a-input v-model:value="editingStep.parameter2Value" placeholder="40" /></a-form-item></a-col><a-col :span="8"><a-form-item label="单位"><a-input v-model:value="editingStep.parameter2Unit" placeholder="min" /></a-form-item></a-col></a-row>
          <a-form-item label="设备"><a-input v-model:value="editingStep.equipment" /></a-form-item>
          <a-form-item label="操作说明"><a-textarea v-model:value="editingStep.instruction" :rows="2" /></a-form-item>
        </a-form>
        <div class="drawer-section"><b>本步骤加入的物料</b><a-button type="link" @click="addStepMaterial">＋ 添加主料/辅料</a-button></div>
        <div v-for="(material, index) in editingStep.materials" :key="material.key" class="row-grid material-row">
          <a-select v-model:value="material.materialRole" :options="materialRoleOptions" />
          <a-input v-model:value="material.materialCode" placeholder="编码" />
          <a-input v-model:value="material.materialName" placeholder="物料名称" />
          <a-input-number v-model:value="material.weightKg" :min="0" addon-after="kg" />
          <a-button danger type="text" @click="editingStep.materials.splice(index,1)">删除</a-button>
        </div>
      </template>
      <template v-else-if="drawerMode === 'yield' && editingMajor">
        <div class="yield-cards"><div><small>主料得率</small><b>{{ percent(yieldOf(editingMajor).mainYieldPercent) }}</b></div><div><small>综合回收率</small><b>{{ percent(yieldOf(editingMajor).recoveryPercent) }}</b></div><div><small>平衡差</small><b>{{ kg(yieldOf(editingMajor).balanceDifferenceKg) }}</b></div></div>
        <div class="drawer-section"><b>投入物料</b><a-button type="link" @click="addInput">＋ 添加投入</a-button></div>
        <div v-for="(input,index) in editingMajor.inputs" :key="input.key" class="row-grid input-row">
          <a-select v-model:value="input.inputRole" :options="materialRoleOptions" /><a-input v-model:value="input.materialCode" placeholder="编码" /><a-input v-model:value="input.materialName" placeholder="物料名称" /><a-input-number v-model:value="input.weightKg" :min="0" addon-after="kg" /><a-button danger type="text" @click="editingMajor.inputs.splice(index,1)">删除</a-button>
        </div>
        <div class="drawer-section"><b>分类产出</b><a-button type="link" @click="addOutput">＋ 添加产出</a-button></div>
        <div v-for="(output,index) in editingMajor.outputs" :key="output.key" class="row-grid output-row">
          <a-select v-model:value="output.outputType" :options="outputTypeOptions" /><a-input-number v-model:value="output.weightKg" :min="0" addon-after="kg" /><a-input v-model:value="output.remark" placeholder="备注" /><a-button danger type="text" @click="editingMajor.outputs.splice(index,1)">删除</a-button>
        </div>
      </template>
      <template #footer><div class="drawer-footer"><a-button v-if="!creating && !readonly" danger @click="removeEditing">删除</a-button><span /><a-button @click="drawerOpen=false">取消</a-button><a-button v-if="!readonly" type="primary" @click="saveDrawer">保存</a-button></div></template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { calculateMajorProcessYield, nextProcessKey, normalizeProcessPlan, type MajorProcessDraft, type MinorProcessStepDraft, type ProcessPlanDraft } from "@rnd/shared";

const props = defineProps<{ modelValue: ProcessPlanDraft; readonly?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ProcessPlanDraft] }>();
const plan = computed(() => props.modelValue);
const libraryMode = ref("大工序"); const keyword = ref(""); const selectedMajorIndex = ref(0);
const drawerOpen = ref(false); const drawerMode = ref<"major"|"step"|"yield">("major"); const creating = ref(false);
const editingMajorIndex = ref(-1); const editingStepIndex = ref(-1); const editingMajor = ref<MajorProcessDraft|null>(null); const editingStep = ref<MinorProcessStepDraft|null>(null);
const dragPayload = ref<{kind:string;majorIndex?:number;stepIndex?:number;name?:string}|null>(null);
const majorTemplates=[{name:"原辅料准备",description:"验收、领料和配料"},{name:"解冻与净制",description:"解冻、清洗和修整"},{name:"分切与规格化",description:"改刀及规格控制"},{name:"调味与腌制",description:"配料、滚揉和静置"},{name:"热加工",description:"焯、煎、炒、炸、煮、焖等"},{name:"冷却与冻结",description:"预冷、速冻和冷冻"},{name:"分装与包装",description:"定量、灌装和封口"},{name:"检测与入库",description:"金检、放行和储存"}];
const stepTemplates=["验收","解冻","清洗","修割","改刀","切块","称量","配料","搅拌","滚揉","腌制","焯水","煎制","炒制","油炸","煮制","卤制","焖制","蒸制","冷却","速冻","包装","封口","金检","称重","研发取样"];
const filteredMajorTemplates=computed(()=>majorTemplates.filter(x=>x.name.includes(keyword.value))); const filteredStepTemplates=computed(()=>stepTemplates.filter(x=>x.includes(keyword.value)));
const stepCount=computed(()=>plan.value.majorProcesses.reduce((n,p)=>n+p.steps.length,0));
const batchYield=computed(()=>{const first=plan.value.majorProcesses[0],last=plan.value.majorProcesses.at(-1);if(!first||!last)return null;const a=yieldOf(first).primaryInputWeightKg,b=yieldOf(last).qualifiedOutputWeightKg;return a>0?b/a*100:null});
const drawerTitle=computed(()=>drawerMode.value==="yield"?`投入产出 · ${editingMajor.value?.processName||""}`:drawerMode.value==="step"?(creating.value?"添加小步骤":"编辑小步骤"):(creating.value?"新建大工序":"编辑大工序"));
const materialRoleOptions=[{label:"主料",value:"PRIMARY"},{label:"辅料",value:"AUXILIARY"},{label:"加工用水",value:"PROCESS_WATER"}];
const outputTypeOptions=[{label:"合格产出",value:"QUALIFIED"},{label:"可用余料",value:"REUSABLE"},{label:"尾料",value:"TAILING"},{label:"研发取样",value:"SAMPLE"},{label:"废弃物",value:"WASTE"},{label:"留存待处理",value:"HOLD"}];
const stepTypeOptions=[{label:"普通操作",value:"NORMAL"},{label:"称重点",value:"WEIGH"},{label:"物料变化",value:"MATERIAL_CHANGE"},{label:"研发取样",value:"SAMPLE"},{label:"废弃节点",value:"WASTE"}];
const yieldBasisOptions=[{label:"合格产出 ÷ 主料投入",value:"PRIMARY_INPUT"},{label:"综合产出 ÷ 全部投入",value:"TOTAL_INPUT"},{label:"不计算得率",value:"NONE"}];
function newMajor(name=""):MajorProcessDraft{return{key:nextProcessKey("major"),sequence:plan.value.majorProcesses.length+1,processCode:"",processName:name,description:majorTemplates.find(x=>x.name===name)?.description||"",yieldBasis:"PRIMARY_INPUT",remark:"",steps:[],inputs:[],outputs:[]}}
function newStep(name=""):MinorProcessStepDraft{return{key:nextProcessKey("step"),sequence:1,stepCode:"",stepName:name,stepType:name==="称重"?"WEIGH":"NORMAL",parameter1Name:"",parameter1Value:"",parameter1Unit:"",parameter2Name:"",parameter2Value:"",parameter2Unit:"",equipment:"",instruction:"",materials:[]}}
function clonePlan(){return normalizeProcessPlan(structuredClone(plan.value))} function commit(next:ProcessPlanDraft){emit("update:modelValue",normalizeProcessPlan(next))}
function addMajor(name:string){if(props.readonly)return;const next=clonePlan();next.majorProcesses.push(newMajor(name));selectedMajorIndex.value=next.majorProcesses.length-1;commit(next)}
function addStep(name:string){if(props.readonly||selectedMajorIndex.value<0)return;const next=clonePlan();const major=next.majorProcesses[selectedMajorIndex.value];if(!major)return;major.steps.push({...newStep(name),sequence:major.steps.length+1});commit(next)}
function openCreateMajor(){creating.value=true;drawerMode.value="major";editingMajorIndex.value=-1;editingMajor.value=newMajor();drawerOpen.value=true}
function openCreateStep(){if(selectedMajorIndex.value<0)return;creating.value=true;drawerMode.value="step";editingMajorIndex.value=selectedMajorIndex.value;editingStepIndex.value=-1;editingStep.value=newStep();drawerOpen.value=true}
function editMajor(index:number){creating.value=false;drawerMode.value="major";editingMajorIndex.value=index;editingMajor.value=structuredClone(plan.value.majorProcesses[index]!);drawerOpen.value=true}
function editStep(mi:number,si:number){creating.value=false;drawerMode.value="step";editingMajorIndex.value=mi;editingStepIndex.value=si;editingStep.value=structuredClone(plan.value.majorProcesses[mi]!.steps[si]!);drawerOpen.value=true}
function editYield(index:number){creating.value=false;drawerMode.value="yield";editingMajorIndex.value=index;editingMajor.value=structuredClone(plan.value.majorProcesses[index]!);drawerOpen.value=true}
function saveDrawer(){const next=clonePlan();if(drawerMode.value==="step"&&editingStep.value){const major=next.majorProcesses[editingMajorIndex.value];if(!major||!editingStep.value.stepName.trim())return;if(creating.value)major.steps.push(editingStep.value);else major.steps.splice(editingStepIndex.value,1,editingStep.value)}else if(editingMajor.value){if(!editingMajor.value.processName.trim())return;if(creating.value)next.majorProcesses.push(editingMajor.value);else next.majorProcesses.splice(editingMajorIndex.value,1,editingMajor.value)}commit(next);drawerOpen.value=false}
function removeEditing(){const next=clonePlan();if(drawerMode.value==="step")next.majorProcesses[editingMajorIndex.value]?.steps.splice(editingStepIndex.value,1);else next.majorProcesses.splice(editingMajorIndex.value,1);commit(next);drawerOpen.value=false}
function addStepMaterial(){editingStep.value?.materials.push({key:nextProcessKey("material"),sequence:(editingStep.value.materials.length||0)+1,materialRole:"AUXILIARY",materialCode:"",materialName:"",materialState:"SOLID"})}
function addInput(){editingMajor.value?.inputs.push({key:nextProcessKey("input"),sequence:(editingMajor.value.inputs.length||0)+1,inputRole:"PRIMARY",materialCode:"",materialName:""})}
function addOutput(){editingMajor.value?.outputs.push({key:nextProcessKey("output"),sequence:(editingMajor.value.outputs.length||0)+1,outputType:"QUALIFIED",remark:""})}
function startTemplateDrag(e:DragEvent,kind:string,name:string){dragPayload.value={kind,name};e.dataTransfer?.setData("text/plain",JSON.stringify(dragPayload.value))}function startMajorDrag(e:DragEvent,majorIndex:number){dragPayload.value={kind:"major-existing",majorIndex};e.dataTransfer?.setData("text/plain",JSON.stringify(dragPayload.value))}function startStepDrag(e:DragEvent,majorIndex:number,stepIndex:number){dragPayload.value={kind:"step-existing",majorIndex,stepIndex};e.dataTransfer?.setData("text/plain",JSON.stringify(dragPayload.value))}
function payload(e:DragEvent){try{return JSON.parse(e.dataTransfer?.getData("text/plain")||"") as typeof dragPayload.value}catch{return dragPayload.value}}
function dropAtEnd(e:DragEvent){const data=payload(e);if(data?.kind==="major")addMajor(data.name||"");else if(data?.kind==="major-existing"&&data.majorIndex!=null){const next=clonePlan();const [item]=next.majorProcesses.splice(data.majorIndex,1);if(item)next.majorProcesses.push(item);commit(next)}}
function dropOnMajor(e:DragEvent,index:number){const data=payload(e);if(data?.kind==="major") {const next=clonePlan();next.majorProcesses.splice(index,0,newMajor(data.name||""));commit(next)} else if(data?.kind==="major-existing"&&data.majorIndex!=null&&data.majorIndex!==index){const next=clonePlan();const [item]=next.majorProcesses.splice(data.majorIndex,1);if(item)next.majorProcesses.splice(index,0,item);commit(next)} else if(data?.kind==="step"){selectedMajorIndex.value=index;addStep(data.name||"")}}
function dropStepAtEnd(e:DragEvent,mi:number){const data=payload(e);if(data?.kind==="step"){selectedMajorIndex.value=mi;addStep(data.name||"")}else if(data?.kind==="step-existing"&&data.majorIndex!=null&&data.stepIndex!=null){const next=clonePlan();const [item]=next.majorProcesses[data.majorIndex]?.steps.splice(data.stepIndex,1)||[];if(item)next.majorProcesses[mi]?.steps.push(item);commit(next)}}
function dropStepBefore(e:DragEvent,mi:number,si:number){const data=payload(e);if(data?.kind==="step"){const next=clonePlan();next.majorProcesses[mi]?.steps.splice(si,0,newStep(data.name||""));commit(next)}else if(data?.kind==="step-existing"&&data.majorIndex!=null&&data.stepIndex!=null){const next=clonePlan();const [item]=next.majorProcesses[data.majorIndex]?.steps.splice(data.stepIndex,1)||[];if(item)next.majorProcesses[mi]?.steps.splice(si,0,item);commit(next)}}
function yieldOf(major:MajorProcessDraft){return calculateMajorProcessYield(major)}function kg(v:number){return `${v.toFixed(3)}kg`}function percent(v:number|null){return v==null?"—":`${v.toFixed(2)}%`}function yieldTone(major:MajorProcessDraft){const v=yieldOf(major).mainYieldPercent;return v!=null&&v<75?"danger":"success"}function stepParameter(step:MinorProcessStepDraft){const a=[step.parameter1Value,step.parameter1Unit].filter(Boolean).join("");const b=[step.parameter2Value,step.parameter2Unit].filter(Boolean).join("");return [a,b].filter(Boolean).join(" · ")||"待设置参数"}
</script>

<style scoped>
.process-hierarchy-editor{border:1px solid #e5e6eb;border-radius:10px;overflow:hidden;background:#f5f6f7}.process-toolbar{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;background:#fff;border-bottom:1px solid #e5e6eb}.process-toolbar>div:first-child{display:flex;flex-direction:column}.process-toolbar span{color:#8f959e;font-size:12px}.process-toolbar :deep(.ant-statistic){min-width:78px}.process-toolbar :deep(.ant-statistic-title){font-size:11px}.process-toolbar :deep(.ant-statistic-content){font-size:16px}.process-layout{display:grid;grid-template-columns:220px minmax(0,1fr);min-height:420px}.process-library{padding:14px 12px;background:#fff;border-right:1px solid #e5e6eb}.process-library h3{margin:0 0 12px}.library-search{margin:10px 0}.template-list{display:grid;gap:6px}.template-card{position:relative;border:1px solid transparent;border-radius:7px;background:#f7f8fa;padding:9px 28px 9px 10px;text-align:left;cursor:grab}.template-card:hover{border-color:#b7cdfd;background:#eef4ff}.template-card b,.template-card small{display:block}.template-card small{color:#8f959e;font-size:10px}.template-card span{position:absolute;right:9px;top:9px;color:#3370ff}.template-card.compact{padding-block:7px}.process-canvas{padding:12px}.empty-route{min-height:180px;display:grid;place-content:center;text-align:center;border:1px dashed #b7bdc8;border-radius:9px;background:#fff}.empty-route span{color:#8f959e}.major-card{margin-bottom:10px;border:1px solid #e5e6eb;border-radius:9px;background:#fff;overflow:hidden}.major-card.selected{border-color:#8fb1ff;box-shadow:0 0 0 2px rgba(51,112,255,.08)}.major-card>header{display:grid;grid-template-columns:20px 34px minmax(180px,1fr) 115px 80px auto;align-items:center;gap:9px;padding:11px}.drag-handle{cursor:grab;color:#a8adb5}.sequence{width:30px;height:30px;display:grid;place-items:center;border-radius:7px;background:#eef4ff;color:#3370ff;font-weight:700}.major-title b,.major-title small{display:block}.major-title small,.yield-summary small{color:#8f959e;font-size:10px}.yield-summary b{display:block;font-size:12px}.success{color:#258446}.danger,.warning{color:#d84a4a}.minor-route{display:flex;align-items:center;gap:6px;flex-wrap:wrap;min-height:58px;padding:10px 12px;background:#fafbfc;border-top:1px solid #f0f1f3}.minor-card{min-width:108px;padding:8px;border:1px solid #dfe2e7;border-radius:7px;background:#fff;text-align:left;cursor:pointer}.minor-card b,.minor-card small{display:block}.minor-card small{color:#8f959e;font-size:10px}.route-arrow{color:#a8adb5}.minor-empty{margin:auto;color:#a8adb5}.major-card footer{display:flex;gap:16px;padding:8px 12px;border-top:1px solid #f0f1f3;color:#646a73;font-size:11px}.add-major-zone{width:100%;height:54px;border:1px dashed #9ab8ff;border-radius:9px;background:#fff;color:#3370ff;cursor:pointer}.drawer-section{display:flex;align-items:center;justify-content:space-between;margin:16px 0 8px}.row-grid{display:grid;gap:7px;margin-bottom:7px}.material-row,.input-row{grid-template-columns:110px 100px minmax(120px,1fr) 130px 48px}.output-row{grid-template-columns:140px 140px 1fr 48px}.yield-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.yield-cards>div{padding:12px;border:1px solid #e5e6eb;border-radius:8px}.yield-cards small,.yield-cards b{display:block}.yield-cards small{color:#8f959e}.yield-cards b{font-size:20px}.drawer-footer{display:flex;gap:8px}.drawer-footer span{flex:1}@media(max-width:1100px){.process-layout{grid-template-columns:190px 1fr}.major-card>header{grid-template-columns:18px 32px 1fr auto}.yield-summary{display:none}}@media(max-width:820px){.process-layout{display:block}.process-library{border-right:0;border-bottom:1px solid #e5e6eb}.major-card>header{grid-template-columns:18px 32px 1fr}.major-card>header>.ant-space{grid-column:3}.process-toolbar{align-items:flex-start}.process-toolbar>.ant-space{display:none}}
</style>
