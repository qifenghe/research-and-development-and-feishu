<template>
  <section class="trial-workbench">
    <header class="scheme-bar">
      <div class="scheme-tabs" role="tablist" aria-label="试验方案">
        <button v-for="item in trials" :key="item.id" :class="{ active: item.id === currentTrial?.id }" :disabled="busy" @click="switchTrial(item.id)">
          <span>{{ item.name }}</span><a-tag v-if="item.archived" color="default">已归档</a-tag><small>v{{ item.versionNo }}</small>
        </button>
      </div>
      <a-space>
        <a-button size="small" :disabled="readonly || busy || !formId" @click="newTrialOpen = true">新建方案</a-button>
        <a-button size="small" :disabled="!editable" @click="openCopy">复制</a-button>
        <a-button size="small" :disabled="readonly || busy || !currentTrial" @click="toggleArchive">{{ currentTrial?.archived ? "恢复" : "归档" }}</a-button>
        <a-button size="small" :loading="saving" :disabled="!editable || !dirty" @click="saveTrial()">保存方案</a-button>
        <a-button size="small" type="primary" :disabled="!editable" @click="openPromotionPreview">提交为正式工艺</a-button>
      </a-space>
    </header>

    <a-spin :spinning="loading">
      <a-empty v-if="!currentTrial" description="尚无试验方案。新建方案后可独立编排，不会写入正式工艺。" />
      <template v-else>
        <a-alert v-if="staleCachedTrial" type="warning" show-icon message="发现与服务器版本不一致的本地缓存，已隔离保留，未覆盖当前方案。" closable @close="discardStaleCache" />
        <a-alert v-if="currentTrial.archived" type="info" show-icon message="该方案已归档，仅可查看或恢复。" />

        <section class="trial-summary">
          <div class="summary-head"><div><span class="eyebrow">试验方案</span><h3>{{ currentTrial.name }}</h3></div><a-tag :color="dirty ? 'orange' : 'green'">{{ dirty ? "有未保存修改" : "已与服务器同步" }}</a-tag></div>
          <a-form layout="vertical">
            <a-row :gutter="10">
              <a-col :span="8"><a-form-item label="方案名称"><a-input v-model:value="currentTrial.name" :disabled="!editable" @update:value="markDirty" /></a-form-item></a-col>
              <a-col :span="8"><a-form-item label="目的"><a-input v-model:value="currentTrial.purpose" :disabled="!editable" placeholder="本轮要验证什么" @update:value="markDirty" /></a-form-item></a-col>
              <a-col :span="8"><a-form-item label="变量"><a-input v-model:value="currentTrial.variables" :disabled="!editable" placeholder="与基线相比改变什么" @update:value="markDirty" /></a-form-item></a-col>
            </a-row>
            <a-row :gutter="10">
              <a-col :span="8"><a-form-item label="结论"><a-select v-model:value="currentTrial.conclusion" :disabled="!editable" :options="conclusionOptions" @change="markDirty" /></a-form-item></a-col>
              <a-col :span="16"><a-form-item label="结论说明"><a-input v-model:value="currentTrial.recommendationReason" :disabled="!editable" placeholder="推荐、调整或淘汰依据" @update:value="markDirty" /></a-form-item></a-col>
            </a-row>
          </a-form>
          <a-collapse ghost>
            <a-collapse-panel key="evaluation" header="感官/质量评价与操作难度（展开填写）">
              <a-row :gutter="10">
                <a-col :span="6"><a-form-item label="质量评分（0-10）"><a-input-number v-model:value="currentTrial.qualityScore" :disabled="!editable" :min="0" :max="10" style="width:100%" @update:value="markDirty" /></a-form-item></a-col>
                <a-col :span="6"><a-form-item label="操作难度"><a-select v-model:value="currentTrial.difficulty" allow-clear :disabled="!editable" :options="difficultyOptions" @change="markDirty" /></a-form-item></a-col>
                <a-col :span="12"><a-form-item label="评价记录"><a-textarea v-model:value="currentTrial.qualityNotes" :disabled="!editable" :rows="2" @update:value="markDirty" /></a-form-item></a-col>
              </a-row>
            </a-collapse-panel>
          </a-collapse>
        </section>

        <section v-if="!activeMajorKey" class="editor-shell">
          <div class="editor-title"><div><b>大工序编排</b><small>该图仅保存到当前试验方案</small></div></div>
          <MajorProcessBoard :model-value="currentTrial.plan" :readonly="!editable" :selected-key="activeMajorKey" @update:model-value="replacePlan" @select="activeMajorKey = $event" />
        </section>
        <section v-else class="editor-shell">
          <div class="breadcrumb"><a-button type="link" @click="activeMajorKey = undefined">‹ 返回大工序</a-button><span>试验方案 / {{ activeMajor?.processName }}</span></div>
          <MinorStepWorkspace
            v-if="activeMajor"
            :model-value="currentTrial.plan"
            :major-key="activeMajor.key"
            :readonly="!editable"
            confirmation-mode="external"
            @update:model-value="replacePlan"
            @confirm-pass="confirmControlPass"
            @confirm-deviation="openDeviationConfirm"
          >
            <template #selected-step-extension="{ major, step }">
              <a-collapse class="planned-extension">
                <a-collapse-panel key="planned" header="计划与偏差（当前步骤）">
                  <p class="hint">计划值与现场实际分开保存；缺失实际显示“待补充”，不按 0 计算。</p>
                  <div v-for="material in step.materials" :key="material.key" class="planned-row">
                    <span>{{ material.materialName || "未命名物料" }}</span>
                    <a-input-number :value="plannedMaterial(material)" :disabled="!editable" :min="0" addon-after="kg" placeholder="计划投入" @update:value="setPlannedMaterial(material, $event)" />
                    <small>{{ actualAndDifference(`material:${nodeId(material)}`, "kg") }}</small>
                  </div>
                  <div class="planned-grid">
                    <a-form-item :label="`${step.parameter1Name || '参数 1'}计划值`"><a-input :value="plannedParameter(step, 1)" :disabled="!editable" @update:value="setPlannedParameter(step, 1, $event)" /><small>{{ actualAndDifference(`parameter:${nodeId(step)}:1`) }}</small></a-form-item>
                    <a-form-item :label="`${step.parameter2Name || '参数 2'}计划值`"><a-input :value="plannedParameter(step, 2)" :disabled="!editable" @update:value="setPlannedParameter(step, 2, $event)" /><small>{{ actualAndDifference(`parameter:${nodeId(step)}:2`) }}</small></a-form-item>
                    <a-form-item label="大工序目标得率"><a-input-number :value="plannedMajorYield(major)" :disabled="!editable" :min="0" :max="1000" addon-after="%" style="width:100%" @update:value="setPlannedMajorYield(major, $event)" /><small>{{ actualAndDifference(`yield:${nodeId(major)}`, "%") }}</small></a-form-item>
                    <a-form-item label="最终目标得率"><a-input-number v-model:value="currentTrial.plannedData.batchYieldTarget" :disabled="!editable" :min="0" :max="1000" addon-after="%" style="width:100%" @update:value="markDirty" /><small>{{ actualAndDifference("yield:batch", "%") }}</small></a-form-item>
                  </div>
                  <a-form-item label="得率口径说明"><a-input v-model:value="currentTrial.plannedData.yieldBasisNote" :disabled="!editable" @update:value="markDirty" /></a-form-item>
                </a-collapse-panel>
              </a-collapse>
            </template>
          </MinorStepWorkspace>
        </section>

        <TrialComparison v-model:baseline-trial-id="baselineTrialId" :trial="currentTrial" :trials="trials" />
      </template>
    </a-spin>

    <a-modal v-model:open="newTrialOpen" title="新建试验方案" :confirm-loading="mutating" @ok="createTrial">
      <a-form layout="vertical"><a-form-item label="方案名称"><a-input v-model:value="newTrialName" placeholder="如：方案 A · 降盐 10%" /></a-form-item><a-form-item label="目的"><a-input v-model:value="newTrialPurpose" /></a-form-item><a-form-item label="变量"><a-input v-model:value="newTrialVariables" /></a-form-item></a-form>
      <a-alert type="info" show-icon message="将以当前正式工艺为结构起点；后续编辑只写入试验方案。" />
    </a-modal>

    <a-modal v-model:open="copyOpen" title="复制试验方案" :confirm-loading="mutating" @ok="copyTrial">
      <a-form layout="vertical"><a-form-item label="新方案名称"><a-input v-model:value="copyName" /></a-form-item><a-checkbox v-model:checked="copyActuals">包含实测记录（将标记为继承数据，不能直接作为新证据）</a-checkbox></a-form>
    </a-modal>

    <a-modal v-model:open="switchModalOpen" title="当前方案有未保存修改" :closable="false" :keyboard="false" :mask-closable="false">
      <p>切换方案或离开页面前，请处理当前修改。</p>
      <template #footer><a-button @click="resolveUnsaved(false)">取消切换</a-button><a-button danger @click="discardAndResolve">放弃修改</a-button><a-button type="primary" :loading="saving" @click="saveAndResolve">保存并切换</a-button></template>
    </a-modal>

    <a-modal v-model:open="deviationOpen" title="确认试验偏差闭环" :confirm-loading="mutating" @ok="confirmControlDeviation">
      <a-alert type="warning" show-icon message="只有系统确认结果有效；说明会进入操作记录，不会覆盖控制点依据。" />
      <a-form-item label="处置与复测说明" style="margin-top:12px"><a-textarea v-model:value="deviationNote" :rows="3" /></a-form-item>
    </a-modal>

    <a-modal v-model:open="promotionOpen" title="正式提交预览与确认" width="680" :confirm-loading="promoting" :ok-button-props="{ disabled: !promotionConfirmed || !promotionPreview?.checks.ready }" ok-text="确认提交正式版本" @ok="promoteTrial">
      <template v-if="promotionPreview">
        <a-alert :type="promotionPreview.checks.ready ? 'success' : 'error'" show-icon :message="promotionPreview.checks.ready ? '服务端校验通过' : '服务端校验未通过'" />
        <a-alert v-if="promotionPreview.differingDraft" type="warning" show-icon message="当前正式草稿不同，提交后原草稿将被安全保留，可从正式版本记录中读取。" style="margin-top:10px" />
        <ul v-if="promotionPreview.checks.errors.length" class="issues"><li v-for="item in promotionPreview.checks.errors" :key="`${item.code}-${item.message}`">{{ item.message }}</li></ul>
        <ul v-if="promotionPreview.checks.warnings.length" class="issues warning"><li v-for="item in promotionPreview.checks.warnings" :key="`${item.code}-${item.message}`">{{ item.message }}</li></ul>
        <a-form-item v-if="promotionFormalStatus === 'SUBMITTED'" label="变更原因（基于已有正式版本时必填）"><a-textarea v-model:value="promotionChangeReason" :rows="2" /></a-form-item>
        <a-checkbox v-model:checked="promotionConfirmed">我确认将当前已保存试验方案提交为正式工艺；只有本次明确确认会更新正式工艺。</a-checkbox>
      </template>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { message, Modal } from "ant-design-vue";
