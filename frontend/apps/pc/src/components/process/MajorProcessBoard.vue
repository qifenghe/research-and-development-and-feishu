<template>
  <div class="major-board">
    <aside class="template-library">
      <div class="library-title">
        <b>大工序模板</b>
        <small>可重复拖入</small>
      </div>
      <a-input v-model:value="keyword" allow-clear size="small" placeholder="搜索模板" />
      <button
        v-for="template in filteredTemplates"
        :key="template.name"
        class="template"
        :draggable="!readonly"
        :disabled="readonly"
        @dragstart="dragTemplate($event, template.name)"
        @dblclick="addMajor(template.name)"
      >
        <b>{{ template.name }}</b>
        <small>{{ template.description }}</small>
        <span>＋</span>
      </button>
      <a-button v-if="!readonly" block type="dashed" @click="openEditor()">＋ 新建大工序</a-button>
    </aside>

    <main class="board-canvas" @dragover.prevent @drop="dropAtEnd">
      <div v-if="!plan.majorProcesses.length" class="empty">
        <b>从模板开始编排大工序</b>
        <span>拖入模板，或新建一个大工序</span>
      </div>
      <article
        v-for="(major, index) in plan.majorProcesses"
        :key="major.key"
        class="major-card"
        :class="{ selected: selectedKey === major.key }"
        :draggable="!readonly"
        @dragstart="dragExisting($event, index)"
        @dragover.prevent
        @drop.stop="dropAt($event, index)"
      >
        <header>
          <span class="handle" aria-hidden="true">⠿</span>
          <span class="sequence">{{ String(index + 1).padStart(2, "0") }}</span>
          <div class="name">
            <b>{{ major.processName || "未命名大工序" }}</b>
            <small>{{ major.description || "请补充工序说明" }}</small>
          </div>
          <div class="metric"><small>小步骤</small><b>{{ major.steps.length }}</b></div>
          <div class="metric"><small>工序得率</small><b>{{ percent(yieldOf(major).mainYieldPercent) }}</b></div>
          <a-space @click.stop>
            <a-button
              size="small"
              :aria-label="`查看${major.processName || '未命名大工序'}步骤`"
              @click="emit('select', major.key)"
            >
              {{ readonly ? "查看步骤" : "进入步骤" }}
            </a-button>
            <template v-if="!readonly">
              <a-button size="small" :aria-label="`${major.processName}上移`" :disabled="index === 0" @click="moveMajor(index, -1)">上移</a-button>
              <a-button size="small" :aria-label="`${major.processName}下移`" :disabled="index === plan.majorProcesses.length - 1" @click="moveMajor(index, 1)">下移</a-button>
              <a-button size="small" @click="openEditor(index)">编辑</a-button>
            </template>
          </a-space>
        </header>
        <footer>
          <span>主料投入 {{ kg(yieldOf(major).primaryInputWeightKg) }}</span>
          <span>末端产出 {{ kg(yieldOf(major).qualifiedOutputWeightKg) }}</span>
          <span :class="{ warn: Math.abs(yieldOf(major).balanceDifferenceKg) > 0.01 }">平衡差 {{ kg(yieldOf(major).balanceDifferenceKg) }}</span>
          <a-button v-if="!readonly" type="link" size="small" @click="copyMajor(major.key)">复制</a-button>
        </footer>
      </article>
      <button v-if="!readonly" class="drop-zone" @click="openEditor()">＋ 添加或拖入大工序</button>
    </main>

    <a-modal v-model:open="editorOpen" :title="editingIndex < 0 ? '新建大工序' : '编辑大工序'">
      <a-form layout="vertical">
        <a-form-item label="工序名称"><a-input v-model:value="editing.processName" /></a-form-item>
        <a-form-item label="标准工序代码"><a-input v-model:value="editing.processCode" /></a-form-item>
        <a-form-item label="工序说明"><a-textarea v-model:value="editing.description" :rows="2" /></a-form-item>
        <a-form-item label="得率口径"><a-select v-model:value="editing.yieldBasis" :options="yieldOptions" /></a-form-item>
        <a-form-item label="不计算/差异说明"><a-textarea v-model:value="editing.remark" :rows="2" /></a-form-item>
      </a-form>
      <template #footer>
        <a-button v-if="editingIndex >= 0 && !readonly" danger @click="removeMajor">删除</a-button>
        <a-button @click="editorOpen = false">取消</a-button>
        <a-button type="primary" :disabled="readonly" @click="saveEditor">保存</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { Modal } from "ant-design-vue";
