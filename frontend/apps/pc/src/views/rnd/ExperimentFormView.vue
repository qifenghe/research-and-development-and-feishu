<template>
  <div>
      <a-page-header
      :title="headerTitle"
      sub-title="现场录入配方、工序损耗、成品出成和照片附件"
      @back="router.back()"
    />

    <a-spin :spinning="loading">
      <a-alert
        v-if="readOnly"
        type="info"
        show-icon
        message="当前为只读查看模式。确认研发草稿无误后，可在右侧通知内部测试。"
        style="margin-bottom: 16px"
      />
      <a-card v-if="experimentPhase === 'arrange'" title="编排本次打样工序" class="page-card">
        <a-table :columns="processColumns" :data-source="processSteps" row-key="key" :pagination="false" size="small">
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'processName'"><a-input v-model:value="record.processName" /></template>
            <template v-else-if="column.key === 'beforeWeightKg'">—</template>
            <template v-else-if="column.key === 'afterWeightKg'">—</template>
            <template v-else-if="column.key === 'lossRate'">—</template>
            <template v-else-if="column.key === 'remark'"><a-input v-model:value="record.remark" placeholder="控制要点（可选）" /></template>
            <template v-else-if="column.key === 'action'">
              <a-space :size="0">
                <a-button type="link" :disabled="index === 0" @click="moveProcess(index, index - 1)">上移</a-button>
                <a-button type="link" :disabled="index === processSteps.length - 1" @click="moveProcess(index, index + 1)">下移</a-button>
                <a-button type="link" @click="copyProcess(index)">复制</a-button>
                <a-button type="link" danger :disabled="processSteps.length === 1" @click="removeProcess(index)">删除</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
        <a-button type="dashed" block style="margin-top:12px" @click="addProcess">+ 新增工序</a-button>
        <a-space style="margin-top:16px">
          <a-button type="primary" @click="confirmArrangement">确认工序并开始打样</a-button>
        </a-space>
      </a-card>

      <a-row v-else :gutter="16">
        <a-col :span="16">
          <a-card title="基本信息" class="page-card">
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12"><a-form-item label="产品名称"><a-input v-model:value="form.productName" disabled /></a-form-item></a-col>
                <a-col :span="12"><a-form-item label="产品规格"><a-input v-model:value="form.specification" disabled /></a-form-item></a-col>
              </a-row>
              <a-form-item label="实验摘要"><a-textarea v-model:value="form.summary" :rows="3" placeholder="填写本次打样说明" :disabled="readOnly" /></a-form-item>
            </a-form>
          </a-card>

          <a-collapse v-if="showLegacyProcessEditor" ghost class="page-card">
            <a-collapse-panel key="legacy-materials" header="历史兼容数据（旧版配方）">
            <a-alert type="info" show-icon style="margin-bottom:12px" message="利用率默认 100%。肉制品可勾选一项或多项主料；酱汁可按全部非包材物料计算得率。" />
            <a-radio-group v-model:value="yieldCalculationMode" :disabled="readOnly" style="margin-bottom:12px">
              <a-radio-button value="SELECTED_PRIMARY_MATERIALS">按所选主料</a-radio-button>
              <a-radio-button value="TOTAL_PICKING_WEIGHT">按全部非包材物料</a-radio-button>
            </a-radio-group>
            <a-table :columns="materialColumns" :data-source="materials" row-key="key" :pagination="false" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'role'">
                  <a-select v-model:value="record.materialCategory" :disabled="readOnly" style="width:86px" :options="materialCategoryOptions" />
                  <a-checkbox v-if="yieldCalculationMode === 'SELECTED_PRIMARY_MATERIALS' && record.materialCategory === 'RAW'" v-model:checked="record.primaryMaterial" :disabled="readOnly" style="margin-left:8px">主料</a-checkbox>
                </template>
                <template v-else-if="column.key === 'materialName'"><a-input v-model:value="record.materialName" :disabled="readOnly" placeholder="物料名称" /></template>
                <template v-else-if="column.key === 'weightKg'"><a-input-number v-model:value="record.weightKg" :disabled="readOnly" :min="0" :precision="4" addon-after="kg" style="width:100%" /></template>
                <template v-else-if="column.key === 'ratio'">{{ formulaRatioAt(index).toFixed(2) }}%</template>
                <template v-else-if="column.key === 'utilizationRate'"><a-input-number v-model:value="record.utilizationRate" :disabled="readOnly" :min="0" :max="100" addon-after="%" style="width:100%" /></template>
                <template v-else-if="column.key === 'action'"><a-button v-if="!readOnly && materials.length > 1" type="link" danger @click="removeMaterial(index)">删除</a-button></template>
              </template>
            </a-table>
            <a-button v-if="!readOnly" type="dashed" block style="margin-top:12px" @click="addMaterial">+ 添加物料</a-button>
            </a-collapse-panel>
          </a-collapse>

          <a-card title="工艺工作台" class="page-card process-plan-card">
            <ProcessPlanWorkspace
              ref="processWorkspace"
              v-model="processPlan"
              :form-id="detail?.currentExperimentForm?.id"
              :readonly="readOnly"
              :server-hydration-token="serverProcessHydrationToken"
              @request-save="saveDraft"
            />
            <a-collapse v-if="showLegacyProcessEditor" ghost style="margin-top:12px">
              <a-collapse-panel key="legacy" header="历史兼容数据（旧版工序编辑器）">
                <ProcessTabsEditor v-model="processSteps" :readonly="readOnly" :create-row="blankProcess" />
              </a-collapse-panel>
            </a-collapse>
          </a-card>

          <a-card title="成品产出" class="page-card">
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :span="8"><a-form-item label="成品重量"><a-input-number v-model:value="form.finishedOutputWeightKg" :disabled="readOnly" :min="0" :precision="4" addon-after="kg" style="width:100%" /></a-form-item></a-col>
                <a-col :span="8"><a-form-item label="成品数量"><a-input-number v-model:value="form.finishedOutputQuantity" :disabled="readOnly" :min="1" :precision="0" :step="1" style="width:100%" /></a-form-item></a-col>
                <a-col :span="8"><a-form-item label="成品单位"><a-select v-model:value="form.finishedOutputUnit" :disabled="readOnly" :options="outputUnitOptions" /></a-form-item></a-col>
              </a-row>
            </a-form>
            <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" :disabled="readOnly" /></a-form-item>
          </a-card>

          <a-card title="核价数据预览" class="page-card">
            <a-table :columns="previewMaterialColumns" :data-source="pricingPreviewMaterials" row-key="key" :pagination="false" size="small" />
            <a-descriptions :column="2" size="small" bordered style="margin-top:12px">
              <a-descriptions-item label="总投入">{{ pricingPreview.totalInputWeightKg.toFixed(3) }}kg</a-descriptions-item>
              <a-descriptions-item label="成品重量">{{ Number(form.finishedOutputWeightKg ?? 0).toFixed(3) }}kg</a-descriptions-item>
              <a-descriptions-item label="成品数量">{{ pricingPreview.referenceQuantity }}{{ form.finishedOutputUnit }}</a-descriptions-item>
              <a-descriptions-item :label="`平均每${form.finishedOutputUnit}重量`">{{ pricingPreview.averageUnitWeightKg.toFixed(3) }}kg</a-descriptions-item>
              <a-descriptions-item label="得率基准">{{ pricingPreview.yieldBasisWeightKg.toFixed(3) }}kg</a-descriptions-item>
              <a-descriptions-item label="研发参考得率">{{ pricingPreview.yieldPercent.toFixed(2) }}%</a-descriptions-item>
            </a-descriptions>
          </a-card>

          <a-card title="照片附件" class="page-card">
            <input v-if="!readOnly" type="file" accept="image/*" @change="onFileChange" />
            <p v-else style="color: #64748b; margin: 0">照片由研发人员在打样现场上传。</p>
            <p v-if="uploadHint" style="margin-top: 8px; color: #64748b">{{ uploadHint }}</p>
          </a-card>
        </a-col>

        <a-col :span="8">
          <a-card title="打样状态" class="page-card workflow-side-card">
            <a-tag color="green">打样中</a-tag>
            <a-tag v-if="draftStatusLabel" color="blue" style="margin-left: 8px">{{ draftStatusLabel }}</a-tag>
            <a-descriptions :column="1" size="small" style="margin-top: 12px">
              <a-descriptions-item label="样品版本">{{ detail?.task.versionCode }}</a-descriptions-item>
              <a-descriptions-item label="录入人">{{ auth.displayName }}</a-descriptions-item>
            </a-descriptions>
            <a-divider />
            <a-typography-text type="secondary">
              {{ readOnly ? "确认研发草稿后，内勤可通知内部测试。" : "先保存草稿，再提交打样记录通知内部测试。" }}
            </a-typography-text>
            <a-space direction="vertical" style="width: 100%; margin-top: 16px">
              <a-button
                v-if="detail?.currentExperimentForm?.id"
                block
                :loading="exporting"
                @click="exportExperimentForm"
              >
                导出实验单 Excel
              </a-button>
              <a-button v-if="canSaveDraft" block :loading="saving" @click="saveDraft">① 保存打样草稿</a-button>
              <a-button
                v-if="canNotifyTest"
                type="primary"
                block
                :loading="submitting"
                @click="submitSamplingRecord"
              >
                {{ auth.role === "RND_ASSISTANT" ? "② 通知内部测试" : "② 提交打样记录" }}
              </a-button>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type {
  ExperimentMaterial,
  ExperimentProcessStep,
  MaterialCategory,
  RemainingDisposition,
  RndTaskDetailView,
  YieldCalculationMode,
  ProcessPlanDraft,
} from "@rnd/shared";
import { aggregateProcessRecipe, calculatePricingPreview, canEditExperiment, canNotifyInternalTest, clearExperimentDraft, createEmptyProcessPlan, experimentDraftKey, formulaRatios, isCachedDraftNewer, nextProcessKey, normalizePositiveIntegerQuantity, normalizeProcessPlan, processPlanToLegacySteps, readExperimentDraft, writeExperimentDraft, yieldBasisWeightKg } from "@rnd/shared";
import ProcessTabsEditor from "../../components/ProcessTabsEditor.vue";
import ProcessPlanWorkspace from "../../components/process/ProcessPlanWorkspace.vue";
import { RequestGeneration } from "../../components/process/requestGeneration";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

