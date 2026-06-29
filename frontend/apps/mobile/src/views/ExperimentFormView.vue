<template>
  <div class="page-with-footer">
    <van-skeleton title :row="8" :loading="loading">
      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="loadDetail">重试</van-button>
        <van-button round plain style="margin-top: 8px" @click="router.replace('/todo')">返回待办</van-button>
      </van-empty>

      <template v-else>
        <PageHeader :title="headerTitle" compact>
          <StatusBadge label="打样中" variant="success" />
          <StatusBadge v-if="draftSaved" label="草稿已保存" variant="primary" style="margin-left: 8px" />
        </PageHeader>

        <van-notice-bar
          v-if="readOnly"
          class="notice-banner"
          color="#246BFE"
          background="#EAF2FF"
          left-icon="info-o"
          text="当前为只读查看，确认无误后点击下方「通知测试」。"
        />

        <van-collapse v-model="activeSections" class="mobile-form-collapse">
          <van-collapse-item title="基本信息" name="basic">
            <van-field v-model="form.productName" label="产品名称" readonly />
            <van-field v-model="form.specification" label="产品规格" readonly />
            <van-field
              v-model="form.summary"
              rows="2"
              autosize
              type="textarea"
              label="实验摘要"
              placeholder="填写本次打样说明"
              :readonly="readOnly"
            />
          </van-collapse-item>
          <van-collapse-item title="原辅料/包材" name="materials">
            <div v-for="(material, index) in materials" :key="index" class="task-card" style="margin-bottom: 8px">
              <van-field v-model="material.stage" label="工段" placeholder="清洗/卤制/包装" :readonly="readOnly" />
              <van-field v-model="material.materialCode" label="物料编码" placeholder="可选" :readonly="readOnly" />
              <van-field v-model="material.materialName" label="物料名称" placeholder="冻猪大肠头" :readonly="readOnly" />
              <van-field v-model="material.weightKg" label="用量kg" type="number" placeholder="100" :readonly="readOnly" />
              <van-field v-model="material.utilizationRate" label="利用率%" type="number" placeholder="95" :readonly="readOnly" />
              <van-field v-model="material.remark" label="备注" placeholder="可选" :readonly="readOnly" />
            </div>
            <van-button v-if="!readOnly" block plain type="primary" size="small" @click="addMaterial">
              + 添加物料行
            </van-button>
          </van-collapse-item>
          <van-collapse-item title="工序记录" name="process">
            <ProcessStepEditor v-model="processSteps" :readonly="readOnly" />
          </van-collapse-item>
          <van-collapse-item title="成品出成" name="yield">
            <van-field v-model="form.yieldQty" label="研发参考出成" type="number" placeholder="89" :readonly="readOnly" />
            <van-field v-model="form.yieldUnit" label="单位" placeholder="kg" :readonly="readOnly" />
          </van-collapse-item>
          <van-collapse-item title="照片附件" name="photos">
            <van-uploader v-if="!readOnly" v-model="fileList" :after-read="afterRead" />
            <div v-else class="mobile-detail-block">照片由研发人员在打样现场上传。</div>
          </van-collapse-item>
          <van-collapse-item title="备注" name="remark">
            <van-field
              v-model="form.remark"
              rows="3"
              autosize
              type="textarea"
              placeholder="补充现场说明"
              :readonly="readOnly"
            />
          </van-collapse-item>
        </van-collapse>

        <FixedActionBar v-if="canSaveDraft || canNotify" :with-tabbar="false">
          <van-button
            v-if="canSaveDraft"
            block
            plain
            type="primary"
            :loading="saving"
            @click="saveDraft"
          >
            保存草稿
          </van-button>
          <van-button
            v-if="canNotify"
            type="primary"
            block
            :loading="submitting"
            @click="notifyTest"
          >
            通知测试
          </van-button>
        </FixedActionBar>
      </template>
    </van-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { ExperimentMaterial, RndTaskDetailView } from "@rnd/shared";
import { canEditExperiment, canNotifyInternalTest } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import ProcessStepEditor, {
  fromProcessSteps,
  toProcessSteps,
  type EditableProcessStep,
} from "../components/ProcessStepEditor.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const activeSections = ref(["basic"]);
const detail = ref<RndTaskDetailView | null>(null);
const loading = ref(true);
const loadError = ref("");
const saving = ref(false);
const submitting = ref(false);
const draftSaved = ref(false);
const fileList = ref<Array<{ url?: string; file?: File }>>([]);
type EditableMaterial = {
  stage: string;
  materialCode: string;
  materialName: string;
  weightKg: string;
  utilizationRate: string;
  remark: string;
};
const blankMaterial = (): EditableMaterial => ({
  stage: "原料",
  materialCode: "",
  materialName: "",
  weightKg: "",
  utilizationRate: "",
  remark: "",
});
const materials = ref<EditableMaterial[]>([blankMaterial()]);
const processSteps = ref<EditableProcessStep[]>([
  { processName: "", beforeWeightKg: "", afterWeightKg: "", remark: "" },
]);
const form = reactive({
  productName: "",
  specification: "",
  summary: "",
  yieldQty: "",
  yieldUnit: "kg",
  remark: "",
});

