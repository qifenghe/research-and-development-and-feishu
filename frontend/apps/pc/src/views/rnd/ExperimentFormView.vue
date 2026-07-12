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
      <a-card v-if="experimentPhase === 'mode'" title="选择本次打样方式" class="page-card mode-chooser-card">
        <a-row :gutter="16">
          <a-col :span="12">
            <button class="mode-choice mode-choice--recommended" @click="startQuickMode">
              <span>推荐 · 操作最少</span>
              <strong>快速打样</strong>
              <small>使用系统推荐工序，直接填写本次实际数据。</small>
            </button>
          </a-col>
          <a-col :span="12">
            <button class="mode-choice" @click="startArrangeMode">
              <span>灵活 · 先确认工艺</span>
              <strong>编排后打样</strong>
              <small>先调整工序顺序和内容，确认后开始打样。</small>
            </button>
          </a-col>
        </a-row>
      </a-card>

      <a-card v-else-if="experimentPhase === 'arrange'" title="编排本次打样工序" class="page-card">
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
          <a-button @click="experimentPhase='mode'">返回</a-button>
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

          <a-card title="配方" class="page-card">
            <a-table :columns="materialColumns" :data-source="materials" row-key="key" :pagination="false" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'role'">
                  <a-tag :color="record.primaryMaterial ? 'blue' : 'green'">{{ record.primaryMaterial ? "主料" : "配料" }}</a-tag>
                  <a-button v-if="!readOnly && !record.primaryMaterial" type="link" size="small" @click="setPrimaryMaterial(index)">设为主料</a-button>
                </template>
                <template v-else-if="column.key === 'materialName'"><a-input v-model:value="record.materialName" :disabled="readOnly" placeholder="物料名称" /></template>
                <template v-else-if="column.key === 'weightKg'"><a-input-number v-model:value="record.weightKg" :disabled="readOnly" :min="0" :precision="4" addon-after="kg" style="width:100%" /></template>
                <template v-else-if="column.key === 'ratio'">{{ formulaRatioAt(index).toFixed(2) }}%</template>
                <template v-else-if="column.key === 'utilizationRate'"><a-input-number v-model:value="record.utilizationRate" :disabled="readOnly" :min="0" :max="100" addon-after="%" style="width:100%" /></template>
                <template v-else-if="column.key === 'action'"><a-button v-if="!readOnly && !record.primaryMaterial" type="link" danger @click="removeMaterial(index)">删除</a-button></template>
              </template>
            </a-table>
            <a-button v-if="!readOnly" type="dashed" block style="margin-top:12px" @click="addMaterial">+ 添加配料</a-button>
          </a-card>

          <a-card title="关键工序" class="page-card">
            <ProcessTabsEditor v-model="processSteps" :readonly="readOnly" :create-row="blankProcess" />
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
              <a-descriptions-item label="主料得率">{{ pricingPreview.primaryMaterialYieldPercent.toFixed(2) }}%</a-descriptions-item>
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
            <a-tag v-if="draftSaved" color="blue" style="margin-left: 8px">草稿已保存</a-tag>
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
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type {
  ExperimentMaterial,
  ExperimentProcessStep,
  MaterialCategory,
  RemainingDisposition,
  RndTaskDetailView,
} from "@rnd/shared";
import { calculatePricingPreview, canEditExperiment, canNotifyInternalTest, formulaRatios, normalizePositiveIntegerQuantity } from "@rnd/shared";
import ProcessTabsEditor from "../../components/ProcessTabsEditor.vue";
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
const uploadHint = ref("");
const experimentPhase = ref<"mode" | "arrange" | "form">("mode");
const samplingMode = ref<"quick" | "arrange">("quick");
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

const materials = ref<MaterialRow[]>([blankMaterial(true)]);
const processSteps = ref<ProcessRow[]>([blankProcess()]);
const primaryInputWeight = computed(() => materials.value.find((item) => item.primaryMaterial)?.weightKg ?? 0);
const ingredientWeight = computed(() => materials.value
  .filter((item) => !item.primaryMaterial)
  .reduce((sum, item) => sum + (item.weightKg ?? 0), 0));