type MaterialRow = {
  key: number;
  materialCategory: MaterialCategory;
  primaryMaterial: boolean;
  materialCode: string;
  materialName: string;
  weightKg: number | null;
  inputUnit: string;
  utilizationRate: number;
  remark: string;
};

type ProcessRow = {
  key: number;
  processName: string;
  beforeWeightKg: number | null;
  afterWeightKg: number | null;
  remainingWeightKg: number | null;
  remainingDisposition: RemainingDisposition;
  remark: string;
};

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const exporting = ref(false);
const draftSaved = ref(false);
const draftSyncState = ref<"idle" | "local" | "syncing" | "saved" | "error">("idle");
const uploadHint = ref("");
const experimentPhase = ref<"arrange" | "form">("form");
const yieldCalculationMode = ref<YieldCalculationMode>("SELECTED_PRIMARY_MATERIALS");
const hydrated = ref(false);
let autoSaveTimer: number | undefined;
let suppressAutoSave = false;
const requestGeneration = new RequestGeneration();
const activeTaskId = ref("");
const detail = ref<RndTaskDetailView | null>(null);
const headerTitle = ref("打样实验单");
let rowKey = 1;

const canSaveDraft = computed(() =>
  detail.value ? canEditExperiment(detail.value, auth.displayName, auth.role) : false,
);
const canNotifyTest = computed(() =>
  detail.value ? canNotifyInternalTest(detail.value, auth.displayName, auth.role) : false,
);
const readOnly = computed(() => !canSaveDraft.value);

