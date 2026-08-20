<template>
  <div class="minor-workspace">
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
          @drop.stop="dropAt($event, index)"
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
        <small>模板可重复拖入</small>
        <button v-for="name in stepTemplates" :key="name" :draggable="!readonly" :disabled="readonly" @dragstart="dragTemplate($event, name)" @dblclick="addStep(name)">{{ name }}</button>
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
          <a-row :gutter="10">
            <a-col v-for="field in parameterFields" :key="field.key" :span="8"><a-form-item :label="field.label"><a-input v-model:value="selectedStep[field.key]" :disabled="readonly" :placeholder="field.placeholder" @update:value="publishLocal" /></a-form-item></a-col>
          </a-row>
          <a-form-item label="设备"><a-input v-model:value="selectedStep.equipment" :disabled="readonly" @update:value="publishLocal" /></a-form-item>
          <a-form-item label="操作要求"><a-textarea v-model:value="selectedStep.instruction" :disabled="readonly" :rows="2" @update:value="publishLocal" /></a-form-item>
        </a-form>
      </section>

      <section class="section">
        <div class="section-title"><b>本步骤投料</b><a-button v-if="!readonly" type="link" size="small" @click="addMaterial">＋ 添加投料</a-button></div>
        <p class="hint">外部物料可填写物料/配方 ID；中间产物仅可选择前序步骤继续流转的产出，<b>不进入物料库</b>。</p>
        <div v-for="(material, index) in selectedStep.materials" :key="material.key" class="material-row">
          <a-select v-model:value="material.sourceType" :disabled="readonly" :options="sourceTypes" @change="onSourceChange(material)" />
          <a-select v-if="material.sourceType === 'STEP_OUTPUT'" v-model:value="material.sourceStepOutputId" :disabled="readonly" :options="previousOutputsFor(material)" placeholder="选择前序产出" @change="applyOutputSource(material)" />
          <template v-else><a-input v-model:value="material.formulaMaterialId" :disabled="readonly" placeholder="配方物料 ID" @update:value="publishLocal" /><a-input v-model:value="material.materialCode" :disabled="readonly" placeholder="物料编码" @update:value="publishLocal" /></template>
          <a-select v-model:value="material.materialRole" :disabled="readonly" :options="materialRoles" @change="ensurePrimaryMaterial(material)" />
          <a-input v-model:value="material.materialName" :disabled="readonly" placeholder="物料名称" @update:value="publishLocal" />
          <a-input-number v-model:value="material.weightKg" :disabled="readonly" :min="0" addon-after="kg" @update:value="publishLocal" />
          <a-button v-if="!readonly" type="text" danger @click="removeMaterial(index)">删除</a-button>
        </div>
      </section>

      <section class="section">
        <div class="section-title"><b>产出与中间状态</b><a-button v-if="!readonly" type="link" size="small" @click="addOutput">＋ 添加产出</a-button></div>
        <p class="hint">中间产物仅在本工艺版本流转，<b>不进入物料库</b>。每步最多一项主料产出。</p>
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

      <section class="section live"><div><b>得率摘要</b><span>小步骤 {{ percent(stepYield.mainYieldPercent) }} · 大工序 {{ percent(majorYield.mainYieldPercent) }} · 最终 {{ percent(finalYield) }}</span></div><div><b>配方汇总</b><span>外部物料 {{ recipeTotal.toFixed(3) }} kg</span></div></section>
      <ControlPointEditor :model-value="selectedStep.controlPoints || []" :readonly="readonly" @update:model-value="replaceControls" />
    </main>
    <main v-else class="no-step">先添加一个小步骤</main>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { message } from "ant-design-vue";
import {
  aggregateProcessRecipe, calculateBatchYield, calculateMajorProcessYield, calculateMinorStepYield,
  nextProcessKey, normalizeProcessPlan, type ControlPointDraft, type MinorProcessStepDraft,
  type ProcessPlanDraft, type ProcessStepMaterialDraft, type StepOutputDraft,
} from "@rnd/shared";
import ControlPointEditor from "./ControlPointEditor.vue";
import { previousFlowOutputs, repairProcessPlanFlow, type RemovedFlowConsumer } from "./processPlanFlow";

const props = defineProps<{ modelValue: ProcessPlanDraft; majorKey: string; readonly?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ProcessPlanDraft] }>();
const localPlan = ref(structuredClone(props.modelValue));
const selectedStepKey = ref("");
const expanded = reactive({ basic: false });
const dragPayload = ref<{ kind: "template" | "existing"; name?: string; index?: number } | null>(null);

