<template>
  <div class="minor-workspace" @dragover.prevent @drop.prevent.stop="dropAt($event, major?.steps.length || 0)" @dragend="dragPayload = null">
    <aside class="step-rail">
      <div class="rail-heading"><b>{{ major?.processName }}</b><small>小步骤编排</small></div>
      <div v-for="(step, index) in major?.steps || []" :key="step.key" class="step-row">
        <button
          class="step-item"
          :class="{ active: step.key === selectedStepKey }"
          :draggable="!readonly"
          :aria-label="`选择步骤${step.stepName || index + 1}`"
          @click="selectedStepKey = step.key"
          @dragstart="dragExisting($event, index)"
          @dragover.prevent
          @drop.prevent.stop="dropAt($event, index)"
        >
          <span>{{ String(index + 1).padStart(2, "0") }}</span>
          <b>{{ step.stepName || "未命名步骤" }}</b>
          <small>{{ parameterSummary(step) }}</small>
        </button>
        <div v-if="!readonly" class="step-moves">
          <button :aria-label="`${step.stepName || '步骤'}上移`" :disabled="index === 0" @click="moveStep(index, -1)">上移</button>
          <button :aria-label="`${step.stepName || '步骤'}下移`" :disabled="index === (major?.steps.length || 0) - 1" @click="moveStep(index, 1)">下移</button>
        </div>
      </div>
      <a-button v-if="!readonly" type="dashed" block @click="addStep()">＋ 新建小步骤</a-button>
      <div class="step-templates">
        <small>点击添加，或拖入右侧；可重复添加</small>
        <button v-for="name in stepTemplates" :key="name" :draggable="!readonly" :disabled="readonly" @dragstart="dragTemplate($event, name)" @click="addStep(name)">{{ name }}</button>
      </div>
    </aside>

    <main v-if="selectedStep && major" class="step-editor">
      <header class="editor-header">
        <div><span class="crumb">{{ major.processName }} / 小步骤 {{ selectedIndex + 1 }}</span><h3>{{ selectedStep.stepName || "未命名步骤" }}</h3><p>{{ parameterSummary(selectedStep) }}</p></div>
        <a-space><a-button v-if="!readonly" @click="copyStep">复制</a-button><a-button v-if="!readonly" danger @click="removeStep">删除</a-button></a-space>
      </header>

      <section class="section">
        <div class="section-title"><b>基础操作参数</b><a-button type="link" size="small" @click="expanded.basic = !expanded.basic">{{ expanded.basic ? "收起" : readonly ? "查看" : "编辑" }}</a-button></div>
        <div v-if="!expanded.basic" class="summary-grid"><span>设备：{{ selectedStep.equipment || "未设置" }}</span><span>{{ parameterSummary(selectedStep) }}</span><span>要求：{{ selectedStep.instruction || "未设置" }}</span></div>
        <a-form v-else layout="vertical">
          <a-row :gutter="10">
            <a-col :span="12"><a-form-item label="步骤名称"><a-input v-model:value="selectedStep.stepName" :disabled="readonly" @update:value="publishLocal" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="步骤类型"><a-select v-model:value="selectedStep.stepType" :disabled="readonly" :options="stepTypes" @update:value="publishLocal" /></a-form-item></a-col>
          </a-row>
          <StepParameterEditor :key="selectedStep.key" :model-value="effectiveStepParameters(selectedStep)" :operation="selectedStep.stepName" :readonly="readonly" @update:model-value="replaceParameters" />
          <a-form-item label="设备"><a-input v-model:value="selectedStep.equipment" :disabled="readonly" @update:value="publishLocal" /></a-form-item>
          <a-form-item label="操作要求"><a-textarea v-model:value="selectedStep.instruction" :disabled="readonly" :rows="2" @update:value="publishLocal" /></a-form-item>
        </a-form>
      </section>

      <slot name="selected-step-extension" :major="major" :step="selectedStep" />

      <section class="section">
        <div class="section-title"><b>本步骤投料</b></div>
        <p class="hint">外部投料从物料库选择；中间产物引用前序步骤的产出，<b>不进入物料库</b>。</p>
        <div v-for="(material, index) in selectedStep.materials" :key="material.key" class="material-row" :data-material-key="material.key">
          <a-select v-model:value="material.sourceType" :disabled="readonly || material.materialRole==='PACKAGING'" :options="sourceTypes" @change="onSourceChange(material)" />
          <a-select v-if="material.sourceType === 'STEP_OUTPUT'" v-model:value="material.sourceStepOutputId" :disabled="readonly" :options="previousOutputsFor(material)" placeholder="选择前序产出" @change="applyOutputSource(material)" />
          <template v-else>
            <MaterialPicker :key="material.key" :purpose="material.materialRole==='PACKAGING'?'packaging':'ingredient'" :current-material="material" :readonly="readonly" @select="selectMaterial(material,$event)" />
            <a-button v-if="!readonly && !material.temporaryMaterialId" size="small" @click="startTemporary(material)">临时填写（不入库）</a-button>
            <TemporaryMaterialEditor v-if="material.temporaryMaterialId && !readonly" :model-value="material" @update:model-value="updateTemporary($event)" />
            <a-select v-if="!readonly && temporaryOptions.length" placeholder="复用本方案临时物料" :options="temporaryOptions" @change="reuseTemporary(material,String($event))" />
          </template>
          <a-select v-model:value="material.materialRole" :disabled="readonly" :options="materialRoles" @change="ensurePrimaryMaterial(material)" />
          <a-input v-if="material.sourceType === 'STEP_OUTPUT'" :value="material.materialName" disabled placeholder="前序产出名称" />
          <template v-if="material.materialRole==='PACKAGING'">
            <a-input-number v-model:value="material.quantity" :disabled="readonly" :min="0" placeholder="包材数量" @update:value="publishLocal" />
            <a-select v-model:value="material.quantityUnit" :disabled="readonly || !!material.catalogMaterialId" :options="packagingUnits" placeholder="数量单位" @change="publishLocal" />
          </template>
          <a-input-number v-else v-model:value="material.weightKg" :disabled="readonly" :min="0" addon-after="kg" @update:value="publishLocal" />
          <a-button v-if="!readonly" type="text" danger @click="removeMaterial(index)">删除</a-button>
          <details class="material-details"><summary>加入时机与方式{{ material.additionTiming ? ` · ${material.additionTiming}` : '' }}</summary><a-input v-model:value="material.additionTiming" :disabled="readonly" placeholder="何时加入，例如水沸后加入" @update:value="publishLocal" /><a-input v-model:value="material.additionMethod" :disabled="readonly" placeholder="如何加入，例如分三次均匀加入" @update:value="publishLocal" /></details>
        </div>
        <a-button v-if="!readonly" class="add-material-bottom" block @click="addMaterial">＋ 添加投料</a-button>
      </section>

      <section class="section">
        <div class="section-title"><b>产出与中间状态</b><a-button v-if="!readonly" type="link" size="small" @click="addOutput">＋ 添加产出</a-button></div>
        <p class="hint">中间产物仅在本工艺版本流转，<b>不进入物料库</b>。名称与状态可沿用投入，产出重量请实际称重填写。每步最多一项主料产出。</p>
        <a-space v-if="!readonly" wrap style="margin-bottom:10px">
          <a-button v-for="material in selectedStep.materials.filter(item => item.materialRole!=='PACKAGING' && item.materialName?.trim())" :key="material.key" size="small" @click="inheritInput(material)">沿用投入：{{ material.materialName }}</a-button>
        </a-space>
        <div v-for="(output, index) in selectedStep.outputs || []" :key="output.key" class="output-row">
          <a-select v-model:value="output.outputType" :disabled="readonly" :options="outputTypes" @update:value="publishLocal" />
          <a-input v-model:value="output.outputName" :disabled="readonly" placeholder="产出名称/中间状态" @update:value="publishLocal" />
          <a-select v-model:value="output.materialState" :disabled="readonly" :options="states" @update:value="publishLocal" />
          <a-input-number v-model:value="output.weightKg" :disabled="readonly" :min="0" addon-after="kg" @update:value="publishLocal" />
          <a-checkbox v-model:checked="output.primaryOutput" :disabled="readonly" @change="ensurePrimaryOutput(output)">主料产出</a-checkbox>
          <a-checkbox v-model:checked="output.continueFlow" :disabled="readonly" @change="publishLocal">继续流转</a-checkbox>
          <a-input v-model:value="output.remark" :disabled="readonly" placeholder="备注" @update:value="publishLocal" />
          <a-button v-if="!readonly" type="text" danger @click="removeOutput(index)">删除</a-button>
        </div>
      </section>

      <section class="section live"><div><b>得率摘要</b><span>小步骤 {{ percent(stepYield.mainYieldPercent) }} · 大工序 {{ percent(majorYield.mainYieldPercent) }} · 最终 {{ percent(finalYield) }}</span><em v-if="majorYieldReason">{{ majorYieldReason }}</em></div><div><b>配方汇总</b><span>外部物料 {{ actualKg(recipeTotal) }}</span></div></section>
      <ControlPointEditor :model-value="selectedStep.controlPoints || []" :readonly="readonly" :confirmation-mode="confirmationMode" @update:model-value="replaceControls" @confirm-pass="confirmPass" @confirm-deviation="confirmDeviation" />
    </main>
    <main v-else class="no-step">{{ readonly ? '暂无小步骤' : '将左侧模板拖到这里，或点击模板添加小步骤' }}</main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { Modal } from "ant-design-vue";