import { createEmptyProcessPlan, normalizeProcessPlan, type ControlPointDraft, type MajorProcessDraft, type MinorProcessStepDraft, type ProcessPlanDraft, type ProcessRevision, type ProcessStepMaterialDraft } from "@rnd/shared";
import { api } from "../../services/api";
import { trialApi, type TrialPlannedData, type TrialPromotionPreview, type TrialScheme } from "../../services/trialApi";
import MajorProcessBoard from "../process/MajorProcessBoard.vue";
import MinorStepWorkspace from "../process/MinorStepWorkspace.vue";
import TrialComparison from "./TrialComparison.vue";
import { plannedActualRows } from "./trialComparison";
import { clearTrialDraft, prepareTrialPlanForSave, readTrialDraft, trialDraftKey, TrialRequestFence, writeTrialDraft } from "./trialDraft";

const props = defineProps<{ formId?: string; userId: string; formalPlan: ProcessPlanDraft; readonly?: boolean }>();
const emit = defineEmits<{ promoted: [revision: ProcessRevision] }>();
const trials = ref<TrialScheme[]>([]);
const currentTrial = ref<TrialScheme>();
const staleCachedTrial = ref<TrialScheme>();
const activeMajorKey = ref<string>();
const baselineTrialId = ref<string>();
const loading = ref(false);
const saving = ref(false);
const mutating = ref(false);
const promoting = ref(false);
const dirty = ref(false);
const newTrialOpen = ref(false);
const newTrialName = ref("方案 A");
const newTrialPurpose = ref("");
const newTrialVariables = ref("");
const copyOpen = ref(false);
const copyName = ref("");
const copyActuals = ref(false);
const switchModalOpen = ref(false);
const switchResolver = ref<((accepted: boolean) => void)>();
const deviationOpen = ref(false);
const deviationNote = ref("");
const pendingControlLocation = ref<{ major: number; step: number; point: number }>();
const promotionOpen = ref(false);
const promotionPreview = ref<TrialPromotionPreview>();
const promotionConfirmed = ref(false);
const promotionChangeReason = ref("");
const promotionFormalStatus = ref<ProcessPlanDraft["status"]>("DRAFT");
const promotionIdempotencyKey = ref("");
const expectedProcessVersionNo = ref(0);
const listFence = new TrialRequestFence();
const detailFence = new TrialRequestFence();
const mutationFence = new TrialRequestFence();
const promotionFence = new TrialRequestFence();

