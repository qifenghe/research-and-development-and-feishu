<template>
  <div class="page-with-footer">
    <van-skeleton title :row="6" :loading="loading">
      <PageHeader :title="productName" compact>
        <StatusBadge :label="statusLabel" :variant="statusVariant" />
      </PageHeader>

      <van-empty v-if="loadError" class="mobile-empty" image="error" :description="loadError">
        <van-button type="primary" @click="load">重新加载</van-button>
      </van-empty>

      <template v-else>
        <InfoCard title="样品信息" :rows="detailFields" />

        <div v-if="assigneeHint" class="mobile-detail-block">
          <p>{{ assigneeHint }}</p>
        </div>
      </template>
    </van-skeleton>

    <FixedActionBar v-if="hasActions" :with-tabbar="false">
      <van-button
        v-if="canOpenExperiment"
        :type="primaryAction === 'experiment' ? 'primary' : 'default'"
        :plain="primaryAction !== 'experiment'"
        block
        @click="router.push(`/experiments/${route.params.id}`)"
      >
        {{ detail?.currentExperimentForm ? "继续填写实验单" : "开始填写实验单" }}
      </van-button>
      <van-button
        v-if="canViewExperiment"
        plain
        type="primary"
        block
        @click="router.push(`/experiments/${route.params.id}`)"
      >
        查看实验单
      </van-button>
      <van-button
        v-if="canNotifyTest"
        :type="primaryAction === 'notify' ? 'primary' : 'default'"
        :plain="primaryAction !== 'notify'"
        block
        :loading="submitting"
        @click="notifyTest"
      >
        通知测试
      </van-button>
      <van-button
        v-if="canAccept"
        :type="primaryAction === 'accept' ? 'primary' : 'default'"
        :plain="primaryAction !== 'accept'"
        block
        :loading="submitting"
        @click="acceptTask"
      >
        接受任务
      </van-button>
      <van-button
        v-if="canViewTest"
        :type="primaryAction === 'test' ? 'primary' : 'default'"
        :plain="primaryAction !== 'test'"
        block
        @click="router.push(`/tests/${route.params.id}`)"
      >
        进入测试确认
      </van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import {
  buildTaskSummaryRows,
  canEditExperiment,
  canNotifyInternalTest,
  canOperateTask,
  taskStatusLabel,
  type RndTaskDetailView,
} from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import InfoCard from "../components/InfoCard.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const detail = ref<RndTaskDetailView | null>(null);
const loadError = ref("");

const productName = computed(() => detail.value?.task.productName ?? "任务详情");
const statusLabel = computed(() => (detail.value ? taskStatusLabel(detail.value.task.status) : "加载中"));
const canAccept = computed(() =>
  detail.value?.task.status === "PENDING_ACCEPTANCE"
  && detail.value
  && canOperateTask(detail.value, auth.displayName, auth.role),
);
const canOpenExperiment = computed(() =>
  detail.value?.task.status === "SAMPLING"
  && detail.value != null
  && canEditExperiment(detail.value, auth.displayName, auth.role),
);
const canViewExperiment = computed(() =>
  detail.value?.task.status === "SAMPLING"
  && auth.role === "RND_ASSISTANT"
  && !canOpenExperiment.value,
);
const canNotifyTest = computed(() =>
  detail.value ? canNotifyInternalTest(detail.value, auth.displayName, auth.role) : false,
);
const canViewTest = computed(() =>
  detail.value?.task.status === "PENDING_TEST"
  && ["TESTER", "QA_TESTER", "ADMIN", "SYSTEM_ADMIN"].includes(auth.role),
);
const hasActions = computed(() =>
  canAccept.value || canOpenExperiment.value || canViewExperiment.value || canNotifyTest.value || canViewTest.value,
);
const primaryAction = computed(() => {
  if (canAccept.value) return "accept";
  if (canNotifyTest.value) return "notify";
  if (canOpenExperiment.value) return "experiment";
  return "test";
});
const assigneeHint = computed(() => {
  const task = detail.value?.task;
  if (!task?.assigneeName) return "";
  if (task.status === "SAMPLING" && auth.role === "RND_ASSISTANT") {
    return `研发负责人：${task.assigneeName}。内勤可在研发保存草稿后通知测试。`;
  }
  if (task.status === "PENDING_ACCEPTANCE") {
    return `已分配给 ${task.assigneeName}，请确认接单后开始打样。`;
  }
  return "";
});
const statusVariant = computed(() => {
  switch (detail.value?.task.status) {
    case "PENDING_ACCEPTANCE":
      return "warning" as const;
    case "SAMPLING":
      return "success" as const;
    default:
      return "primary" as const;
  }
});

const detailFields = computed(() => {
  if (!detail.value) return [];
  return buildTaskSummaryRows(detail.value);
});

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
  } catch (error) {
    detail.value = null;
    loadError.value = error instanceof Error ? error.message : "任务详情加载失败";
  } finally {
    loading.value = false;
  }
}

async function acceptTask() {
  submitting.value = true;
  try {
    await api.task.accept(String(route.params.id), auth.displayName);
    showSuccessToast("已接受任务");
    router.push(`/experiments/${route.params.id}`);
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "接受失败");
  } finally {
    submitting.value = false;
  }
}

async function notifyTest() {
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    showFailToast("研发尚未保存实验单草稿");
    return;
  }
  submitting.value = true;
  try {
    await api.task.submitExperimentForTest(experimentId, auth.displayName);
    showSuccessToast("已通知内部测试");
    router.push("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "通知失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>
