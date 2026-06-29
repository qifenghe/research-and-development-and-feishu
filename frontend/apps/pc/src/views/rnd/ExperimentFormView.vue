<template>
  <div>
    <a-page-header
      :title="headerTitle"
      sub-title="现场录入原辅料、工序称重、出成和照片附件"
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
      <a-row :gutter="16">
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

          <a-card title="原辅料 / 包材" class="page-card">
            <a-table :columns="materialColumns" :data-source="materials" row-key="key" :pagination="false" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'stage'"><a-input v-model:value="record.stage" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'materialCode'"><a-input v-model:value="record.materialCode" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'materialName'"><a-input v-model:value="record.materialName" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'weightKg'"><a-input-number v-model:value="record.weightKg" :min="0" style="width: 100%" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'utilizationRate'"><a-input-number v-model:value="record.utilizationRate" :min="0" :max="100" style="width: 100%" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'remark'"><a-input v-model:value="record.remark" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'action'"><a-button type="link" danger :disabled="readOnly" @click="removeMaterial(index)">删除</a-button></template>
              </template>
            </a-table>
            <a-button v-if="!readOnly" type="dashed" block style="margin-top: 12px" @click="addMaterial">+ 添加物料行</a-button>
          </a-card>

          <a-card title="工序记录" class="page-card">
            <a-table :columns="processColumns" :data-source="processSteps" row-key="key" :pagination="false" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'processName'"><a-input v-model:value="record.processName" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'beforeWeightKg'"><a-input-number v-model:value="record.beforeWeightKg" :min="0" style="width: 100%" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'afterWeightKg'"><a-input-number v-model:value="record.afterWeightKg" :min="0" style="width: 100%" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'lossRate'">{{ formatLossRate(record) }}</template>
                <template v-else-if="column.key === 'remark'"><a-input v-model:value="record.remark" :disabled="readOnly" /></template>
                <template v-else-if="column.key === 'action'"><a-button type="link" danger :disabled="readOnly" @click="removeProcess(index)">删除</a-button></template>
              </template>
            </a-table>
            <a-button v-if="!readOnly" type="dashed" block style="margin-top: 12px" @click="addProcess">+ 添加工序行</a-button>
          </a-card>

          <a-card title="成品出成与备注" class="page-card">
            <a-row :gutter="16">
              <a-col :span="8"><a-form-item label="研发参考出成"><a-input-number v-model:value="form.yieldQty" :min="0" style="width: 100%" :disabled="readOnly" /></a-form-item></a-col>
              <a-col :span="8"><a-form-item label="单位"><a-input v-model:value="form.yieldUnit" :disabled="readOnly" /></a-form-item></a-col>
            </a-row>
            <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" :disabled="readOnly" /></a-form-item>
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
import type { ExperimentMaterial, ExperimentProcessStep, RndTaskDetailView } from "@rnd/shared";
import { canEditExperiment, canNotifyInternalTest } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

type MaterialRow = {
  key: number;
  stage: string;
  materialCode: string;
  materialName: string;
  weightKg: number | null;
  utilizationRate: number | null;
  remark: string;
};

type ProcessRow = {
  key: number;
  processName: string;
  beforeWeightKg: number | null;
  afterWeightKg: number | null;
  remark: string;
};

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const draftSaved = ref(false);
const uploadHint = ref("");
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
  yieldQty: null as number | null,
  yieldUnit: "kg",
  remark: "",
});

const materials = ref<MaterialRow[]>([blankMaterial()]);
const processSteps = ref<ProcessRow[]>([blankProcess()]);

const materialColumns = [
  { title: "工段", key: "stage" },
  { title: "物料编码", key: "materialCode" },
  { title: "物料名称", key: "materialName" },
  { title: "用量kg", key: "weightKg" },
  { title: "利用率%", key: "utilizationRate" },
  { title: "备注", key: "remark" },
  { title: "操作", key: "action", width: 80 },
];

const processColumns = [
  { title: "工序", key: "processName" },
  { title: "前重kg", key: "beforeWeightKg" },
  { title: "后重kg", key: "afterWeightKg" },
  { title: "损耗率", key: "lossRate" },
  { title: "备注", key: "remark" },
  { title: "操作", key: "action", width: 80 },
];

function blankMaterial(): MaterialRow {
  return { key: rowKey++, stage: "原料", materialCode: "", materialName: "", weightKg: null, utilizationRate: null, remark: "" };
}

function blankProcess(): ProcessRow {
  return { key: rowKey++, processName: "", beforeWeightKg: null, afterWeightKg: null, remark: "" };
}

function addMaterial() {
  materials.value.push(blankMaterial());
}

function removeMaterial(index: number) {
  materials.value.splice(index, 1);
  if (materials.value.length === 0) addMaterial();
}

function addProcess() {
  processSteps.value.push(blankProcess());
}

function removeProcess(index: number) {
  processSteps.value.splice(index, 1);
  if (processSteps.value.length === 0) addProcess();
}

function formatLossRate(record: ProcessRow) {
  const before = record.beforeWeightKg ?? 0;
  const after = record.afterWeightKg ?? 0;
  if (!before) return "—";
  return `${(((before - after) / before) * 100).toFixed(1)}%`;
}

function buildMaterials(): ExperimentMaterial[] {
  return materials.value
    .filter((item) => item.materialName.trim())
    .map((item, index) => ({
      stage: item.stage.trim() || "原料",
      sequence: index + 1,
      materialCode: item.materialCode.trim() || undefined,
      materialName: item.materialName.trim(),
      weightKg: item.weightKg ?? 0,
      utilizationRate: item.utilizationRate != null ? item.utilizationRate / 100 : undefined,
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
        lossRate: before > 0 ? (before - after) / before : undefined,
        remark: step.remark.trim() || undefined,
      };
    });
}

function buildSummary() {
  return [
    form.summary,
    form.yieldQty != null ? `出成：${form.yieldQty}${form.yieldUnit}` : "",
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
    if (detail.value.currentExperimentForm?.materials?.length) {
      materials.value = detail.value.currentExperimentForm.materials.map((item) => ({
        key: rowKey++,
        stage: item.stage || "原料",
        materialCode: item.materialCode || "",
        materialName: item.materialName,
        weightKg: item.weightKg ?? null,
        utilizationRate: item.utilizationRate != null ? item.utilizationRate * 100 : null,
        remark: item.remark || "",
      }));
    }
    if (detail.value.currentExperimentForm?.processSteps?.length) {
      processSteps.value = detail.value.currentExperimentForm.processSteps.map((step) => ({
        key: rowKey++,
        processName: step.processName,
        beforeWeightKg: step.beforeWeightKg ?? null,
        afterWeightKg: step.afterWeightKg ?? null,
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
          remark: step.remark || "",
        }));
      }
    }
  } finally {
    loading.value = false;
  }
});

async function saveDraft() {
  saving.value = true;
  try {
    const saved = await api.task.saveExperimentDraft(String(route.params.id), {
      operatorName: auth.displayName,
      summary: buildSummary(),
      materials: buildMaterials(),
      processSteps: buildProcessSteps(),
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
</script>