import {
  calculateMajorProcessYield,
  nextProcessKey,
  normalizeProcessPlan,
  type MajorProcessDraft,
  type ProcessPlanDraft,
} from "@rnd/shared";
import { confirmProcessPlanRepair, copyMajorProcess, type RemovedFlowConsumer } from "./processPlanFlow";
import { cloneVueValue } from "./cloneVueValue";
import { templateProcessCode } from "./processDraftRecovery";

const props = defineProps<{ modelValue: ProcessPlanDraft; readonly?: boolean; selectedKey?: string }>();
const emit = defineEmits<{ "update:modelValue": [value: ProcessPlanDraft]; select: [key: string] }>();
const plan = computed(() => props.modelValue);
const keyword = ref("");
const editorOpen = ref(false);
const editingIndex = ref(-1);
const dragPayload = ref<{ kind: "template" | "existing"; name?: string; index?: number } | null>(null);

const templates = [
  { name: "原辅料准备", description: "验收、领料和配料" },
  { name: "解冻与净制", description: "解冻、清洗和修整" },
  { name: "分切与规格化", description: "规格控制" },
  { name: "调味与腌制", description: "配料、滚揉和静置" },
  { name: "热加工", description: "焯、煎、炒、炸、煮" },
  { name: "冷却与冻结", description: "预冷、速冻" },
  { name: "分装与包装", description: "定量、封口" },
  { name: "检测与入库", description: "金检、放行" },
];
const editing = reactive<MajorProcessDraft>(blankMajor());
const yieldOptions = [
  { label: "主料首端投入 → 末端产出", value: "PRIMARY_INPUT" },
  { label: "不计算得率", value: "NONE" },
];
const filteredTemplates = computed(() => templates.filter(item => item.name.includes(keyword.value)));
const clonePlan = (value: ProcessPlanDraft) => cloneVueValue(value);

function blankMajor(name = ""): MajorProcessDraft {
  return {
    key: nextProcessKey("major"), sequence: plan.value.majorProcesses.length + 1, processCode: templateProcessCode(name),
    processName: name, description: templates.find(item => item.name === name)?.description || "",
    yieldBasis: "PRIMARY_INPUT", remark: "", steps: [], inputs: [], outputs: [],
  };
}