const headerTitle = ref("实验单录入");

const canSaveDraft = computed(() =>
  detail.value ? canEditExperiment(detail.value, auth.displayName, auth.role) : false,
);
const canNotify = computed(() =>
  detail.value ? canNotifyInternalTest(detail.value, auth.displayName, auth.role) : false,
);
const readOnly = computed(() => !canSaveDraft.value);

async function ensureAuthReady() {
  if (!auth.principal) {
    await auth.fetchMe().catch(() => undefined);
  }
}

function applyDetail(data: RndTaskDetailView) {
  detail.value = data;
  form.productName = data.task.productName;
  headerTitle.value = `${data.task.productName} ${data.task.versionCode}`;
  form.specification = data.version.specification ?? data.project?.specification ?? "";
  if (data.currentExperimentForm?.summary) {
    form.summary = data.currentExperimentForm.summary;
    draftSaved.value = true;
  }
  if (data.currentExperimentForm?.materials?.length) {
    materials.value = data.currentExperimentForm.materials.map((item) => ({
      stage: item.stage || "原料",
      materialCode: item.materialCode || "",
      materialName: item.materialName,
      weightKg: String(item.weightKg ?? ""),
      utilizationRate: item.utilizationRate != null ? String(item.utilizationRate * 100) : "",
      remark: item.remark || "",
    }));
  }
  if (data.currentExperimentForm?.processSteps?.length) {
    processSteps.value = fromProcessSteps(data.currentExperimentForm.processSteps);
  }
}

async function loadProcessTemplate(versionId: string) {
  try {
    const steps = await api.sample.processSteps(versionId);
    if (steps.length) {
      processSteps.value = fromProcessSteps(steps);
    }
  } catch {
    // 工序模板加载失败不阻断实验单录入
  }
}

async function loadDetail() {
  loading.value = true;
  loadError.value = "";
  try {
    await ensureAuthReady();
    const data = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
    applyDetail(data);
    if (!data.currentExperimentForm?.processSteps?.length && !readOnly.value) {
      await loadProcessTemplate(data.task.versionId);
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "无法打开实验单";
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);

function addMaterial() {
  materials.value.push(blankMaterial());
}

function buildMaterials(): ExperimentMaterial[] {
  return materials.value
    .filter((item) => item.materialName.trim())
    .map((item, index) => ({
      stage: item.stage.trim() || "原料",
      sequence: index + 1,
      materialCode: item.materialCode.trim() || undefined,
      materialName: item.materialName.trim(),
      weightKg: Number(item.weightKg || 0),
      utilizationRate: item.utilizationRate ? Number(item.utilizationRate) / 100 : undefined,
      remark: item.remark.trim() || undefined,
    }));
}

function buildSummary() {
  return [
    form.summary,
    form.yieldQty ? `出成：${form.yieldQty}${form.yieldUnit}` : "",
    form.remark ? `备注：${form.remark}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

async function saveDraft() {
  saving.value = true;
  try {
    detail.value = {
      ...detail.value!,
      currentExperimentForm: await api.task.saveExperimentDraft(String(route.params.id), {
        operatorName: auth.displayName,
        summary: buildSummary(),
        materials: buildMaterials(),
        processSteps: toProcessSteps(processSteps.value),
      }),
    };
    draftSaved.value = true;
    showSuccessToast("草稿已保存");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function afterRead(item: { file?: File } | Array<{ file?: File }>) {
  const entry = Array.isArray(item) ? item[0] : item;
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId || !entry?.file) return;
  try {
    await api.task.uploadAttachment(experimentId, entry.file, {
      uploadedBy: auth.displayName,
      category: "PHOTO",
    });
    showSuccessToast("照片已上传");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "上传失败");
  }
}

async function notifyTest() {
  if (!detail.value?.currentExperimentForm?.id && canSaveDraft.value) {
    await saveDraft();
  }
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    showFailToast("请先保存草稿");
    return;
  }
  submitting.value = true;
  try {
    await api.task.submitExperimentForTest(experimentId, auth.displayName);
    showSuccessToast("已通知内部测试");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}
</script>