const major = computed(() => localPlan.value.majorProcesses.find(item => item.key === props.majorKey));
const selectedIndex = computed(() => Math.max(0, major.value?.steps.findIndex(step => step.key === selectedStepKey.value) ?? 0));
const selectedStep = computed(() => major.value?.steps.find(step => step.key === selectedStepKey.value));
const previousOutputs = computed(() => selectedStep.value ? previousFlowOutputs(localPlan.value, props.majorKey, selectedStep.value.key).map(item => ({ label: `${item.major.processName} / ${item.step.stepName} · ${item.output.outputName || "未命名产出"}（不进入物料库）`, value: item.output.id || item.output.key, output: item.output })) : []);
const stepYield = computed(() => selectedStep.value ? calculateMinorStepYield(selectedStep.value) : { mainYieldPercent: null });
const majorYield = computed(() => major.value ? calculateMajorProcessYield(major.value) : { mainYieldPercent: null });
const finalYield = computed(() => calculateBatchYield(localPlan.value));
const recipeTotal = computed(() => aggregateProcessRecipe(localPlan.value).reduce((sum, line) => sum + line.weightKg, 0));

const stepTemplates = ["验收", "解冻", "清洗", "修割", "配料", "滚揉", "腌制", "焯水", "煎制", "炒制", "油炸", "煮制", "冷却", "速冻", "包装", "金检", "研发取样"];
const materialRoles = [{ label: "主料", value: "PRIMARY" }, { label: "辅料", value: "AUXILIARY" }, { label: "加工用水", value: "PROCESS_WATER" }];
const sourceTypes = [{ label: "外部物料", value: "EXTERNAL" }, { label: "前序中间产物", value: "STEP_OUTPUT" }];
const states = [{ label: "固体", value: "SOLID" }, { label: "液体", value: "LIQUID" }, { label: "半固体", value: "SEMI_SOLID" }];
const outputTypes = [{ label: "中间产物", value: "INTERMEDIATE" }, { label: "成品", value: "FINISHED" }, { label: "合格产出", value: "QUALIFIED" }, { label: "余料", value: "REUSABLE" }, { label: "尾料", value: "TAILING" }, { label: "取样", value: "SAMPLE" }, { label: "废弃", value: "WASTE" }];
const stepTypes = [{ label: "普通操作", value: "NORMAL" }, { label: "称重点", value: "WEIGH" }, { label: "物料变化", value: "MATERIAL_CHANGE" }, { label: "研发取样", value: "SAMPLE" }, { label: "废弃节点", value: "WASTE" }];
const parameterFields = [
  { key: "parameter1Name" as const, label: "参数 1", placeholder: "温度" }, { key: "parameter1Value" as const, label: "值", placeholder: "" }, { key: "parameter1Unit" as const, label: "单位", placeholder: "" },
  { key: "parameter2Name" as const, label: "参数 2", placeholder: "时间" }, { key: "parameter2Value" as const, label: "值", placeholder: "" }, { key: "parameter2Unit" as const, label: "单位", placeholder: "" },
];

watch(() => props.modelValue, value => {
  localPlan.value = structuredClone(value);
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

function publish(next: ProcessPlanDraft, disruptive = false) {
  const repaired = repairProcessPlanFlow(next);
  if (repaired.removedConsumers.length && disruptive && !window.confirm(flowWarning(repaired.removedConsumers))) { localPlan.value = structuredClone(props.modelValue); return false; }
  if (repaired.removedConsumers.length && !disruptive) message.warning(flowWarning(repaired.removedConsumers));
  localPlan.value = normalizeProcessPlan(repaired.plan);
  emit("update:modelValue", structuredClone(localPlan.value));
  ensureSelection();
  return true;
}
function publishLocal() { if (!props.readonly) publish(structuredClone(localPlan.value)); }
function flowWarning(consumers: RemovedFlowConsumer[]) {
  const names = consumers.map(item => `${item.majorName}/${item.stepName}/${item.materialName || "未命名投料"}`).join("、");
  return `该操作会使 ${consumers.length} 项中间产物投料失效：${names}。将移除投料且不转为外部物料。是否继续？`;
}

function mutate(mutator: (plan: ProcessPlanDraft) => void, disruptive = false) {
  const next = structuredClone(props.modelValue);
  mutator(next);
  return publish(next, disruptive);
}

function addStep(name = "") {
  let key = "";
  mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    if (!target) return;
    const step = newStep(name);
    key = step.key;
    target.steps.push(step);
  });
  selectedStepKey.value = key;
}

function copyStep() {
  if (!selectedStep.value) return;
  let key = "";
  mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    const index = target?.steps.findIndex(step => step.key === selectedStepKey.value) ?? -1;
    if (!target || index < 0) return;
    const step = structuredClone(target.steps[index]!);
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
  selectedStepKey.value = key;
}

function removeStep() {
  const old = selectedStepKey.value;
  mutate(plan => {
    const target = plan.majorProcesses.find(item => item.key === props.majorKey);
    if (target) target.steps = target.steps.filter(step => step.key !== old);
  }, true);
}

function moveStep(index: number, offset: number) {
  mutate(plan => {
    const steps = plan.majorProcesses.find(item => item.key === props.majorKey)?.steps;
    if (!steps) return;
    const [step] = steps.splice(index, 1);
    if (step) steps.splice(index + offset, 0, step);
  }, true);
}