const form = reactive({
  productName: "",
  specification: "",
  summary: "",
  finishedOutputWeightKg: null as number | null,
  finishedOutputQuantity: null as number | null,
  finishedOutputUnit: "袋" as "袋" | "盒" | "份" | "个" | "盘",
  remark: "",
});

const materials = ref<MaterialRow[]>([blankMaterial(false)]);
const processSteps = ref<ProcessRow[]>([blankProcess()]);
const processPlan = ref<ProcessPlanDraft>(createEmptyProcessPlan());
const serverProcessHydrationToken = ref(0);
const processWorkspace = ref<{ flushSave: (silent?: boolean) => Promise<void>; restoreLocalDirty: (plan: ProcessPlanDraft) => void }>();
const showLegacyProcessEditor = computed(() => processPlan.value.legacy && !processPlan.value.majorProcesses.some((item) => item.steps.length));
const processRecipe = computed(() => aggregateProcessRecipe(processPlan.value));
const hasProcessPlanData = computed(() => !processPlan.value.legacy && processRecipe.value.length > 0);
const effectiveMaterials = computed<MaterialRow[]>(() => hasProcessPlanData.value ? processRecipe.value.flatMap((item, index) => {
  const grouped = new Map<string, number>();
  item.sources.forEach(source => grouped.set(source.materialRole, (grouped.get(source.materialRole) || 0) + source.weightKg));
  return [...grouped].map(([role, weight], roleIndex) => ({ key:index * 10 + roleIndex + 1, materialCategory:role === "PRIMARY" ? "RAW" : "AUXILIARY", primaryMaterial:role === "PRIMARY", materialCode:item.materialCode || "", materialName:item.materialName, weightKg:weight, inputUnit:"kg", utilizationRate:100, remark:"工艺方案自动汇总" }));
}) : materials.value);
const formulaRatioValues = computed(() => formulaRatios(effectiveMaterials.value.map((item) => item.weightKg ?? 0)));
const totalFormulaWeight = computed(() => effectiveMaterials.value.reduce((sum, item) => sum + (item.weightKg ?? 0), 0));
const yieldBasisWeight = computed(() => yieldBasisWeightKg(effectiveMaterials.value.map((item) => ({
  weightKg: item.weightKg,
  utilizationRatePercent: item.utilizationRate,
  materialCategory: item.materialCategory,
  primaryMaterial: item.primaryMaterial,
})), yieldCalculationMode.value));
const pricingPreview = computed(() => calculatePricingPreview({
  totalInputWeightKg: totalFormulaWeight.value,
  yieldBasisWeightKg: yieldBasisWeight.value,
  finishedOutputWeightKg: form.finishedOutputWeightKg,
  finishedOutputQuantity: form.finishedOutputQuantity,
}));
const draftStatusLabel = computed(() => ({ idle: "", local: "已本地保存", syncing: "正在自动保存", saved: "已自动保存", error: "网络异常，已本地保存" }[draftSyncState.value]));
const localDraftKey = computed(() => experimentDraftKey(auth.principal?.userId || auth.user?.id || auth.displayName, activeTaskId.value));
const pricingPreviewMaterials = computed(() => effectiveMaterials.value.map((item, index) => ({
  key: item.key,
  role: `${categoryLabel(item.materialCategory)}${item.primaryMaterial ? " · 主料" : ""}`,
  materialName: item.materialName || "未命名",
  weightKg: `${(item.weightKg ?? 0).toFixed(3)}kg`,
  ratio: `${formulaRatioValues.value[index]?.toFixed(2) ?? "0.00"}%`,
})));
const outputUnitOptions = ["袋", "盒", "份", "个", "盘"].map((value) => ({ label: value, value }));
const materialCategoryOptions = [
  { label: "原料", value: "RAW" },
  { label: "辅料", value: "AUXILIARY" },
  { label: "包材", value: "PACKAGING" },
];

