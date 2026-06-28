<template>
  <div>
    <a-page-header :title="detail?.productName ?? '需求详情'" @back="router.back()">
      <template #extra>
        <a-tag v-if="detail" :color="statusColor">{{ detail.status }}</a-tag>
      </template>
    </a-page-header>
    <a-card v-if="detail" :loading="loading">
      <a-descriptions bordered :column="2">
        <a-descriptions-item label="样品编号">{{ detail.sampleNo }}</a-descriptions-item>
        <a-descriptions-item label="产品类型">{{ detail.productType }}</a-descriptions-item>
        <a-descriptions-item label="客户">{{ detail.customerName }}</a-descriptions-item>
        <a-descriptions-item label="规格">{{ detail.specification }}</a-descriptions-item>
        <a-descriptions-item label="应用场景">{{ detail.applicationScenario }}</a-descriptions-item>
        <a-descriptions-item label="风味要求">{{ detail.flavorRequirement }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ detail.creatorName }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detail.createdAt }}</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { SampleRequest } from "@rnd/shared";
import { api } from "../../services/api";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<SampleRequest | null>(null);

const statusColor = computed(() => {
  if (!detail.value) return "default";
  if (detail.value.status === "APPROVED") return "green";
  if (detail.value.status === "PENDING_REVIEW") return "orange";
  return "red";
});

onMounted(async () => {
  loading.value = true;
  try {
    detail.value = await api.sample.detail(String(route.params.id));
  } finally {
    loading.value = false;
  }
});
</script>
