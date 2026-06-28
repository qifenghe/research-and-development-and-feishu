<template>
  <div>
    <PageHeader :title="headerTitle" compact>
      <StatusBadge label="打样中" variant="success" />
      <StatusBadge v-if="draftSaved" label="草稿已保存" variant="primary" style="margin-left: 8px" />
    </PageHeader>

    <van-collapse v-model="activeSections">
      <van-collapse-item title="基本信息" name="basic">
        <van-field v-model="form.productName" label="产品名称" readonly />
        <van-field v-model="form.specification" label="产品规格" readonly />
        <van-field v-model="form.summary" rows="2" autosize type="textarea" label="实验摘要" placeholder="填写本次打样说明" />
      </van-collapse-item>
      <van-collapse-item title="原辅料/包材" name="materials">
        <div v-for="(material, index) in materials" :key="index" class="task-card" style="margin-bottom: 8px">
          <van-field v-model="material.stage" label="工段" placeholder="清洗/卤制/包装" />
          <van-field v-model="material.materialCode" label="物料编码" placeholder="可选" />
          <van-field v-model="material.materialName" label="物料名称" placeholder="冻猪大肠头" />
          <van-field v-model="material.weightKg" label="用量kg" type="number" placeholder="100" />
          <van-field v-model="material.utilizationRate" label="利用率%" type="number" placeholder="95" />
          <van-field v-model="material.remark" label="备注" placeholder="可选" />
        </div>
        <van-button block plain type="primary" size="small" @click="addMaterial">+ 添加物料行</van-button>
      </van-collapse-item>
      <van-collapse-item title="工序记录" name="process">
        <ProcessStepEditor v-model="processSteps" />
      </van-collapse-item>
      <van-collapse-item title="成品出成" name="yield">
        <van-field v-model="form.yieldQty" label="研发参考出成" type="number" placeholder="89" />
        <van-field v-model="form.yieldUnit" label="单位" placeholder="kg" />
      </van-collapse-item>
      <van-collapse-item title="照片附件" name="photos">
        <van-uploader v-model="fileList" :after-read="afterRead" />
      </van-collapse-item>
      <van-collapse-item title="备注" name="remark">
        <van-field v-model="form.remark" rows="3" autosize type="textarea" placeholder="补充现场说明" />
      </van-collapse-item>
    </van-collapse>

    <FixedActionBar>
      <van-button block plain type="primary" :loading="saving" @click="saveDraft">保存草稿</van-button>
      <van-button type="primary" block :loading="submitting" @click="notifyTest">通知测试</van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { ExperimentMaterial, RndTaskDetailView } from "@rnd/shared";
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

onMounted(async () => {
  detail.value = await api.task.detail(String(route.params.id), auth.role);
  form.productName = detail.value.task.productName;
  headerTitle.value = `${detail.value.task.productName} ${detail.value.task.versionCode}`;
  const specField = detail.value.fieldGroups.flatMap((g) => g.fields).find((f) => f.label.includes("规格"));
  form.specification = specField?.value ?? "";
  if (detail.value.currentExperimentForm?.summary) {
    form.summary = detail.value.currentExperimentForm.summary;
    draftSaved.value = true;
  }
  if (detail.value.currentExperimentForm?.materials?.length) {
    materials.value = detail.value.currentExperimentForm.materials.map((item) => ({
      stage: item.stage || "原料",
      materialCode: item.materialCode || "",
      materialName: item.materialName,
      weightKg: String(item.weightKg ?? ""),
      utilizationRate: item.utilizationRate != null ? String(item.utilizationRate * 100) : "",
      remark: item.remark || "",
    }));
  }
  if (detail.value.currentExperimentForm?.processSteps?.length) {
    processSteps.value = fromProcessSteps(detail.value.currentExperimentForm.processSteps);
  } else {
    const steps = await api.sample.processSteps(detail.value.task.versionId);
    if (steps.length) {
      processSteps.value = fromProcessSteps(steps);
    }
  }
});

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
  if (!detail.value?.currentExperimentForm?.id) {
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