import { outputFromInput } from './outputFromInput';
import TemporaryMaterialEditor from '../materials/TemporaryMaterialEditor.vue';
import {
  aggregateProcessRecipe, calculateBatchYield, calculateMajorProcessYield, calculateMinorStepYield,
  mainYieldUnavailableReason,
  nextProcessKey, normalizeProcessPlan, type ControlPointDraft, type MinorProcessStepDraft,
  type ProcessPlanDraft, type ProcessStepMaterialDraft, type StepOutputDraft,
  effectiveStepParameters, type StepParameterDraft,
} from "@rnd/shared";
import ControlPointEditor from "./ControlPointEditor.vue";
import StepParameterEditor from "./StepParameterEditor.vue";
import { summaryParameters } from "./stepParameterTemplates";
import { confirmProcessPlanRepair, previousFlowOutputs, type RemovedFlowConsumer } from "./processPlanFlow";
import { cloneVueValue } from "./cloneVueValue";
import { externalActualTotal, actualKg } from "./actualSummary";
import MaterialPicker from '../materials/MaterialPicker.vue';
import {selectCatalogMaterial,clearCatalogReference,type MaterialCatalogItem} from '@rnd/shared';

const props = withDefaults(defineProps<{ modelValue: ProcessPlanDraft; majorKey: string; readonly?: boolean; confirmationMode?: "legacy-sentinel" | "external" }>(), {
  confirmationMode: "legacy-sentinel",
});
const emit = defineEmits<{ "update:modelValue": [value: ProcessPlanDraft]; "confirm-pass": [point: ControlPointDraft]; "confirm-deviation": [point: ControlPointDraft] }>();
const clonePlan = (value: ProcessPlanDraft) => cloneVueValue(value);
const localPlan = ref(clonePlan(props.modelValue));
const temporaryOptions=computed(()=>{
 const rows=new Map<string,ProcessStepMaterialDraft>();
 for(const m of localPlan.value.majorProcesses)for(const s of m.steps)for(const r of s.materials)if(r.temporaryMaterialId&&!r.catalogMaterialId&&r.sourceType!=='STEP_OUTPUT')rows.set(r.temporaryMaterialId,r);
 return [...rows].map(([value,r])=>({value,label:`${r.materialName||'未命名'} · ${r.temporarySpecification||'未填规格'}`}));
});
const selectedStepKey = ref("");
const expanded = reactive({ basic: false });
const dragPayload = ref<{ kind: "template" | "existing"; name?: string; index?: number } | null>(null);