async function commitCandidate(next: ProcessPlanDraft) {
  const result = await confirmProcessPlanRepair(next, confirmFlowRepair);
  if (!result.accepted) return false;
  emit("update:modelValue", normalizeProcessPlan(result.plan));
  return true;
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

async function addMajor(name: string) {
  if (props.readonly) return;
  const next = clonePlan(plan.value);
  const major = blankMajor(name);
  next.majorProcesses.push(major);
  if (await commitCandidate(next)) emit("select", major.key);
}

function openEditor(index = -1) {
  editingIndex.value = index;
  Object.assign(editing, index < 0 ? blankMajor() : cloneVueValue(plan.value.majorProcesses[index]!));
  editorOpen.value = true;
}

async function saveEditor() {
  if (props.readonly || !editing.processName.trim()) return;
  const next = clonePlan(plan.value);
  if (editingIndex.value < 0) next.majorProcesses.push(cloneVueValue(editing));
  else next.majorProcesses.splice(editingIndex.value, 1, cloneVueValue(editing));
  if (await commitCandidate(next)) editorOpen.value = false;
}

async function removeMajor() {
  if (props.readonly || editingIndex.value < 0) return;
  const next = clonePlan(plan.value);
  next.majorProcesses.splice(editingIndex.value, 1);
  if (await commitCandidate(next)) editorOpen.value = false;
}

async function copyMajor(key: string) {
  if (props.readonly) return;
  const copied = copyMajorProcess(clonePlan(plan.value), key);
  if (copied.removedConsumers.length && !await confirmFlowRepair(copied.removedConsumers)) return;
  emit("update:modelValue", normalizeProcessPlan(copied.plan));
}

async function moveMajor(index: number, offset: number) {
  const target = index + offset;
  if (props.readonly || target < 0 || target >= plan.value.majorProcesses.length) return;
  const next = clonePlan(plan.value);
  const [major] = next.majorProcesses.splice(index, 1);
  if (major) next.majorProcesses.splice(target, 0, major);
  await commitCandidate(next);
}

function dragTemplate(event: DragEvent, name: string) {
  dragPayload.value = { kind: "template", name };
  event.dataTransfer?.setData("application/rnd-process", JSON.stringify(dragPayload.value));
}

function dragExisting(event: DragEvent, index: number) {
  dragPayload.value = { kind: "existing", index };
  event.dataTransfer?.setData("application/rnd-process", JSON.stringify(dragPayload.value));
}

function payload(event: DragEvent) {
  try { return JSON.parse(event.dataTransfer?.getData("application/rnd-process") || ""); }
  catch { return dragPayload.value; }
}

async function dropAt(event: DragEvent, index: number) {
  if (props.readonly) return;
  const data = payload(event);
  if (data?.kind === "template") {
    const next = clonePlan(plan.value);
    next.majorProcesses.splice(index, 0, blankMajor(data.name));
    await commitCandidate(next);
  } else if (data?.kind === "existing" && data.index !== index) {
    const next = clonePlan(plan.value);
    const [major] = next.majorProcesses.splice(data.index, 1);
    if (major) next.majorProcesses.splice(index, 0, major);
    await commitCandidate(next);
  }
}

async function dropAtEnd(event: DragEvent) {
  if (props.readonly) return;
  const data = payload(event);
  if (data?.kind === "template") await addMajor(data.name);
  else if (data?.kind === "existing") await moveMajor(data.index, plan.value.majorProcesses.length - 1 - data.index);
}

function yieldOf(major: MajorProcessDraft) { return calculateMajorProcessYield(major); }
function kg(value: number) { return `${value.toFixed(3)}kg`; }
function percent(value: number | null) { return value == null ? "待补充" : `${value.toFixed(2)}%`; }
</script>

<style scoped>
.major-board { display: grid; grid-template-columns: 210px minmax(0, 1fr); min-height: 440px; border: 1px solid #e5e6eb; border-radius: 12px; overflow: hidden; background: #f5f6f8; }
.template-library { display: grid; align-content: start; gap: 8px; padding: 14px; background: #fff; border-right: 1px solid #e5e6eb; }
.library-title small, .template small, .name small, .metric small { display: block; color: #86909c; font-size: 12px; }
.template { position: relative; padding: 10px 26px 10px 10px; text-align: left; border: 1px solid transparent; border-radius: 8px; background: #f7f8fa; cursor: grab; }
.template:disabled { cursor: default; }
.template:hover { border-color: #adc6ff; background: #f0f5ff; }
.template span { position: absolute; right: 9px; top: 9px; color: #1677ff; }
.board-canvas { padding: 14px; }
.empty { min-height: 180px; display: grid; place-content: center; text-align: center; border: 1px dashed #adc6ff; border-radius: 10px; background: #fff; }
.empty span { color: #86909c; }
.major-card { margin-bottom: 10px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; overflow: hidden; }
.major-card.selected { border-color: #1677ff; box-shadow: 0 0 0 2px #e6f4ff; }
.major-card header { display: grid; grid-template-columns: 20px 36px minmax(0, 1fr) 72px 90px; align-items: center; gap: 10px; padding: 12px; }
.major-card header :deep(.ant-space) { grid-column: 3 / -1; flex-wrap: wrap; }
.handle { color: #bfbfbf; cursor: grab; }
.sequence { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; background: #e6f4ff; color: #1677ff; font-weight: 700; }
.name b, .metric b { display: block; }
.major-card footer { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; padding: 8px 12px; background: #fafafa; border-top: 1px solid #f0f0f0; color: #595959; font-size: 12px; }
.major-card footer .ant-btn { margin-left: auto; }
.warn { color: #d4380d; }
.drop-zone { width: 100%; min-height: 48px; border: 1px dashed #91caff; border-radius: 10px; background: #fff; color: #1677ff; cursor: pointer; }
button:focus-visible, :deep(.ant-btn:focus-visible) { outline: 3px solid #69b1ff; outline-offset: 2px; }
@media (max-width: 960px) { .major-board { grid-template-columns: 180px minmax(0, 1fr); } .major-card header { grid-template-columns: 20px 36px 1fr auto; } .metric { display: none; } }
</style>