const formulaRatioValues = computed(() => formulaRatios(materials.value.map((item) => item.weightKg ?? 0)));
const pricingPreview = computed(() => calculatePricingPreview({
  primaryMaterialWeightKg: primaryInputWeight.value,
  ingredientWeightKg: ingredientWeight.value,
  finishedOutputWeightKg: form.finishedOutputWeightKg,
  finishedOutputQuantity: form.finishedOutputQuantity,
}));
const pricingPreviewMaterials = computed(() => materials.value.map((item, index) => ({
  key: item.key,
  role: item.primaryMaterial ? "主料" : "配料",
  materialName: item.materialName || "未命名",
  weightKg: `${(item.weightKg ?? 0).toFixed(3)}kg`,
  ratio: `${formulaRatioValues.value[index]?.toFixed(2) ?? "0.00"}%`,
})));
const outputUnitOptions = ["袋", "盒", "份", "个", "盘"].map((value) => ({ label: value, value }));

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
  if (materials.value[index]?.primaryMaterial) return;
  materials.value.splice(index, 1);
}

function setPrimaryMaterial(index: number) {
  if (readOnly.value || !materials.value[index]) return;
  materials.value = materials.value.map((item, itemIndex) => ({
    ...item,
    materialCategory: itemIndex === index ? "RAW" : "AUXILIARY",
    primaryMaterial: itemIndex === index,
  }));
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

function startQuickMode() {
  samplingMode.value = "quick";
  experimentPhase.value = "form";
}

function startArrangeMode() {
  samplingMode.value = "arrange";
  experimentPhase.value = "arrange";
}

function confirmArrangement() {
  const valid = processSteps.value.filter((step) => step.processName.trim());
  if (!valid.length) {
    message.warning("请至少填写一个工序");
    return;
  }
  processSteps.value = valid;
  experimentPhase.value = "form";
}

function categoryLabel(category: MaterialCategory) {
  if (category === "AUXILIARY") return "辅料";
  return "原料";
}

function categoryFromStage(stage?: string): MaterialCategory {
  if (stage === "辅料" || stage === "AUXILIARY") return "AUXILIARY";
  if (stage === "包材" || stage === "PACKAGING") return "PACKAGING";
  return "RAW";
}

function buildMaterials(): ExperimentMaterial[] {
  return materials.value
    .filter((item) => item.materialName.trim())
    .map((item, index) => ({
      stage: categoryLabel(item.materialCategory),
      sequence: index + 1,
      materialCode: item.materialCode.trim() || undefined,
      materialName: item.materialName.trim(),
      weightKg: item.weightKg ?? 0,
      utilizationRate: item.utilizationRate / 100,
      materialCategory: item.primaryMaterial ? "RAW" : "AUXILIARY",
      primaryMaterial: item.primaryMaterial,
      inputUnit: item.inputUnit || "kg",
      remark: item.remark.trim() || undefined,
    }));
}

function buildProcessSteps(): ExperimentProcessStep[] {
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

onMounted(async () => {
  loading.value = true;
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
    form.productName = detail.value.task.productName;
    form.specification = detail.value.version.specification ?? detail.value.project?.specification ?? "";
    headerTitle.value = `${detail.value.task.productName} ${detail.value.task.versionCode}`;
    if (detail.value.currentExperimentForm?.summary) {
      form.summary = detail.value.currentExperimentForm.summary;
      draftSaved.value = true;
    }
  if (detail.value.currentExperimentForm?.finishedOutputWeightKg != null) {
    form.finishedOutputWeightKg = detail.value.currentExperimentForm.finishedOutputWeightKg;
  }
    if (detail.value.currentExperimentForm?.finishedOutputQuantity != null) {
      form.finishedOutputQuantity = detail.value.currentExperimentForm.finishedOutputQuantity;
    }
    form.finishedOutputUnit = (detail.value.currentExperimentForm?.finishedOutputUnit as typeof form.finishedOutputUnit) || "袋";
    if (detail.value.currentExperimentForm || readOnly.value) {
      experimentPhase.value = "form";
    }
    if (detail.value.currentExperimentForm?.materials?.length) {
      const savedMaterials = detail.value.currentExperimentForm.materials
        .filter((item) => (item.materialCategory ?? categoryFromStage(item.stage)) !== "PACKAGING")
        .map((item) => ({
        key: rowKey++,
        materialCategory: item.materialCategory ?? categoryFromStage(item.stage),
        primaryMaterial: item.primaryMaterial ?? false,
        materialCode: item.materialCode || "",
        materialName: item.materialName,
        weightKg: item.weightKg ?? null,
        inputUnit: item.inputUnit || "kg",
        utilizationRate: item.utilizationRate != null ? item.utilizationRate * 100 : 100,
        remark: item.remark || "",
        }));
      const primaryIndex = savedMaterials.findIndex((item) => item.primaryMaterial);
      materials.value = savedMaterials.length
        ? savedMaterials.map((item, index) => ({
          ...item,
          materialCategory: index === (primaryIndex >= 0 ? primaryIndex : 0) ? "RAW" : "AUXILIARY",
          primaryMaterial: index === (primaryIndex >= 0 ? primaryIndex : 0),
        }))
        : [blankMaterial(true)];
    }
    if (detail.value.currentExperimentForm?.processSteps?.length) {
      processSteps.value = detail.value.currentExperimentForm.processSteps.map((step) => ({
        key: rowKey++,
        processName: step.processName,
        beforeWeightKg: step.beforeWeightKg ?? null,
        afterWeightKg: step.afterWeightKg ?? null,
        remainingWeightKg: step.remainingWeightKg ?? null,
        remainingDisposition: step.remainingDisposition ?? "REUSE",
        remark: step.remark || "",
      }));
    } else if (canEditExperiment(detail.value, auth.displayName, auth.role)) {
      const steps = await api.sample.processSteps(detail.value.task.versionId);
      if (steps.length) {
        processSteps.value = steps.map((step) => ({
          key: rowKey++,
          processName: step.processName,
          beforeWeightKg: step.beforeWeightKg ?? null,
          afterWeightKg: step.afterWeightKg ?? null,
          remainingWeightKg: step.remainingWeightKg ?? null,
          remainingDisposition: step.remainingDisposition ?? "REUSE",
          remark: step.remark || "",
        }));
      }
    }
  } finally {
    loading.value = false;
  }
});

function validateFinishedOutputQuantity() {
  const quantity = normalizePositiveIntegerQuantity(form.finishedOutputQuantity);
  if (form.finishedOutputQuantity !== null && quantity === undefined) {
    message.warning("成品数量必须为正整数");
    return undefined;
  }
  return quantity;
}

async function saveDraft() {
  const finishedOutputQuantity = validateFinishedOutputQuantity();
  if (form.finishedOutputQuantity !== null && finishedOutputQuantity === undefined) return;
  saving.value = true;
  try {
    const saved = await api.task.saveExperimentDraft(String(route.params.id), {
      operatorName: auth.displayName,
      summary: buildSummary(),
      materials: buildMaterials(),
      processSteps: buildProcessSteps(),
      finishedOutputWeightKg: form.finishedOutputWeightKg ?? undefined,
      finishedOutputQuantity,
      finishedOutputUnit: form.finishedOutputUnit,
    });
    detail.value = { ...detail.value!, currentExperimentForm: saved };
    draftSaved.value = true;
    message.success("草稿已保存");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function onFileChange(event: Event) {
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
    uploadHint.value = `已上传：${file.name}`;
    message.success("照片已上传");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "上传失败");
  }
}

async function submitSamplingRecord() {
  if (primaryInputWeight.value <= 0) {
    message.warning("通知测试前请填写主料重量");
    return;
  }
  if (!processSteps.value.some((step) => step.processName.trim() && (step.beforeWeightKg ?? 0) > 0)) {
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
  }
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先保存打样草稿");
    return;
  }
  submitting.value = true;
  try {
    await api.task.submitExperimentForTest(experimentId, auth.displayName);
    message.success("打样记录已提交，已通知内部测试");
    router.push(`/rnd/tasks/${route.params.id}/test`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function exportExperimentForm() {
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先保存实验单草稿");
    return;
  }
  exporting.value = true;
  try {
    const blob = await api.report.exportExperimentForm(experimentId);
    download(blob, `${detail.value?.task.productName ?? "实验单"}-${detail.value?.task.versionCode ?? ""}-打样实验单.xlsx`);
    message.success("实验单已导出");
  } catch (error) {
    message.error(error instanceof Error ? error.message : "导出失败");
  } finally {
    exporting.value = false;
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
