<template>
  <div>
    <PageHeader title="审核需求" subtitle="研发总监审核内勤提交的样品需求" />

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <van-empty v-if="loadError" class="mobile-empty" image="error" :description="loadError">
        <van-button type="primary" @click="load">重新加载</van-button>
      </van-empty>
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
        <van-button
          v-if="canApprove"
          block
          type="primary"
          :loading="approvingId === request.id"
          @click="approve(request.id)"
        >
          审核通过
        </van-button>
      </div>

      <van-empty v-if="!loading && !loadError && rows.length === 0" description="暂无待审核需求，可下拉刷新" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { showFailToast, showSuccessToast } from "vant";
import { canPerformAction, type SampleRequest } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const canApprove = computed(() => canPerformAction(auth.role, "APPROVE_REQUEST"));
const loading = ref(false);
const refreshing = ref(false);
const approvingId = ref("");
const rows = ref<SampleRequest[]>([]);
const loadError = ref("");

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    rows.value = (await api.sample.list({ status: "PENDING_REVIEW" })) as SampleRequest[];
  } catch (error) {
    rows.value = [];
    loadError.value = error instanceof Error ? error.message : "待审核需求加载失败";
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
