<template>
  <div>
    <van-skeleton title :row="6" :loading="loading">
      <PageHeader :title="productName" compact>
        <StatusBadge :label="statusLabel" :variant="statusVariant" />
      </PageHeader>

      <InfoCard title="样品信息" :rows="detailFields" />
    </van-skeleton>

    <FixedActionBar>
      <van-button v-if="canAccept" type="primary" block :loading="submitting" @click="acceptTask">接受任务</van-button>
      <van-button v-if="canEditExperiment" type="primary" block @click="router.push(`/experiments/${route.params.id}`)">
        {{ detail?.currentExperimentForm ? "继续填写实验单" : "开始填写实验单" }}
      </van-button>
      <van-button v-if="canViewTest" type="primary" block @click="router.push(`/tests/${route.params.id}`)">查看测试确认</van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import { taskStatusLabel, type RndTaskDetailView } from "@rnd/shared";
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

const productName = computed(() => detail.value?.task.productName ?? "任务详情");
const statusLabel = computed(() => (detail.value ? taskStatusLabel(detail.value.task.status) : "加载中"));
const canAccept = computed(() => detail.value?.task.status === "PENDING_ACCEPTANCE");
const canEditExperiment = computed(() => detail.value?.task.status === "SAMPLING");
const canViewTest = computed(() => detail.value?.task.status === "PENDING_TEST");
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
  const groups = detail.value?.fieldGroups ?? [];
  return groups.flatMap((group) => group.fields);
});

async function load() {
  loading.value = true;
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role);
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

onMounted(load);
</script>
