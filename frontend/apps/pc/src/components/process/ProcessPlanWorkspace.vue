<template>
  <section class="workspace">
    <header class="workspace-top">
      <div class="title">
        <span class="eyebrow">研发工艺工作台</span>
        <h2>工艺、配方与 SOP</h2>
        <div class="tags"><a-tag :color="plan.status === 'SUBMITTED' ? 'green' : 'blue'">{{ plan.status === "SUBMITTED" ? "正式版本" : "草稿" }}</a-tag><span :class="`save-${saveState}`">{{ saveLabel }}</span></div>
      </div>
      <div class="top-summary">
        <span>大工序 <b>{{ plan.majorProcesses.length }}</b></span>
        <span>小步骤 <b>{{ stepCount }}</b></span>
        <span>最终得率 <b>{{ percent(finalYield) }}</b></span>
      </div>
      <a-space>
        <ProcessYieldComparison :form-id="formId" />
        <a-button :disabled="readonly || plan.status !== 'DRAFT'" :loading="saveState === 'saving'" @click="saveNow()">保存草稿</a-button>
        <a-button type="primary" :disabled="readonly || !formId || plan.status !== 'DRAFT' || saveState === 'saving'" @click="openSubmit">正式提交</a-button>
      </a-space>
    </header>

    <div class="workspace-main">
      <div v-if="!activeMajorKey" class="view">
        <div class="view-bar"><div><b>大工序编排</b><small>拖拽或键盘排序；模板可重复使用</small></div></div>
        <MajorProcessBoard :model-value="plan" :readonly="readonly || plan.status !== 'DRAFT'" :selected-key="activeMajorKey" @update:model-value="replacePlan" @select="openMajor" />
      </div>
      <div v-else class="view">
        <div class="breadcrumb">
          <a-button type="link" @click="activeMajorKey = undefined">‹ 返回大工序</a-button>
          <span>工艺工作台 / {{ activeMajor?.processName }}</span>
        </div>
        <MinorStepWorkspace v-if="activeMajor" :model-value="plan" :major-key="activeMajor.key" :readonly="readonly || plan.status !== 'DRAFT'" @update:model-value="replacePlan" @confirm-deviation="confirmDeviation" />
      </div>

      <aside class="side">
        <section class="side-card">
          <div class="side-title"><b>版本历史</b><a-button size="small" type="link" @click="loadRevisions">刷新</a-button></div>
          <a-empty v-if="!revisions.length" :image="false" description="尚未提交正式版本" />
          <button v-for="revision in revisions" :key="revision.id" class="revision" @click="loadRevision(revision.id)">
            <b>R{{ revision.revisionNo }}</b>
            <span>{{ formatDate(revision.submittedAt) }}</span>
            <small>{{ revision.changeReason || "首次正式提交" }}</small>
          </button>
        </section>
        <RndOutputCenter v-model:selected-revision-id="selectedOutputRevisionId" :form-id="formId" :revisions="revisions" :readonly="readonly" />
      </aside>
    </div>

    <a-drawer v-model:open="revisionDrawerOpen" width="760" title="不可变工艺版本快照">
      <template v-if="selectedRevision">
        <a-descriptions size="small" :column="2" bordered>
          <a-descriptions-item label="版本">R{{ selectedRevision.revisionNo }}</a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ formatDate(selectedRevision.submittedAt) }}</a-descriptions-item>
          <a-descriptions-item label="来源版本">{{ selectedRevision.sourceRevisionId || "首次提交" }}</a-descriptions-item>
          <a-descriptions-item label="变更原因">{{ selectedRevision.changeReason || "首次正式提交" }}</a-descriptions-item>
          <a-descriptions-item label="配方总重">{{ revisionRecipeWeight.toFixed(3) }} kg</a-descriptions-item>
          <a-descriptions-item label="成品得率">{{ percent(revisionFinalYield) }}</a-descriptions-item>
        </a-descriptions>
        <a-alert v-if="revisionSource" type="info" show-icon class="revision-source">
          <template #message>来源：试验方案 {{ revisionSource.trialName }} · v{{ revisionSource.trialVersionNo }}</template>
          <template #description>由 {{ revisionSource.promotedBy }} 于 {{ formatDate(revisionSource.promotedAt) }} 提交；来源快照 {{ revisionSource.trialSnapshotHash.slice(0, 12) }}…</template>
        </a-alert>
        <section class="revision-diff">
          <h3>版本差异</h3>
          <p v-if="!selectedRevisionSource">首次正式版本，无来源版本可比较。</p>
          <a-empty v-else-if="!revisionDifferences.length" :image="false" description="与来源版本无工艺内容差异" />
          <div v-for="item in revisionDifferences" :key="`${item.type}-${item.path}-${item.before}-${item.after}`" class="revision-diff-line">
            <a-tag :color="item.type === 'ADDED' ? 'green' : item.type === 'REMOVED' ? 'red' : 'blue'">{{ item.type === "ADDED" ? "新增" : item.type === "REMOVED" ? "删除" : "修改" }}</a-tag>
            <span><b>{{ item.path }}</b><small>{{ item.type === "CHANGED" ? `${item.before} → ${item.after}` : item.before || item.after }}</small></span>
          </div>
        </section>
        <ProcessPlanSnapshot :plan="selectedRevision.snapshot" />
        <section v-if="canRecoverDisplacedDraft && revisionSource" class="recovery">
          <div class="side-title"><div><b>提交前被替换草稿</b><small>研发内部数据，仅按需读取</small></div><a-button size="small" :loading="recoveryLoading" @click="loadDisplacedDraft">读取草稿</a-button></div>
          <a-empty v-if="recoveryLoaded && !displacedDraft" :image="false" description="本次提交未替换不同的正式草稿" />
          <template v-if="displacedDraft"><ProcessPlanSnapshot :plan="displacedDraft" /><a-button block @click="copyRecoveredDraft">复制为新试验方案</a-button></template>
        </section>
        <a-button v-if="!readonly" block type="primary" @click="openNewDraft">从该版本新建草稿</a-button>
      </template>
    </a-drawer>

    <a-modal v-model:open="newDraftOpen" title="从正式版本新建草稿">
      <p>将复制 R{{ selectedRevision?.revisionNo }} 为新的可编辑草稿，正式版本保持不变。</p>
      <a-form layout="vertical">
        <a-form-item label="变更原因（必填）"><a-textarea v-model:value="newDraftReason" :rows="3" /></a-form-item>
        <a-checkbox v-model:checked="newDraftConfirmed">我确认基于该版本创建新草稿</a-checkbox>
      </a-form>
      <template #footer>
        <a-button @click="newDraftOpen = false">取消</a-button>
        <a-button type="primary" :disabled="!newDraftReason.trim() || !newDraftConfirmed" @click="createDraft">新建草稿</a-button>
      </template>
    </a-modal>
    <ProcessSubmitDialog v-model:open="showSubmit" :form-id="formId" :plan="plan" :ready="saveState !== 'dirty' && saveState !== 'saving'" @submitted="onSubmitted" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { message, Modal } from "ant-design-vue";