const materialColumns = [
  { title: "类型", key: "role", width: 130 },
  { title: "物料", key: "materialName" },
  { title: "重量", key: "weightKg", width: 170 },
  { title: "比例", key: "ratio", width: 100 },
  { title: "利用率", key: "utilizationRate", width: 150 },
  { title: "操作", key: "action", width: 80 },
];
const previewMaterialColumns = [
  { title: "类型", dataIndex: "role", key: "role", width: 100 },
  { title: "物料", dataIndex: "materialName", key: "materialName" },
  { title: "投入", dataIndex: "weightKg", key: "weightKg", width: 140 },
  { title: "比例", dataIndex: "ratio", key: "ratio", width: 120 },
];

const processColumns = [
  { title: "工序", key: "processName" },
  { title: "前重kg", key: "beforeWeightKg" },
  { title: "后重kg", key: "afterWeightKg" },
  { title: "损耗率", key: "lossRate" },
  { title: "备注", key: "remark" },
  { title: "操作", key: "action", width: 80 },
];

function blankMaterial(primaryMaterial = false): MaterialRow {
  return {
    key: rowKey++,
    materialCategory: primaryMaterial ? "RAW" : "AUXILIARY",
    primaryMaterial,
    materialCode: "",
    materialName: "",
    weightKg: null,
    inputUnit: "kg",
    utilizationRate: 100,
    remark: "",
  };
}