const major = computed(() => localPlan.value.majorProcesses.find(item => item.key === props.majorKey));
const selectedIndex = computed(() => Math.max(0, major.value?.steps.findIndex(step => step.key === selectedStepKey.value) ?? 0));
const selectedStep = computed(() => major.value?.steps.find(step => step.key === selectedStepKey.value));
const previousOutputs = computed(() => selectedStep.value ? previousFlowOutputs(localPlan.value, props.majorKey, selectedStep.value.key).map(item => ({ label: `${item.major.processName} / ${item.step.stepName} · ${item.output.outputName || "未命名产出"}（不进入物料库）`, value: item.output.id || item.output.key, output: item.output })) : []);
const stepYield = computed(() => selectedStep.value ? calculateMinorStepYield(selectedStep.value) : { mainYieldPercent: null });
const majorYield = computed(() => major.value ? calculateMajorProcessYield(major.value) : { mainYieldPercent: null });
const majorYieldReason = computed(() => major.value ? mainYieldUnavailableReason(major.value) : null);
const finalYield = computed(() => calculateBatchYield(localPlan.value));
const recipeTotal = computed(() => externalActualTotal(localPlan.value));

const stepTemplates = ["验收", "解冻", "清洗", "修割", "切块", "配料", "滚揉", "搅拌", "斩拌", "腌制", "焯水", "煎制", "炒制", "油炸", "煮制", "蒸煮", "冷却", "速冻", "包装", "金检", "研发取样"];
const materialRoles = [{ label: "主料", value: "PRIMARY" }, { label: "辅料", value: "AUXILIARY" }, { label: "加工用水", value: "PROCESS_WATER" }, {label:"包装物",value:"PACKAGING"}];
const packagingUnits=['个','张','只','袋','盒','箱','米'].map(value=>({label:value,value}));
const sourceTypes = [{ label: "外部物料", value: "EXTERNAL" }, { label: "前序中间产物", value: "STEP_OUTPUT" }];
const states = [{ label: "固体", value: "SOLID" }, { label: "液体", value: "LIQUID" }, { label: "半固体", value: "SEMI_SOLID" }];
const outputTypes = [{ label: "中间产物", value: "INTERMEDIATE" }, { label: "成品", value: "FINISHED" }, { label: "合格产出", value: "QUALIFIED" }, { label: "余料", value: "REUSABLE" }, { label: "尾料", value: "TAILING" }, { label: "取样", value: "SAMPLE" }, { label: "废弃", value: "WASTE" }];
const stepTypes = [{ label: "普通操作", value: "NORMAL" }, { label: "称重点", value: "WEIGH" }, { label: "物料变化", value: "MATERIAL_CHANGE" }, { label: "研发取样", value: "SAMPLE" }, { label: "废弃节点", value: "WASTE" }];