import { aggregateProcessRecipe, calculateBatchYield, normalizeProcessPlan, type ControlPointDraft, type ProcessPlanDraft, type ProcessRevision, type ProcessRevisionSummary } from "@rnd/shared";
import { api } from "../../services/api";
import { trialApi, type TrialPromotionSource } from "../../services/trialApi";
import { createStructureOnlyTrialSource, prepareTrialPlanForSave } from "../trials/trialDraft";
import MajorProcessBoard from "./MajorProcessBoard.vue";
import MinorStepWorkspace from "./MinorStepWorkspace.vue";
import ProcessSubmitDialog from "./ProcessSubmitDialog.vue";
import ProcessPlanSnapshot from "./ProcessPlanSnapshot.vue";
import RndOutputCenter from "./RndOutputCenter.vue";
import ProcessYieldComparison from "./ProcessYieldComparison.vue";
import { diffProcessPlans } from "./processPlanDiff";
import { ProcessPlanSaveCoordinator, type ProcessPlanSaveState } from "./processPlanAutosave";
import { RequestGeneration } from "./requestGeneration";
import { cloneVueValue } from "./cloneVueValue";

const props = defineProps<{
  modelValue: ProcessPlanDraft;
  formId?: string;
  readonly?: boolean;
  serverHydrationToken?: number;
  canRecoverDisplacedDraft?: boolean;
}>();
const emit = defineEmits<{ "update:modelValue": [value: ProcessPlanDraft]; "request-save": []; saved: [value: ProcessPlanDraft]; "trial-created": [trialId: string] }>();
const clonePlan = (value: ProcessPlanDraft) => cloneVueValue(value);
const plan = ref(normalizeProcessPlan(clonePlan(props.modelValue)));
const activeMajorKey = ref<string>();
const saveState = ref<ProcessPlanSaveState>("idle");
const revisions = ref<ProcessRevisionSummary[]>([]);
const selectedRevision = ref<ProcessRevision>();
const selectedRevisionSource = ref<ProcessRevision>();
const selectedOutputRevisionId = ref<string>();
const revisionDrawerOpen = ref(false);
const showSubmit = ref(false);
const newDraftOpen = ref(false);
const newDraftReason = ref("");
const newDraftConfirmed = ref(false);
const revisionSource = ref<TrialPromotionSource | null>();
const displacedDraft = ref<ProcessPlanDraft | null>();
const recoveryLoaded = ref(false);
const recoveryLoading = ref(false);
const listRequests = new RequestGeneration();
const detailRequests = new RequestGeneration();
const sourceRequests = new RequestGeneration();
const recoveryRequests = new RequestGeneration();