function blankProcess(): ProcessRow {
  return {
    key: rowKey++,
    processName: "",
    beforeWeightKg: null,
    afterWeightKg: null,
    remainingWeightKg: null,
    remainingDisposition: "REUSE",
    remark: "",
  };
}

function addProcess() {
  processSteps.value.push(blankProcess());
}

function addMaterial() {
  materials.value.push(blankMaterial());
}

function removeMaterial(index: number) {
  if (materials.value.length <= 1) return;
  materials.value.splice(index, 1);
}

function formulaRatioAt(index: number) {
  return formulaRatioValues.value[index] ?? 0;
}

function removeProcess(index: number) {
  processSteps.value.splice(index, 1);
  if (processSteps.value.length === 0) addProcess();
}

function moveProcess(fromIndex: number, toIndex: number) {
  if (fromIndex === toIndex || toIndex < 0 || toIndex >= processSteps.value.length) return;
  const next = [...processSteps.value];
  const [step] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, step);
  processSteps.value = next;
}

function copyProcess(index: number) {
  const source = processSteps.value[index];
  if (!source) return;
  processSteps.value.splice(index + 1, 0, {
    ...source,
    key: rowKey++,
    processName: `${source.processName}（副本）`,
  });
}

function startArrangeMode() {
  experimentPhase.value = "arrange";
}

function confirmArrangement() {
  const valid = processSteps.value.filter((step) => step.processName.trim());
  if (!valid.length) {
    message.warning("请至少填写一个工序");
    return;
  }
  processSteps.value = valid;
  if (!processPlan.value.majorProcesses.length) {
    processPlan.value = normalizeProcessPlan({
      ...createEmptyProcessPlan(),
      majorProcesses: valid.map((step, index) => ({
        key: nextProcessKey("major"), sequence: index + 1, processCode: "", processName: step.processName,
        description: step.remark, yieldBasis: "PRIMARY_INPUT" as const, remark: step.remark,
        steps: [],
        inputs: step.beforeWeightKg ? [{ key: nextProcessKey("input"), sequence: 1, inputRole: "PRIMARY" as const, materialName: "本步投入", weightKg: step.beforeWeightKg }] : [],
        outputs: step.afterWeightKg ? [{ key: nextProcessKey("output"), sequence: 1, outputType: "QUALIFIED" as const, weightKg: step.afterWeightKg, remark: "" }] : [],
      })),
    });
  }
  experimentPhase.value = "form";
}

function categoryLabel(category: MaterialCategory) {
  if (category === "AUXILIARY") return "辅料";
  if (category === "PACKAGING") return "包材";
  return "原料";
}

function categoryFromStage(stage?: string): MaterialCategory {
  if (stage === "辅料" || stage === "AUXILIARY") return "AUXILIARY";
  if (stage === "包材" || stage === "PACKAGING") return "PACKAGING";
  return "RAW";
}

function buildMaterials(): ExperimentMaterial[] {
  return effectiveMaterials.value
    .filter((item) => item.materialName.trim())
    .map((item, index) => ({
      stage: categoryLabel(item.materialCategory),
      sequence: index + 1,
      materialCode: item.materialCode.trim() || undefined,
      materialName: item.materialName.trim(),
      weightKg: item.weightKg ?? 0,
      utilizationRate: item.utilizationRate / 100,
      materialCategory: item.materialCategory,
      primaryMaterial: item.materialCategory === "RAW" && item.primaryMaterial,
      inputUnit: item.inputUnit || "kg",
      remark: item.remark.trim() || undefined,
    }));
}

function buildProcessSteps(): ExperimentProcessStep[] {
  if (processPlan.value.majorProcesses.length) return processPlanToLegacySteps(processPlan.value);
  return processSteps.value
    .filter((step) => step.processName.trim())
    .map((step, index) => {
      const before = step.beforeWeightKg ?? 0;
      const after = step.afterWeightKg ?? 0;
      return {
        sequence: index + 1,
        processName: step.processName.trim(),
        beforeWeightKg: before || undefined,
        afterWeightKg: after || undefined,
        remainingWeightKg: step.remainingWeightKg ?? undefined,
        remainingDisposition: step.remainingDisposition,
        remark: step.remark.trim() || undefined,
      };
    });
}