const busy = computed(() => loading.value || saving.value || mutating.value || promoting.value);
const editable = computed(() => !props.readonly && !busy.value && Boolean(currentTrial.value) && !currentTrial.value?.archived);
const activeMajor = computed(() => currentTrial.value?.plan.majorProcesses.find(major => major.key === activeMajorKey.value));
const conclusionOptions = [{ value: "PENDING", label: "待结论" }, { value: "ADJUST", label: "继续调整" }, { value: "REJECT", label: "淘汰" }, { value: "RECOMMEND", label: "推荐提交" }];
const difficultyOptions = [{ value: "EASY", label: "容易" }, { value: "MEDIUM", label: "中等" }, { value: "HARD", label: "困难" }];

watch(() => props.formId, formId => {
  listFence.invalidate(); detailFence.invalidate(); mutationFence.invalidate(); promotionFence.invalidate();
  trials.value = []; currentTrial.value = undefined; staleCachedTrial.value = undefined; activeMajorKey.value = undefined; baselineTrialId.value = undefined; dirty.value = false;
  if (formId) void loadTrials(formId);
}, { immediate: true });

onMounted(() => { window.addEventListener("beforeunload", beforeUnload); window.addEventListener("pagehide", persistCache); });
onBeforeUnmount(() => { listFence.invalidate(); detailFence.invalidate(); mutationFence.invalidate(); promotionFence.invalidate(); window.removeEventListener("beforeunload", beforeUnload); window.removeEventListener("pagehide", persistCache); });
onBeforeRouteLeave(async () => dirty.value ? await confirmUnsaved() : true);
defineExpose({ openCreatedTrial });