watch(() => props.modelValue, value => {
  localPlan.value = clonePlan(value);
  ensureSelection();
});
watch(() => props.majorKey, ensureSelection, { immediate: true });
watch(() => major.value?.steps.map(step => step.key), ensureSelection);

function ensureSelection() {
  if (!major.value?.steps.some(step => step.key === selectedStepKey.value)) {
    selectedStepKey.value = major.value?.steps[0]?.key || "";
  }
}

function newStep(name = ""): MinorProcessStepDraft {
  return {
    key: nextProcessKey("step"), sequence: (major.value?.steps.length || 0) + 1,
    stepCode: "", stepName: name, stepType: "NORMAL", parameter1Name: "", parameter1Value: "",
    parameter1Unit: "", parameter2Name: "", parameter2Value: "", parameter2Unit: "",
    equipment: "", instruction: "", materials: [], outputs: [], controlPoints: [],
  };
}

async function publish(next: ProcessPlanDraft) {
  const result = await confirmProcessPlanRepair(next, confirmFlowRepair);
  if (!result.accepted) {
    localPlan.value = clonePlan(props.modelValue);
    return false;
  }
  localPlan.value = normalizeProcessPlan(result.plan);
  emit("update:modelValue", clonePlan(localPlan.value));
  ensureSelection();
  return true;
}
function publishLocal() {
  if (!props.readonly) void publish(clonePlan(localPlan.value));
}
function flowWarning(consumers: RemovedFlowConsumer[]) {
  const names = consumers.map(item => `${item.majorName}/${item.stepName}/${item.materialName || "未命名投料"}`).join("、");
  return `该操作会修复 ${consumers.length} 项失效流转：${names}。无效中间投料将移除，外部物料残留来源 ID 将清除。`;
}
function confirmFlowRepair(consumers: RemovedFlowConsumer[]) {
  return new Promise<boolean>(resolve => {
    Modal.confirm({
      title: "确认修复受影响的下游投料",
      content: flowWarning(consumers),
      okText: "继续并修复",
      cancelText: "取消",
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

function mutate(mutator: (plan: ProcessPlanDraft) => void) {
  const next = clonePlan(props.modelValue);
  mutator(next);
  return publish(next);
}

async function addStep(name = "") {
  if (props.readonly) return;
  let key = "";
  const committed = await mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    if (!target) return;
    const step = newStep(name);
    key = step.key;
    target.steps.push(step);
  });
  if (committed) selectedStepKey.value = key;
}

async function copyStep() {
  if (!selectedStep.value) return;
  let key = "";
  const committed = await mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    const index = target?.steps.findIndex(step => step.key === selectedStepKey.value) ?? -1;
    if (!target || index < 0) return;
    const step = cloneVueValue(target.steps[index]!);
    step.id = undefined;
    step.key = nextProcessKey("step");
    key = step.key;
    step.stepName = `${step.stepName}（副本）`;
    step.materials.forEach(item => {
      item.id = undefined;
      item.key = nextProcessKey("material");
    });
    (step.outputs || []).forEach(item => {
      const id = nextProcessKey("output");
      item.id = id;
      item.key = id;
    });
    (step.controlPoints || []).forEach(point => {
      point.id = undefined;
      point.key = nextProcessKey("control");
      point.measurements.forEach(measurement => {
        measurement.id = undefined;
        measurement.key = nextProcessKey("measurement");
      });
    });
    target.steps.splice(index + 1, 0, step);
  });
  if (committed) selectedStepKey.value = key;
}

function removeStep() {
  const old = selectedStepKey.value;
  mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    if (target) target.steps = target.steps.filter(step => step.key !== old);
  });
}