const coordinator = new ProcessPlanSaveCoordinator<ProcessPlanDraft>({
  formId: props.formId,
  debounceMs: 1200,
  isDraft: value => value.status === "DRAFT",
  mergeAck: (local, ack) => normalizeProcessPlan({ ...local, legacy: ack.legacy, versionNo: ack.versionNo, status: ack.status, sourceRevisionId: ack.sourceRevisionId, changeReason: ack.changeReason, balanceToleranceKg: ack.balanceToleranceKg }),
  save: (formId, value) => api.task.saveProcessPlan(formId, { ...normalizeProcessPlan(value), experimentFormId: formId }),
  onChange: (value, state) => {
    saveState.value = state;
    if (state === "saved") {
      plan.value = normalizeProcessPlan(value);
      emit("update:modelValue", clonePlan(plan.value));
      emit("saved", clonePlan(plan.value));
    }
  },
  onError: error => message.error(error instanceof Error ? error.message : "工艺草稿保存失败，请刷新后比较差异"),
});
coordinator.hydrateServer(clonePlan(plan.value));

const activeMajor = computed(() => plan.value.majorProcesses.find(major => major.key === activeMajorKey.value));
const stepCount = computed(() => plan.value.majorProcesses.reduce((count, major) => count + major.steps.length, 0));
const finalYield = computed(() => calculateBatchYield(plan.value));
const saveLabel = computed(() => ({ idle: "", dirty: "待保存", saving: "正在保存", saved: "已保存", error: "保存失败，请重试" })[saveState.value]);
const revisionRecipe = computed(() => selectedRevision.value ? aggregateProcessRecipe(selectedRevision.value.snapshot) : []);
const revisionRecipeWeight = computed(() => revisionRecipe.value.reduce((sum, line) => sum + line.weightKg, 0));
const revisionFinalYield = computed(() => selectedRevision.value ? calculateBatchYield(selectedRevision.value.snapshot) : null);
const revisionDifferences = computed(() => selectedRevision.value && selectedRevisionSource.value
  ? diffProcessPlans(selectedRevisionSource.value.snapshot, selectedRevision.value.snapshot)
  : []);

