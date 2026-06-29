<template>
  <div>
    <PageHeader title="录入客户反馈" subtitle="登记寄样并录入客户试吃结论" />

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div class="section-title">待录入反馈</div>
      <TaskCard
        v-for="item in pendingFeedback"
        :key="item.id"
        :to="`/shipments/${item.id}`"
        :title="item.productName"
        :meta="`${item.versionCode} · ${item.trackingNo}`"
        action-label="录入"
      />
      <van-empty v-if="!loading && pendingFeedback.length === 0" description="暂无待反馈寄样" />

      <div class="section-title">待登记寄样</div>
      <TaskCard
        v-for="task in pendingShipment"
        :key="task.id"
        :to="`/tasks/${task.id}/feedback`"
        :title="task.productName"
        :meta="`${task.versionCode} · ${task.assigneeName}`"
        action-label="登记"
      />
      <van-empty v-if="!loading && pendingShipment.length === 0" description="暂无待寄样样品" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { RndTask, ShipmentRecord } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import TaskCard from "../components/TaskCard.vue";
import { api } from "../services/api";

const loading = ref(false);
const refreshing = ref(false);
const pendingFeedback = ref<ShipmentRecord[]>([]);
const pendingShipment = ref<RndTask[]>([]);

async function load() {
  loading.value = true;
  try {
    const [shipped, completedTasks] = await Promise.all([
      api.shipment.list({ status: "SHIPPED" }) as Promise<ShipmentRecord[]>,
      api.task.list({ status: "COMPLETED" }) as Promise<RndTask[]>,
    ]);
    pendingFeedback.value = shipped;
    const allShipments = (await api.shipment.list()) as ShipmentRecord[];
    const shippedVersionIds = new Set(allShipments.map((item) => item.versionId));
    pendingShipment.value = completedTasks.filter((task) => !shippedVersionIds.has(task.versionId));
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

onMounted(load);
</script>