function moveStep(index: number, offset: number) {
  mutate(plan => {
    const steps = plan.majorProcesses.find(item => item.key === props.majorKey)?.steps;
    if (!steps) return;
    const [step] = steps.splice(index, 1);
    if (step) steps.splice(index + offset, 0, step);
  });
}

async function addMaterial() { if(!selectedStep.value)return;const key=nextProcessKey('material');selectedStep.value.materials.push({ key, sequence: selectedStep.value.materials.length + 1, materialRole: "AUXILIARY", materialName: "", materialState: "SOLID", sourceType: "EXTERNAL" }); publishLocal();await nextTick();document.querySelector(`[data-material-key="${key}"]`)?.scrollIntoView?.({block:'nearest',behavior:'smooth'}); }
function removeMaterial(index: number) { selectedStep.value?.materials.splice(index, 1); publishLocal(); }
function addOutput() { if (!selectedStep.value) return; const id = nextProcessKey("output"); (selectedStep.value.outputs ||= []).push({ id, key: id, sequence: selectedStep.value.outputs.length + 1, outputType: "INTERMEDIATE", outputName: "", materialState: "SOLID", primaryOutput: false, continueFlow: true }); publishLocal(); }
function inheritInput(material: ProcessStepMaterialDraft) {
  if (!selectedStep.value || props.readonly) return;
  const outputs = selectedStep.value.outputs ||= [];
  outputs.push(outputFromInput(material, outputs.length + 1, outputs.some(item => item.primaryOutput)));
  publishLocal();
}
function removeOutput(index: number) { if (!selectedStep.value) return; selectedStep.value.outputs?.splice(index, 1); void publish(clonePlan(localPlan.value)); }
function replaceControls(value: ControlPointDraft[]) { if (!selectedStep.value) return; selectedStep.value.controlPoints = cloneVueValue(value); publishLocal(); }
function replaceParameters(value: StepParameterDraft[]) {
  if (!selectedStep.value || props.readonly) return;
  selectedStep.value.parameters = value;
  for (const index of [1,2] as const) { selectedStep.value[`parameter${index}Name`]=undefined; selectedStep.value[`parameter${index}Value`]=undefined; selectedStep.value[`parameter${index}Unit`]=undefined; }
  publishLocal();
}
function confirmPass(point: ControlPointDraft) { emit("confirm-pass", cloneVueValue(point)); }
function confirmDeviation(point: ControlPointDraft) { emit("confirm-deviation", cloneVueValue(point)); }
function onSourceChange(material: ProcessStepMaterialDraft) {
  Object.assign(material,clearCatalogReference(material));
  material.materialCode=undefined;material.materialName='';material.sourceStepOutputId=undefined;
  if (material.sourceType === "EXTERNAL") publishLocal();
  else {
    material.materialCode = undefined;
    material.formulaMaterialId = undefined;
    delete material.sourceStepOutputId;
  }
}
function selectMaterial(material:ProcessStepMaterialDraft,item:MaterialCatalogItem){const packaging=material.materialRole==='PACKAGING';Object.assign(material,selectCatalogMaterial(material,item,packaging?'packaging':'ingredient'),{temporaryMaterialId:undefined,temporaryClassificationId:undefined,temporarySpecification:undefined,...(packaging?{weightKg:undefined,quantityUnit:item.unit}:{} )});publishLocal();}
function startTemporary(material:ProcessStepMaterialDraft){Object.assign(material,clearCatalogReference(material),{temporaryMaterialId:nextProcessKey('tmp'),temporaryClassificationId:undefined,temporarySpecification:undefined,materialCode:undefined});publishLocal();}
function updateTemporary(definition:ProcessStepMaterialDraft){for(const m of localPlan.value.majorProcesses)for(const s of m.steps)for(const r of s.materials)if(r.temporaryMaterialId===definition.temporaryMaterialId)Object.assign(r,{materialName:definition.materialName,temporaryClassificationId:definition.temporaryClassificationId,temporarySpecification:definition.temporarySpecification});publishLocal();}
function reuseTemporary(material:ProcessStepMaterialDraft,id:string){const rows=localPlan.value.majorProcesses.flatMap(m=>m.steps.flatMap(s=>s.materials));const source=rows.find(r=>r.temporaryMaterialId===id);if(!source)return;const packaging=source.materialRole==='PACKAGING';Object.assign(material,clearCatalogReference(material),{temporaryMaterialId:id,temporaryClassificationId:source.temporaryClassificationId,temporarySpecification:source.temporarySpecification,materialName:source.materialName,materialCode:undefined,materialRole:source.materialRole,sourceType:'EXTERNAL',sourceStepOutputId:undefined,quantityUnit:packaging?source.quantityUnit:undefined,quantity:undefined,...(packaging?{weightKg:undefined}:{})});publishLocal();}
function previousOutputsFor(material: ProcessStepMaterialDraft) { return material.materialRole === "PRIMARY" ? previousOutputs.value.filter(item => item.output.primaryOutput) : previousOutputs.value; }
function applyOutputSource(material: ProcessStepMaterialDraft) { const output = previousOutputs.value.find(item => item.value === material.sourceStepOutputId)?.output; if (output) { Object.assign(material, clearCatalogReference(material)); material.materialCode=undefined; material.formulaMaterialId=undefined; material.materialName = output.outputName; material.materialState = output.materialState; material.weightKg = output.weightKg; } publishLocal(); }
function ensurePrimaryMaterial(material: ProcessStepMaterialDraft) {
 const packaging=material.materialRole==='PACKAGING',wasPackaging=!!material.quantityUnit||material.quantity!=null;
 if(packaging){material.weightKg=undefined;material.sourceType='EXTERNAL';material.sourceStepOutputId=undefined;if(material.catalogUnit==='kg')Object.assign(material,clearCatalogReference(material),{materialName:'',materialCode:undefined});}
 else if(wasPackaging){Object.assign(material,clearCatalogReference(material),{quantity:undefined,quantityUnit:undefined,materialName:'',materialCode:undefined,temporaryMaterialId:undefined,temporaryClassificationId:undefined,temporarySpecification:undefined});}
 if(packaging&&material.temporaryClassificationId){material.temporaryClassificationId=undefined;material.temporaryMaterialId=nextProcessKey('tmp');}
 if (material.materialRole === "PRIMARY") selectedStep.value?.materials.forEach(item => { if (item.key !== material.key && item.materialRole==='PRIMARY') item.materialRole = "AUXILIARY"; }); publishLocal();
}
function ensurePrimaryOutput(output: StepOutputDraft) { if (output.primaryOutput) selectedStep.value?.outputs?.forEach(item => { if (item.key !== output.key) item.primaryOutput = false; }); publishLocal(); }