function buildSummary() {
  return [
    form.summary,
    form.finishedOutputWeightKg != null ? `成品实际产出：${form.finishedOutputWeightKg}kg` : "",
    form.remark ? `备注：${form.remark}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

onMounted(() => window.addEventListener("pagehide", persistLocalDraft));

watch(() => route.params.id, taskId => {
  if (hydrated.value) persistLocalDraft();
  const generation = requestGeneration.next();
  void resetAndLoad(String(taskId), generation);
}, { immediate: true });

async function resetAndLoad(taskId: string, generation: number) {
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer);
  autoSaveTimer = undefined;
  suppressAutoSave = true;
  hydrated.value = false;
  loading.value = true;
  saving.value = false;
  submitting.value = false;
  exporting.value = false;
  draftSaved.value = false;
  draftSyncState.value = "idle";
  uploadHint.value = "";
  experimentPhase.value = "form";
  yieldCalculationMode.value = "SELECTED_PRIMARY_MATERIALS";
  activeTaskId.value = taskId;
  detail.value = null;
  headerTitle.value = "打样实验单";
  rowKey = 1;
  Object.assign(form, { productName: "", specification: "", summary: "", finishedOutputWeightKg: null, finishedOutputQuantity: null, finishedOutputUnit: "袋", remark: "" });
  materials.value = [blankMaterial(false)];
  processSteps.value = [blankProcess()];
  processPlan.value = createEmptyProcessPlan();
  try {
    const loadedDetail = await api.task.detail(taskId, auth.role, auth.displayName);
    if (!requestGeneration.isCurrent(generation)) return;
    detail.value = loadedDetail;
    form.productName = loadedDetail.task.productName;
    form.specification = loadedDetail.version.specification ?? loadedDetail.project?.specification ?? "";
    headerTitle.value = `${loadedDetail.task.productName} ${loadedDetail.task.versionCode}`;
    form.summary = loadedDetail.currentExperimentForm?.summary || "";
    form.finishedOutputWeightKg = loadedDetail.currentExperimentForm?.finishedOutputWeightKg ?? null;
    form.finishedOutputQuantity = loadedDetail.currentExperimentForm?.finishedOutputQuantity ?? null;
    form.finishedOutputUnit = (loadedDetail.currentExperimentForm?.finishedOutputUnit as typeof form.finishedOutputUnit) || "袋";
    draftSaved.value = Boolean(loadedDetail.currentExperimentForm?.summary);
    yieldCalculationMode.value = loadedDetail.currentExperimentForm?.yieldCalculationMode
      || (/酱汁|复合调味/.test(loadedDetail.project?.productType || "") ? "TOTAL_PICKING_WEIGHT" : "SELECTED_PRIMARY_MATERIALS");
    if (loadedDetail.currentExperimentForm?.materials?.length) {
      materials.value = loadedDetail.currentExperimentForm.materials.map(item => ({
        key: rowKey++, materialCategory: item.materialCategory ?? categoryFromStage(item.stage),
        primaryMaterial: item.primaryMaterial ?? false, materialCode: item.materialCode || "",
        materialName: item.materialName, weightKg: item.weightKg ?? null, inputUnit: item.inputUnit || "kg",
        utilizationRate: item.utilizationRate != null ? item.utilizationRate * 100 : 100, remark: item.remark || "",
      }));
    }
    if (loadedDetail.currentExperimentForm?.processSteps?.length) {
      processSteps.value = loadedDetail.currentExperimentForm.processSteps.map(step => ({ key: rowKey++, processName: step.processName, beforeWeightKg: step.beforeWeightKg ?? null, afterWeightKg: step.afterWeightKg ?? null, remainingWeightKg: step.remainingWeightKg ?? null, remainingDisposition: step.remainingDisposition ?? "REUSE", remark: step.remark || "" }));
    } else if (canEditExperiment(loadedDetail, auth.displayName, auth.role)) {
      const steps = await api.sample.processSteps(loadedDetail.task.versionId);
      if (!requestGeneration.isCurrent(generation)) return;
      if (steps.length) processSteps.value = steps.map(step => ({ key: rowKey++, processName: step.processName, beforeWeightKg: step.beforeWeightKg ?? null, afterWeightKg: step.afterWeightKg ?? null, remainingWeightKg: step.remainingWeightKg ?? null, remainingDisposition: step.remainingDisposition ?? "REUSE", remark: step.remark || "" }));
    }
    const formId = loadedDetail.currentExperimentForm?.id;
    if (formId) {
      try {
        const loadedPlan = await api.task.getProcessPlan(formId);
        if (!requestGeneration.isCurrent(generation)) return;
        processPlan.value = normalizeProcessPlan(loadedPlan);
      } catch (error) {
        if (!requestGeneration.isCurrent(generation)) return;
        processPlan.value = createEmptyProcessPlan();
      }
      serverProcessHydrationToken.value++;
      await nextTick();
    }
    if (!requestGeneration.isCurrent(generation)) return;
    restoreLocalDraft(loadedDetail.currentExperimentForm?.savedAt);
  } catch (error) {
    if (requestGeneration.isCurrent(generation)) message.error(error instanceof Error ? error.message : "无法加载打样实验单");
  } finally {
    if (requestGeneration.isCurrent(generation)) {
      loading.value = false;
      hydrated.value = true;
      suppressAutoSave = false;
    }
  }
}

onBeforeUnmount(() => {
  requestGeneration.invalidate();
  persistLocalDraft();
  window.removeEventListener("pagehide", persistLocalDraft);
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer);
});

function validateFinishedOutputQuantity() {
  const quantity = normalizePositiveIntegerQuantity(form.finishedOutputQuantity);
  if (form.finishedOutputQuantity !== null && quantity === undefined) {
    message.warning("成品数量必须为正整数");
    return undefined;
  }
  return quantity;
}

function draftSnapshot() {
  return {
    experimentPhase: experimentPhase.value,
    yieldCalculationMode: yieldCalculationMode.value,
    form: { ...form },
    materials: materials.value.map((item) => ({ ...item })),
    processSteps: processSteps.value.map((item) => ({ ...item })),
    processPlan: structuredClone(processPlan.value),
  };
}

function persistLocalDraft() {
  if (!hydrated.value || readOnly.value) return;
  writeExperimentDraft(localStorage, localDraftKey.value, draftSnapshot());
  if (!["syncing", "error"].includes(draftSyncState.value)) draftSyncState.value = "local";
}

function restoreLocalDraft(serverSavedAt?: string) {
  if (readOnly.value) {
    clearExperimentDraft(localStorage, localDraftKey.value);
    return;
  }
  const cached = readExperimentDraft<ReturnType<typeof draftSnapshot>>(localStorage, localDraftKey.value);
  if (!cached || !isCachedDraftNewer(cached.savedAt, serverSavedAt)) return;
  experimentPhase.value = cached.value.experimentPhase;
  yieldCalculationMode.value = cached.value.yieldCalculationMode;
  Object.assign(form, cached.value.form);
  materials.value = cached.value.materials;
  processSteps.value = cached.value.processSteps;
  if (cached.value.processPlan) {
    processPlan.value = normalizeProcessPlan(cached.value.processPlan);
    processWorkspace.value?.restoreLocalDirty(processPlan.value);
  }
  draftSyncState.value = "local";
}

function scheduleAutoSave() {
  if (!hydrated.value || readOnly.value || suppressAutoSave) return;
  persistLocalDraft();
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer);
  autoSaveTimer = window.setTimeout(() => void autoSave(), 1500);
}

async function autoSave() {
  if (!canSaveDraft.value) return;
  await saveDraft({ silent: true });
}

watch([form, materials, processSteps, yieldCalculationMode, experimentPhase], scheduleAutoSave, { deep: true });

async function saveDraft(options: { silent?: boolean } = {}) {
  const generation = requestGeneration.capture();
  const taskId = activeTaskId.value;
  const finishedOutputQuantity = validateFinishedOutputQuantity();
  if (form.finishedOutputQuantity !== null && finishedOutputQuantity === undefined) return;
  saving.value = true;
  draftSyncState.value = "syncing";
  try {
    const saved = await api.task.saveExperimentDraft(taskId, {
      operatorName: auth.displayName,
      summary: buildSummary(),
      materials: buildMaterials(),
      processSteps: buildProcessSteps(),
      finishedOutputWeightKg: form.finishedOutputWeightKg ?? undefined,
      finishedOutputQuantity,
      finishedOutputUnit: form.finishedOutputUnit,
      yieldCalculationMode: yieldCalculationMode.value,
    });
    if (!requestGeneration.isCurrent(generation) || taskId !== activeTaskId.value) return false;
    detail.value = { ...detail.value!, currentExperimentForm: saved };
    if (processPlan.value.majorProcesses.length) {
      await nextTick();
      if (!requestGeneration.isCurrent(generation)) return false;
      await processWorkspace.value?.flushSave(true);
      if (!requestGeneration.isCurrent(generation)) return false;
    }
    draftSaved.value = true;
    draftSyncState.value = "saved";
    writeExperimentDraft(localStorage, localDraftKey.value, draftSnapshot(), saved.savedAt);
    if (!options.silent) message.success("草稿已保存");
    return true;
  } catch (error) {
    if (!requestGeneration.isCurrent(generation)) return false;
    draftSyncState.value = "error";
    persistLocalDraft();
    if (!options.silent) message.error(error instanceof Error ? error.message : "保存失败");
    return false;
  } finally {
    if (requestGeneration.isCurrent(generation)) saving.value = false;
  }
}

async function onFileChange(event: Event) {
  const generation = requestGeneration.capture();
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!detail.value?.currentExperimentForm?.id) {
    await saveDraft();
  }
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先保存草稿");
    return;
  }
  try {
    await api.task.uploadAttachment(experimentId, file, {
      uploadedBy: auth.displayName,
      category: "PHOTO",
    });
    if (!requestGeneration.isCurrent(generation)) return;
    uploadHint.value = `已上传：${file.name}`;
    message.success("照片已上传");
  } catch (error) {
    if (!requestGeneration.isCurrent(generation)) return;
    message.error(error instanceof Error ? error.message : "上传失败");
  }
}

async function submitSamplingRecord() {
  const generation = requestGeneration.capture();
  if (yieldBasisWeight.value <= 0) {
    message.warning(yieldCalculationMode.value === "SELECTED_PRIMARY_MATERIALS" ? "请至少选择一项有重量的主料" : "请填写非包材物料重量");
    return;
  }
  if (!buildProcessSteps().some((step) => step.processName.trim() && (step.beforeWeightKg ?? 0) > 0)) {
    message.warning("通知测试前请至少完成一道有效工序");
    return;
  }
  if ((form.finishedOutputWeightKg ?? 0) <= 0) {
    message.warning("通知测试前请填写成品重量");
    return;
  }
  if (form.finishedOutputQuantity === null) {
    message.warning("通知测试前请填写成品数量");
    return;
  }
  if (validateFinishedOutputQuantity() === undefined) return;
  if (canSaveDraft.value && !detail.value?.currentExperimentForm?.id) {
    await saveDraft();
    if (!requestGeneration.isCurrent(generation)) return;
  }
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先保存打样草稿");
    return;
  }
  submitting.value = true;
  try {
    await api.task.submitExperimentForTest(experimentId, auth.displayName);
    if (!requestGeneration.isCurrent(generation)) return;
    hydrated.value = false;
    clearExperimentDraft(localStorage, localDraftKey.value);
    message.success("打样记录已提交，已通知内部测试");
    router.push(`/rnd/tasks/${route.params.id}/test`);
  } catch (error) {
    if (!requestGeneration.isCurrent(generation)) return;
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    if (requestGeneration.isCurrent(generation)) submitting.value = false;
  }
}

async function exportExperimentForm() {
  const generation = requestGeneration.capture();
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先保存实验单草稿");
    return;
  }
  exporting.value = true;
  try {
    const blob = await api.report.exportExperimentForm(experimentId);
    if (!requestGeneration.isCurrent(generation)) return;
    download(blob, `${detail.value?.task.productName ?? "实验单"}-${detail.value?.task.versionCode ?? ""}-打样实验单.xlsx`);
    message.success("实验单已导出");
  } catch (error) {
    if (!requestGeneration.isCurrent(generation)) return;
    message.error(error instanceof Error ? error.message : "导出失败");
  } finally {
    if (requestGeneration.isCurrent(generation)) exporting.value = false;
  }
}

function download(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.mode-chooser-card{max-width:920px;margin:24px auto}.mode-choice{width:100%;min-height:180px;padding:24px;text-align:left;background:#fff;border:1px solid #dbe3ef;border-radius:8px}.mode-choice--recommended{border:2px solid #246bfe;background:#f7faff}.mode-choice span,.mode-choice strong,.mode-choice small{display:block}.mode-choice span{margin-bottom:14px;color:#246bfe;font-size:12px;font-weight:700}.mode-choice strong{margin-bottom:8px;font-size:22px}.mode-choice small{color:#64748b;font-size:14px;line-height:1.7}
</style>