async function loadTrials(formId: string) {
  const token = listFence.begin(formId, "list");
  loading.value = true;
  try {
    const value = await trialApi.list(formId);
    if (!listFence.isCurrent(token) || formId !== props.formId) return;
    trials.value = value.map(normalizeTrial);
    const selected = trials.value.find(item => !item.archived) || trials.value[0];
    if (selected) await loadTrial(selected.id);
  } catch (error) {
    if (listFence.isCurrent(token)) message.error(errorMessage(error, "无法加载试验方案"));
  } finally {
    if (listFence.isCurrent(token)) loading.value = false;
  }
}

async function loadTrial(trialId: string) {
  const formId = props.formId;
  if (!formId) return;
  const token = detailFence.begin(formId, trialId);
  loading.value = true;
  try {
    const value = normalizeTrial(await trialApi.find(formId, trialId));
    if (!detailFence.isCurrent(token) || formId !== props.formId) return;
    adoptServer(value, true);
  } catch (error) {
    if (detailFence.isCurrent(token)) message.error(errorMessage(error, "无法读取试验方案"));
  } finally {
    if (detailFence.isCurrent(token)) loading.value = false;
  }
}

async function switchTrial(trialId: string) {
  if (trialId === currentTrial.value?.id) return;
  if (dirty.value && !await confirmUnsaved()) return;
  await loadTrial(trialId);
}

async function openCreatedTrial(trialId: string) {
  const formId = props.formId;
  if (!formId || (dirty.value && !await confirmUnsaved())) return;
  await loadTrials(formId);
  if (currentTrial.value?.id !== trialId) await loadTrial(trialId);
}