function dragTemplate(event: DragEvent, name: string) { dragPayload.value = { kind: "template", name }; event.dataTransfer?.setData("application/rnd-step", JSON.stringify(dragPayload.value)); }
function dragExisting(event: DragEvent, index: number) { dragPayload.value = { kind: "existing", index }; event.dataTransfer?.setData("application/rnd-step", JSON.stringify(dragPayload.value)); }
function payload(event: DragEvent) { try { return JSON.parse(event.dataTransfer?.getData("application/rnd-step") || ""); } catch { return dragPayload.value; } }
async function dropAt(event: DragEvent, index: number) {
  if (props.readonly || !major.value) return;
  const data = payload(event);
  dragPayload.value = null;
  if (data?.kind === "template" && typeof data.name === "string" && stepTemplates.includes(data.name)) {
    const step = newStep(data.name);
    const committed = await mutate(plan => plan.majorProcesses.find(item => item.key === props.majorKey)?.steps.splice(index, 0, step));
    if (committed) selectedStepKey.value = step.key;
  } else if (data?.kind === "existing" && Number.isInteger(data.index) && data.index >= 0 && data.index < major.value.steps.length) {
    const target = Math.min(index, major.value.steps.length - 1);
    if (data.index !== target) await moveStep(data.index, target - data.index);
  }
}
function parameterSummary(step: MinorProcessStepDraft) { const all=effectiveStepParameters(step),selected=summaryParameters(all); return [selected.map(p=>`${p.name || '未命名'} ${p.actualValue ?? '待补充'} ${p.unit || ''}`).join(' · '),all.length>selected.length?`另有 ${all.length-selected.length} 项，展开查看`:''].filter(Boolean).join(' · ') || "关键参数待设置"; }
function percent(value: number | null) { return value == null ? "待补充" : `${value.toFixed(2)}%`; }
</script>