function addMaterial() { selectedStep.value?.materials.push({ key: nextProcessKey("material"), sequence: selectedStep.value.materials.length + 1, materialRole: "AUXILIARY", materialName: "", materialState: "SOLID", sourceType: "EXTERNAL" }); publishLocal(); }
function removeMaterial(index: number) { selectedStep.value?.materials.splice(index, 1); publishLocal(); }
function addOutput() { if (!selectedStep.value) return; const id = nextProcessKey("output"); (selectedStep.value.outputs ||= []).push({ id, key: id, sequence: selectedStep.value.outputs.length + 1, outputType: "INTERMEDIATE", outputName: "", materialState: "SOLID", primaryOutput: false, continueFlow: true }); publishLocal(); }
function removeOutput(index: number) { if (!selectedStep.value) return; selectedStep.value.outputs?.splice(index, 1); publish(structuredClone(localPlan.value), true); }
function replaceControls(value: ControlPointDraft[]) { if (!selectedStep.value) return; selectedStep.value.controlPoints = structuredClone(value); publishLocal(); }
function onSourceChange(material: ProcessStepMaterialDraft) { if (material.sourceType === "EXTERNAL") { delete material.sourceStepOutputId; publishLocal(); } else { material.materialCode = undefined; material.formulaMaterialId = undefined; } }
function previousOutputsFor(material: ProcessStepMaterialDraft) { return material.materialRole === "PRIMARY" ? previousOutputs.value.filter(item => item.output.primaryOutput) : previousOutputs.value; }
function applyOutputSource(material: ProcessStepMaterialDraft) { const output = previousOutputs.value.find(item => item.value === material.sourceStepOutputId)?.output; if (output) { material.materialName = output.outputName; material.materialState = output.materialState; material.weightKg = output.weightKg; } publishLocal(); }
function ensurePrimaryMaterial(material: ProcessStepMaterialDraft) { if (material.materialRole === "PRIMARY") selectedStep.value?.materials.forEach(item => { if (item.key !== material.key) item.materialRole = "AUXILIARY"; }); if (material.sourceType === "STEP_OUTPUT" && !previousOutputsFor(material).some(item => item.value === material.sourceStepOutputId)) delete material.sourceStepOutputId; publishLocal(); }
function ensurePrimaryOutput(output: StepOutputDraft) { if (output.primaryOutput) selectedStep.value?.outputs?.forEach(item => { if (item.key !== output.key) item.primaryOutput = false; }); publishLocal(); }

function dragTemplate(event: DragEvent, name: string) { dragPayload.value = { kind: "template", name }; event.dataTransfer?.setData("application/rnd-step", JSON.stringify(dragPayload.value)); }
function dragExisting(event: DragEvent, index: number) { dragPayload.value = { kind: "existing", index }; event.dataTransfer?.setData("application/rnd-step", JSON.stringify(dragPayload.value)); }
function payload(event: DragEvent) { try { return JSON.parse(event.dataTransfer?.getData("application/rnd-step") || ""); } catch { return dragPayload.value; } }
function dropAt(event: DragEvent, index: number) { if (props.readonly) return; const data = payload(event); if (data?.kind === "template") mutate(plan => plan.majorProcesses.find(item => item.key === props.majorKey)?.steps.splice(index, 0, newStep(data.name))); else if (data?.kind === "existing" && data.index !== index) moveStep(data.index, index - data.index); }
function parameterSummary(step: MinorProcessStepDraft) { return [[step.parameter1Name, step.parameter1Value, step.parameter1Unit].filter(Boolean).join(" "), [step.parameter2Name, step.parameter2Value, step.parameter2Unit].filter(Boolean).join(" ")].filter(Boolean).join(" · ") || "关键参数待设置"; }
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
.step-editor { display: grid; align-content: start; gap: 10px; padding: 16px; overflow: auto; }
.editor-header { display: flex; justify-content: space-between; align-items: start; }
.editor-header h3 { margin: 3px 0; } .editor-header p { margin: 0; }
.section { padding: 13px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; color: #595959; font-size: 13px; }
.hint { margin: 0 0 8px; }
.material-row, .output-row { display: grid; gap: 6px; align-items: center; margin-top: 6px; }
.material-row { grid-template-columns: 118px 110px 100px 90px minmax(120px, 1fr) 120px 45px; }
.output-row { grid-template-columns: 110px minmax(120px, 1fr) 82px 120px 82px 82px 130px 45px; }
.live { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; background: #f0f7ff; }
.live b, .live span { display: block; } .live span { margin-top: 3px; color: #1677ff; }
.no-step { display: grid; place-content: center; min-height: 300px; color: #86909c; }
button:focus-visible, :deep(.ant-btn:focus-visible) { outline: 3px solid #69b1ff; outline-offset: 2px; }
@media (max-width: 1100px) { .minor-workspace { grid-template-columns: 180px minmax(0, 1fr); } .material-row, .output-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } .summary-grid { grid-template-columns: 1fr; } }
</style>