function normalizeTrial(value: TrialScheme): TrialScheme {
  const clone = plainClone(value);
  clone.plan = normalizeProcessPlan(clone.plan || createEmptyProcessPlan());
  clone.plannedData = normalizePlannedData(clone.plannedData);
  clone.majorOrigins ||= {};
  return clone;
}

function normalizePlannedData(value?: Partial<TrialPlannedData> | null): TrialPlannedData {
  return { materialWeightsKg: { ...(value?.materialWeightsKg || {}) }, stepParameters: { ...(value?.stepParameters || {}) }, majorYieldTargets: { ...(value?.majorYieldTargets || {}) }, batchYieldTarget: value?.batchYieldTarget ?? null, yieldBasisNote: value?.yieldBasisNote ?? null };
}

function adoptServer(value: TrialScheme, allowCache = false) {
  const server = normalizeTrial(value);
  const key = cacheKey(server.id);
  const cached = allowCache && key ? readTrialDraft<TrialScheme>(localStorage, key) : null;
  staleCachedTrial.value = undefined;
  if (cached && cached.value.versionNo === server.versionNo && Date.parse(cached.savedAt) > Date.parse(server.updatedAt)) {
    currentTrial.value = normalizeTrial(cached.value);
    dirty.value = true;
  } else {
    currentTrial.value = server;
    dirty.value = false;
    if (cached && cached.value.versionNo !== server.versionNo) staleCachedTrial.value = cached.value;
  }
  activeMajorKey.value = undefined;
  trials.value = [server, ...trials.value.filter(item => item.id !== server.id)];
}

function replacePlan(value: ProcessPlanDraft) {
  if (!editable.value || !currentTrial.value) return;
  currentTrial.value.plan = normalizeProcessPlan(plainClone(value));
  markDirty();
}

function markDirty() {
  if (!currentTrial.value || props.readonly || currentTrial.value.archived || busy.value) return;
  dirty.value = true;
  persistCache();
}

function persistCache() {
  const trial = currentTrial.value;
  const key = trial ? cacheKey(trial.id) : null;
  if (dirty.value && trial && key) writeTrialDraft(localStorage, key, plainClone(trial));
}

async function saveTrial() {
  const trial = currentTrial.value;
  const formId = props.formId;
  if (!trial || !formId || !dirty.value || props.readonly || trial.archived) return !dirty.value;
  const token = mutationFence.begin(formId, `save:${trial.id}`);
  saving.value = true;
  persistCache();
  try {
    const acknowledgement = await trialApi.save(formId, trial.id, {
      versionNo: trial.versionNo, name: trial.name.trim(), purpose: text(trial.purpose), variables: text(trial.variables), conclusion: trial.conclusion,
      recommendationReason: text(trial.recommendationReason), qualityScore: trial.qualityScore, qualityNotes: text(trial.qualityNotes), difficulty: trial.difficulty,
      plan: prepareTrialPlanForSave(trial.plan), plannedData: plainClone(trial.plannedData),
    });
    if (!mutationFence.isCurrent(token) || currentTrial.value?.id !== trial.id || formId !== props.formId) return false;
    const key = cacheKey(trial.id);
    if (key) clearTrialDraft(localStorage, key);
    adoptServer(acknowledgement);
    message.success("试验方案已保存");
    return true;
  } catch (error) {
    if (mutationFence.isCurrent(token)) {
      persistCache();
      message.error(errorMessage(error, "方案保存失败；本地修改已保留，未覆盖服务器"));
    }
    return false;
  } finally {
    if (mutationFence.isCurrent(token)) saving.value = false;
  }
}

async function createTrial() {
  const formId = props.formId;
  if (!formId || !newTrialName.value.trim()) return;
  const token = mutationFence.begin(formId, "create");
  mutating.value = true;
  try {
    const value = await trialApi.create(formId, { name: newTrialName.value.trim(), purpose: text(newTrialPurpose.value), variables: text(newTrialVariables.value), plan: prepareTrialPlanForSave(normalizeProcessPlan(plainClone(props.formalPlan))), plannedData: normalizePlannedData() });
    if (!mutationFence.isCurrent(token) || formId !== props.formId) return;
    newTrialOpen.value = false;
    adoptServer(value);
  } catch (error) { if (mutationFence.isCurrent(token)) message.error(errorMessage(error, "新建方案失败")); }
  finally { if (mutationFence.isCurrent(token)) mutating.value = false; }
}