watch(() => props.formId, (formId, previous) => {
  listRequests.invalidate(); detailRequests.invalidate(); sourceRequests.invalidate(); recoveryRequests.invalidate(); revisions.value = []; selectedRevision.value = undefined; selectedRevisionSource.value = undefined; revisionSource.value = undefined; displacedDraft.value = undefined; recoveryLoaded.value = false; selectedOutputRevisionId.value = undefined; revisionDrawerOpen.value = false; activeMajorKey.value = undefined; showSubmit.value = false; newDraftOpen.value = false;
  const incoming = normalizeProcessPlan(clonePlan(props.modelValue));
  const preservePreIdDraft = !previous && !!formId && plan.value.majorProcesses.length > 0;
  plan.value = incoming;
  coordinator.rebind(formId, incoming);
  if (preservePreIdDraft) coordinator.restoreLocalDirty(clonePlan(plan.value));
  if (formId) void loadRevisions();
}, { immediate: true });

watch(() => props.serverHydrationToken, (token, previous) => {
  if (token == null || token === previous) return;
  hydrateServer(props.modelValue);
});

onBeforeUnmount(() => {
  listRequests.invalidate();
  detailRequests.invalidate();
  sourceRequests.invalidate();
  recoveryRequests.invalidate();
  coordinator.dispose();
});

function replacePlan(value: ProcessPlanDraft) {
  if (props.readonly) return;
  plan.value = normalizeProcessPlan(clonePlan(value));
  emit("update:modelValue", clonePlan(plan.value));
  coordinator.edit(() => clonePlan(plan.value));
}

function openMajor(key: string) {
  activeMajorKey.value = key;
}

async function saveNow(silent = false) {
  if (props.readonly) return;
  if (!props.formId) {
    emit("request-save");
    return;
  }
  try {
    await coordinator.flush();
    if (!silent && saveState.value === "saved") message.success("工艺草稿已保存");
  } catch (error) {
    if (!silent) message.error(error instanceof Error ? error.message : "保存失败");
    throw error;
  }
}

async function openSubmit() {
  try {
    await saveNow(true);
    if (saveState.value !== "error" && saveState.value !== "saving") showSubmit.value = true;
  } catch (error) {
    message.error(error instanceof Error ? error.message : "请先处理草稿保存问题");
  }
}

async function confirmDeviation(point: ControlPointDraft) {
  const formId = props.formId;
  if (!formId || !point.id) return;
  try {
    await saveNow(true);
    const confirmed = normalizeProcessPlan(await api.task.confirmProcessDeviation(formId, point.id, point.basisOrRemark));
    if (formId !== props.formId) return;
    plan.value = confirmed;
    coordinator.hydrateServer(clonePlan(confirmed));
    emit("update:modelValue", clonePlan(confirmed));
    message.success("偏差已由当前负责人确认并留痕");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "偏差确认失败");
  }
}

function restoreLocalDirty(value: ProcessPlanDraft) {
  plan.value = normalizeProcessPlan(clonePlan(value));
  coordinator.restoreLocalDirty(clonePlan(plan.value));
}
function hydrateServer(value: ProcessPlanDraft) {
  plan.value = normalizeProcessPlan(clonePlan(value));
  coordinator.hydrateServer(clonePlan(plan.value));
}
defineExpose({ flushSave: saveNow, restoreLocalDirty, hydrateServer });

async function loadRevisions() {
  const formId = props.formId;
  if (!formId) return;
  const generation = listRequests.next();
  try {
    const value = await api.task.getProcessRevisions(formId);
    if (!listRequests.isCurrent(generation) || formId !== props.formId) return;
    revisions.value = value;
    if (!selectedOutputRevisionId.value && value[0]) selectedOutputRevisionId.value = value[0].id;
  } catch (error) {
    if (listRequests.isCurrent(generation)) message.error(error instanceof Error ? error.message : "无法加载版本历史");
  }
}