<style scoped>
.minor-workspace { display: grid; grid-template-columns: 220px minmax(0, 1fr); min-height: 520px; border: 1px solid #e5e6eb; border-radius: 12px; overflow: hidden; background: #f6f7f9; }
.step-rail { display: grid; align-content: start; gap: 7px; padding: 14px; background: #fff; border-right: 1px solid #e5e6eb; }
.rail-heading small, .step-item small, .crumb, .editor-header p, .hint { display: block; color: #86909c; font-size: 12px; }
.step-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 4px; }
.step-item { min-width: 0; text-align: left; padding: 10px; border: 1px solid transparent; border-radius: 8px; background: #f7f8fa; cursor: pointer; }
.step-item.active { border-color: #91caff; background: #e6f4ff; }
.step-item span { display: inline-block; width: 26px; color: #1677ff; font-size: 12px; }
.step-item b { font-size: 13px; }
.step-moves { display: grid; gap: 2px; }
.step-moves button { border: 0; border-radius: 4px; background: #f0f5ff; color: #1677ff; font-size: 11px; }
.step-templates { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 8px; padding-top: 10px; border-top: 1px solid #f0f0f0; }
.step-templates button { border: 1px solid #d9d9d9; border-radius: 5px; background: #fff; padding: 4px 7px; font-size: 12px; cursor: grab; }
.step-editor { display: grid; grid-template-columns: minmax(0, 1fr); align-content: start; gap: 10px; padding: 16px; overflow: auto; }
.step-editor > * { min-width: 0; max-width: 100%; }
.editor-header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: start; gap: 8px; }
.editor-header > * { min-width: 0; overflow-wrap: anywhere; }
.editor-header :deep(.ant-space) { flex-wrap: wrap; }
.editor-header h3 { margin: 3px 0; } .editor-header p { margin: 0; }
.section { padding: 13px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.section-title { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; color: #595959; font-size: 13px; }
.hint { margin: 0 0 8px; }
.material-row, .output-row { display: grid; gap: 6px; align-items: center; margin-top: 6px; }
.material-row, .output-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.material-row > *, .output-row > *, .step-editor { min-width: 0; width: 100%; }
.material-details { grid-column:1/-1; padding:6px 0; color:#667085; font-size:12px; }.material-details summary { cursor:pointer; }.material-details :deep(.ant-input) { margin-top:6px; }
.live { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; background: #f0f7ff; }
.live b, .live span { display: block; } .live span { margin-top: 3px; color: #1677ff; }
.live em { display: block; margin-top: 4px; color: #d46b08; font-size: 11px; font-style: normal; line-height: 1.35; }
.no-step { display: grid; place-content: center; min-height: 300px; color: #86909c; }
button:focus-visible, :deep(.ant-btn:focus-visible) { outline: 3px solid #69b1ff; outline-offset: 2px; }
@media (max-width: 1100px) { .minor-workspace { grid-template-columns: 180px minmax(0, 1fr); } .material-row, .output-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } .summary-grid { grid-template-columns: 1fr; } }
</style>