function openCopy() { if (!currentTrial.value) return; copyName.value = `${currentTrial.value.name}（副本）`; copyActuals.value = false; copyOpen.value = true; }
async function copyTrial() {
  const formId = props.formId; const trial = currentTrial.value;
  if (!formId || !trial || !copyName.value.trim()) return;
  if (dirty.value && !await saveTrial()) return;
  const current = currentTrial.value!;
  const token = mutationFence.begin(formId, `copy:${current.id}`); mutating.value = true;
  try {
    const value = await trialApi.copy(formId, current.id, { versionNo: current.versionNo, name: copyName.value.trim(), includeActuals: copyActuals.value });
    if (!mutationFence.isCurrent(token) || formId !== props.formId) return;
    copyOpen.value = false; adoptServer(value);
  } catch (error) { if (mutationFence.isCurrent(token)) message.error(errorMessage(error, "复制方案失败")); }
  finally { if (mutationFence.isCurrent(token)) mutating.value = false; }
}

function toggleArchive() {
  const trial = currentTrial.value;
  if (!trial || dirty.value) { if (dirty.value) message.warning("请先保存或放弃当前修改"); return; }
  Modal.confirm({ title: trial.archived ? "恢复该试验方案？" : "归档该试验方案？", content: "该操作使用版本校验，不会删除方案记录。", onOk: () => archiveTrial(!trial.archived) });
}
async function archiveTrial(archived: boolean) {
  const formId = props.formId; const trial = currentTrial.value; if (!formId || !trial) return;
  const token = mutationFence.begin(formId, `archive:${trial.id}`); mutating.value = true;
  try { const value = await trialApi.archive(formId, trial.id, { versionNo: trial.versionNo, archived }); if (mutationFence.isCurrent(token)) adoptServer(value); }
  catch (error) { if (mutationFence.isCurrent(token)) message.error(errorMessage(error, archived ? "归档失败" : "恢复失败")); }
  finally { if (mutationFence.isCurrent(token)) mutating.value = false; }
}

async function confirmControlPass(point: ControlPointDraft) {
  const location = locatePoint(point); if (!location || !await saveTrial()) return;
  await performControlConfirmation(location, false);
}
function openDeviationConfirm(point: ControlPointDraft) { const location = locatePoint(point); if (!location) return; pendingControlLocation.value = location; deviationNote.value = ""; deviationOpen.value = true; }
async function confirmControlDeviation() {
  const location = pendingControlLocation.value;
  if (!location || !deviationNote.value.trim() || !await saveTrial()) return;
  deviationOpen.value = false;
  await performControlConfirmation(location, true, deviationNote.value.trim());
}
async function performControlConfirmation(location: { major: number; step: number; point: number }, deviation: boolean, note = "") {
  const formId = props.formId; const trial = currentTrial.value; const point = trial?.plan.majorProcesses[location.major]?.steps[location.step]?.controlPoints?.[location.point];
  if (!formId || !trial || !point?.id) return;
  const token = mutationFence.begin(formId, `confirm:${trial.id}:${point.id}`); mutating.value = true;
  try {
    const value = deviation
      ? await trialApi.confirmControlDeviation(formId, trial.id, point.id, trial.versionNo, note)
      : await trialApi.confirmControlPoint(formId, trial.id, point.id, trial.versionNo);
    if (mutationFence.isCurrent(token)) { adoptServer(value); message.success(deviation ? "偏差闭环已由服务端确认" : "控制点已由服务端确认"); }
  } catch (error) { if (mutationFence.isCurrent(token)) message.error(errorMessage(error, "确认失败")); }
  finally { if (mutationFence.isCurrent(token)) mutating.value = false; }
}

