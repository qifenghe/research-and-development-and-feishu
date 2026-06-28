<template>
  <div>
    <PageHeader title="审核需求" subtitle="研发总监审核内勤提交的样品需求" />

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div v-for="request in rows" :key="request.id" class="task-card">
        <div class="task-card__row">
          <div>
            <h3 class="task-card__title">{{ request.productName }}</h3>
            <p class="task-card__meta">{{ request.sampleNo }} · {{ request.productType }} · {{ request.specification }}</p>
          </div>
          <StatusBadge label="待审核" variant="warning" />
        </div>
        <div class="mobile-detail-block">
          <p><strong>客户：</strong>{{ request.customerName }}</p>
          <p><strong>应用场景：</strong>{{ request.applicationScenario }}</p>
          <p><strong>口味/风味：</strong>{{ request.flavorRequirement }}</p>
          <p><strong>申请人：</strong>{{ request.creatorName }}</p>
        </div>
        <van-button block round type="primary" :loading="approvingId === request.id" @click="approve(request.id)">
          审核通过
        </van-button>
      </div>

      <van-empty v-if="!loading && rows.length === 0" description="暂无待审核需求" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { showFailToast, showSuccessToast } from "vant";
import type { SampleRequest } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const loading = ref(false);
const refreshing = ref(false);
const approvingId = ref("");
const rows = ref<SampleRequest[]>([]);

async function load() {
  loading.value = true;
  try {
    rows.value = (await api.sample.list({ status: "PENDING_REVIEW" })) as SampleRequest[];
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function approve(id: string) {
  approvingId.value = id;
  try {
    await api.sample.approve(id, auth.displayName);
    showSuccessToast("审核通过，已进入任务池");
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "审核失败");
  } finally {
    approvingId.value = "";
  }
}

onMounted(load);
</script>