async function loadRevision(id: string) {
  const formId = props.formId;
  if (!formId) return;
  const generation = detailRequests.next();
  try {
    const revision = await api.task.getProcessRevision(formId, id);
    if (!detailRequests.isCurrent(generation) || formId !== props.formId) return;
    const sourceId = revision.sourceRevisionId
      || revisions.value.find(item => item.revisionNo === revision.revisionNo - 1)?.id;
    const source = sourceId ? await api.task.getProcessRevision(formId, sourceId) : undefined;
    if (!detailRequests.isCurrent(generation) || formId !== props.formId) return;
    selectedRevision.value = revision;
    selectedRevisionSource.value = source;
    recoveryRequests.invalidate();
    revisionSource.value = undefined;
    displacedDraft.value = undefined;
    recoveryLoaded.value = false;
    revisionDrawerOpen.value = true;
    void loadRevisionSource(formId, id);
  } catch (error) {
    if (detailRequests.isCurrent(generation)) message.error(error instanceof Error ? error.message : "无法读取版本详情");
  }
}

async function loadRevisionSource(formId: string, revisionId: string) {
  const generation = sourceRequests.next();
  try {
    const value = await trialApi.revisionSource(formId, revisionId);
    if (!sourceRequests.isCurrent(generation) || props.formId !== formId || selectedRevision.value?.id !== revisionId) return;
    revisionSource.value = value;
  } catch (error) {
    if (sourceRequests.isCurrent(generation)) message.error(error instanceof Error ? error.message : "无法读取版本来源");
  }
}

async function loadDisplacedDraft() {
  const formId = props.formId;
  const revisionId = selectedRevision.value?.id;
  if (!props.canRecoverDisplacedDraft || !formId || !revisionId) return;
  const generation = recoveryRequests.next();
  recoveryLoading.value = true;
  try {
    const value = await trialApi.displacedDraft(formId, revisionId);
    if (!recoveryRequests.isCurrent(generation) || props.formId !== formId || selectedRevision.value?.id !== revisionId) return;
    displacedDraft.value = value ? normalizeProcessPlan(value) : null;
    recoveryLoaded.value = true;
  } catch (error) {
    if (recoveryRequests.isCurrent(generation)) message.error(error instanceof Error ? error.message : "无法读取被替换草稿");
  } finally {
    if (recoveryRequests.isCurrent(generation)) recoveryLoading.value = false;
  }
}

function copyRecoveredDraft() {
  const formId = props.formId;
  const recovered = displacedDraft.value;
  if (!formId || !recovered || !props.canRecoverDisplacedDraft) return;
  Modal.confirm({
    title: "将被替换草稿复制为新试验方案？",
    content: "这不会恢复或覆盖当前正式工艺，只会创建一个独立试验方案。",
    okText: "创建试验方案",
    onOk: async () => {
      const structure = createStructureOnlyTrialSource(recovered);
      const value = await trialApi.create(formId, { name: `恢复草稿 · R${selectedRevision.value?.revisionNo || ""}`, purpose: "保留正式提交前草稿", variables: "来自被替换草稿（旧实测已转为计划值）", plan: prepareTrialPlanForSave(structure.plan), plannedData: structure.plannedData });
      if (props.formId !== formId) return;
      emit("trial-created", value.id);
      message.success("已复制为独立试验方案");
    },
  });
}

function openNewDraft() {
  newDraftReason.value = "";
  newDraftConfirmed.value = false;
  newDraftOpen.value = true;
}

async function createDraft() {
  const formId = props.formId;
  const revision = selectedRevision.value;
  if (!formId || !revision || !newDraftReason.value.trim() || !newDraftConfirmed.value) return;
  const generation = detailRequests.next();
  try {
    const draft = normalizeProcessPlan(await api.task.createDraftFromRevision(formId, revision.id, newDraftReason.value.trim()));
    if (!detailRequests.isCurrent(generation) || formId !== props.formId) return;
    plan.value = draft;
    coordinator.adoptServerDraft(draft);
    emit("update:modelValue", clonePlan(draft));
    activeMajorKey.value = undefined;
    newDraftOpen.value = false;
    revisionDrawerOpen.value = false;
    saveState.value = "saved";
    message.success("已从正式版本创建新草稿");
  } catch (error) {
    if (detailRequests.isCurrent(generation)) message.error(error instanceof Error ? error.message : "新建草稿失败");
  }
}