async function openPromotionPreview() {
  const formId = props.formId; if (!formId || !currentTrial.value || !await saveTrial()) return;
  const trial = currentTrial.value;
  const token = promotionFence.begin(formId, `preview:${trial.id}`); promoting.value = true;
  try {
    const formal = normalizeProcessPlan(await api.task.getProcessPlan(formId));
    if (!promotionFence.isCurrent(token) || currentTrial.value?.id !== trial.id) return;
    const preview = await trialApi.previewPromotion(formId, trial.id, trial.versionNo, formal.versionNo);
    if (!promotionFence.isCurrent(token) || currentTrial.value?.id !== trial.id) return;
    promotionPreview.value = preview; expectedProcessVersionNo.value = formal.versionNo; promotionFormalStatus.value = formal.status;
    promotionChangeReason.value = formal.status === "SUBMITTED" ? "" : formal.changeReason || ""; promotionConfirmed.value = false; promotionIdempotencyKey.value = newIdempotencyKey(); promotionOpen.value = true;
  } catch (error) { if (promotionFence.isCurrent(token)) message.error(errorMessage(error, "无法生成正式提交预览")); }
  finally { if (promotionFence.isCurrent(token)) promoting.value = false; }
}
async function promoteTrial() {
  const formId = props.formId; const trial = currentTrial.value; const preview = promotionPreview.value;
  if (!formId || !trial || !preview || !promotionConfirmed.value || !preview.checks.ready) return;
  if (promotionFormalStatus.value === "SUBMITTED" && !promotionChangeReason.value.trim()) { message.warning("请填写变更原因"); return; }
  const token = promotionFence.begin(formId, `submit:${trial.id}`); promoting.value = true;
  try {
    const revision = await trialApi.promote(formId, trial.id, { trialVersionNo: trial.versionNo, expectedProcessVersionNo: expectedProcessVersionNo.value, previewToken: preview.previewToken, confirmed: true, changeReason: text(promotionChangeReason.value), idempotencyKey: promotionIdempotencyKey.value });
    if (!promotionFence.isCurrent(token) || currentTrial.value?.id !== trial.id) return;
    promotionOpen.value = false; promotionPreview.value = undefined; emit("promoted", revision); message.success("试验方案已提交，正式工艺已刷新");
  } catch (error) { if (promotionFence.isCurrent(token)) message.error(errorMessage(error, "正式提交失败；请按当前预览重试或重新预览")); }
  finally { if (promotionFence.isCurrent(token)) promoting.value = false; }
}

function confirmUnsaved() { if (!dirty.value) return Promise.resolve(true); if (switchResolver.value) return Promise.resolve(false); switchModalOpen.value = true; return new Promise<boolean>(resolve => { switchResolver.value = resolve; }); }
function resolveUnsaved(accepted: boolean) { const resolve = switchResolver.value; switchResolver.value = undefined; switchModalOpen.value = false; resolve?.(accepted); }
async function saveAndResolve() { if (await saveTrial()) resolveUnsaved(true); }
function discardAndResolve() { const trial = currentTrial.value; const key = trial ? cacheKey(trial.id) : null; if (key) clearTrialDraft(localStorage, key); dirty.value = false; resolveUnsaved(true); }
function beforeUnload(event: BeforeUnloadEvent) { if (!dirty.value) return; persistCache(); event.preventDefault(); event.returnValue = ""; }
function discardStaleCache() { const trial = currentTrial.value; const key = trial ? cacheKey(trial.id) : null; if (key) clearTrialDraft(localStorage, key); staleCachedTrial.value = undefined; }