async function onSubmitted(revision: ProcessRevision) {
  revisions.value = [revision, ...revisions.value.filter(item => item.id !== revision.id)];
  selectedRevision.value = revision;
  selectedOutputRevisionId.value = revision.id;
  plan.value = normalizeProcessPlan(revision.snapshot);
  coordinator.hydrateServer(clonePlan(plan.value));
  emit("update:modelValue", clonePlan(plan.value));
  saveState.value = "saved";
  await nextTick();
  await loadRevisions();
}
function percent(value: number | null | undefined) { return value == null ? "待补充" : `${value.toFixed(2)}%`; }
function formatDate(value: string) { return value ? new Date(value).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : ""; }
</script>

<style scoped>
.workspace { display: grid; gap: 12px; padding: 14px; border-radius: 12px; background: #f5f6f8; } .workspace-top { display: flex; align-items: center; gap: 20px; padding: 14px 16px; border: 1px solid #e5e6eb; border-radius: 12px; background: #fff; } .title { min-width: 200px; } .eyebrow { color: #1677ff; font-size: 12px; font-weight: 600; } .title h2 { margin: 2px 0 5px; font-size: 20px; } .tags { display: flex; gap: 8px; align-items: center; font-size: 12px; color: #86909c; } .save-saving { color: #1677ff; } .save-error { color: #cf1322; } .save-saved { color: #389e0d; }
.top-summary { display: flex; gap: 16px; flex: 1; color: #595959; font-size: 13px; } .top-summary b { display: block; color: #262626; font-size: 17px; } .workspace-main { display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 12px; } .view, .side-card { padding: 12px; border: 1px solid #e5e6eb; border-radius: 12px; background: #fff; } .view-bar { display: flex; justify-content: space-between; margin-bottom: 10px; } .view-bar small { display: block; color: #86909c; font-size: 12px; margin-top: 3px; } .breadcrumb { display: flex; align-items: center; gap: 6px; margin-bottom: 9px; color: #86909c; font-size: 12px; } .side { display: grid; align-content: start; gap: 12px; } .side-title { display: flex; justify-content: space-between; align-items: center; }
.revision { display: grid; width: 100%; grid-template-columns: 36px 1fr; text-align: left; padding: 9px 0; border: 0; border-top: 1px solid #f0f0f0; background: #fff; cursor: pointer; } .revision span, .revision small { font-size: 12px; color: #86909c; } .revision small { grid-column: 2; }
.revision-diff { margin: 12px 0; padding: 12px; border: 1px solid #e5e6eb; border-radius: 8px; background: #fafafa; } .revision-diff h3 { margin: 0 0 8px; } .revision-diff-line { display: flex; align-items: start; gap: 6px; margin-top: 6px; } .revision-diff-line span, .revision-diff-line small { display: block; } .revision-diff-line small { color: #595959; font-weight: 400; }
.revision-source, .recovery { margin: 12px 0; } .recovery { padding: 12px; border: 1px solid #d6e4ff; border-radius: 8px; background: #f6f9ff; } .recovery small { display: block; color: #86909c; font-size: 12px; }
.workspace, .view, .side { min-width: 0; }
.workspace-main { grid-template-columns: minmax(0, 1fr); }
.side { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.workspace-top { flex-wrap: wrap; }
.top-summary { flex-wrap: wrap; min-width: 220px; }
.top-summary > span { white-space: nowrap; }
@media (max-width: 1120px) { .top-summary { order: 3; flex-basis: 100%; } }
@media (max-width: 760px) { .side { grid-template-columns: 1fr; } }
</style>