function plannedMaterial(material: ProcessStepMaterialDraft) { return currentTrial.value?.plannedData.materialWeightsKg[nodeId(material)]; }
function setPlannedMaterial(material: ProcessStepMaterialDraft, value: number | null) { const data = currentTrial.value?.plannedData; if (!data || !editable.value) return; setRecordValue(data.materialWeightsKg, nodeId(material), value); markDirty(); }
function plannedParameter(step: MinorProcessStepDraft, index: 1 | 2) { const value = currentTrial.value?.plannedData.stepParameters[nodeId(step)]; return index === 1 ? value?.parameter1Value : value?.parameter2Value; }
function setPlannedParameter(step: MinorProcessStepDraft, index: 1 | 2, value: string) { const data = currentTrial.value?.plannedData; if (!data || !editable.value) return; const key = nodeId(step); const current = data.stepParameters[key] || { parameter1Value: null, parameter2Value: null }; data.stepParameters[key] = { ...current, [index === 1 ? "parameter1Value" : "parameter2Value"]: text(value) }; if (!data.stepParameters[key].parameter1Value && !data.stepParameters[key].parameter2Value) delete data.stepParameters[key]; markDirty(); }
function plannedMajorYield(major: MajorProcessDraft) { return currentTrial.value?.plannedData.majorYieldTargets[nodeId(major)]; }
function setPlannedMajorYield(major: MajorProcessDraft, value: number | null) { const data = currentTrial.value?.plannedData; if (!data || !editable.value) return; setRecordValue(data.majorYieldTargets, nodeId(major), value); markDirty(); }
function setRecordValue(record: Record<string, number>, key: string, value: number | null) { if (value == null) delete record[key]; else record[key] = value; }
function actualAndDifference(key: string, unit = "") {
  const trial = currentTrial.value;
  const row = trial ? plannedActualRows(trial).find(item => item.key === key) : undefined;
  if (!row || !row.complete) return "实际：待补充 · 偏差：—";
  const actual = `${row.actual}${unit}`;
  if (row.kind === "PARAMETER") return `实际：${actual} · ${row.planned === row.actual ? "一致" : "不同"}`;
  const difference = row.difference == null ? "—" : `${row.difference > 0 ? "+" : ""}${row.difference}${unit}`;
  return `实际：${actual} · 偏差：${difference}`;
}
function locatePoint(point: ControlPointDraft) { const target = point.id || point.key; const plan = currentTrial.value?.plan; if (!plan) return; for (let major = 0; major < plan.majorProcesses.length; major++) for (let step = 0; step < plan.majorProcesses[major]!.steps.length; step++) { const pointIndex = plan.majorProcesses[major]!.steps[step]!.controlPoints?.findIndex(item => (item.id || item.key) === target) ?? -1; if (pointIndex >= 0) return { major, step, point: pointIndex }; } }
function nodeId(value: { id?: string; key: string }) { return value.id || value.key; }
function cacheKey(trialId: string) { return props.formId && props.userId ? trialDraftKey(props.userId, props.formId, trialId) : null; }
function text(value?: string | null) { const trimmed = value?.trim(); return trimmed ? trimmed : null; }
function plainClone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T; }
function errorMessage(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback; }
function newIdempotencyKey() { return globalThis.crypto?.randomUUID?.() || `trial-submit-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
</script>

<style scoped>
.trial-workbench { display: grid; gap: 12px; padding: 12px; border-radius: 12px; background: #f5f6f8; }
.scheme-bar { position: sticky; top: 0; z-index: 2; display: flex; justify-content: space-between; gap: 12px; align-items: center; padding: 9px 11px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.scheme-tabs { display: flex; gap: 5px; min-width: 0; overflow-x: auto; }
.scheme-tabs button { display: flex; gap: 5px; align-items: center; white-space: nowrap; padding: 6px 9px; border: 1px solid transparent; border-radius: 7px; background: #f7f8fa; cursor: pointer; }
.scheme-tabs button.active { border-color: #3370ff; color: #245bdb; background: #eef3ff; }
.scheme-tabs small { color: #86909c; }
.trial-summary, .editor-shell { padding: 13px; border: 1px solid #e5e6eb; border-radius: 10px; background: #fff; }
.summary-head, .editor-title, .breadcrumb { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.summary-head h3 { margin: 2px 0 8px; }.eyebrow { color: #3370ff; font-size: 12px; font-weight: 600; }
.editor-title { margin-bottom: 9px; }.editor-title small, .hint { display: block; color: #86909c; font-size: 12px; }.hint { margin: 0 0 8px; }
.breadcrumb { justify-content: flex-start; margin-bottom: 8px; color: #86909c; font-size: 12px; }
.planned-extension { margin: 0; border: 1px solid #b7d2ff; border-radius: 10px; background: #f6f9ff; }
.planned-row { display: grid; grid-template-columns: minmax(160px, 1fr) 220px 140px; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #e5e6eb; }
.planned-row small { color: #595959; }.planned-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.issues { margin: 10px 0; padding-left: 22px; color: #cf1322; }.issues.warning { color: #d46b08; }
button:focus-visible { outline: 3px solid #69b1ff; outline-offset: 2px; }
@media (max-width: 1000px) { .scheme-bar { align-items: flex-start; flex-direction: column; }.planned-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.planned-row { grid-template-columns: 1fr; } }
</style>
