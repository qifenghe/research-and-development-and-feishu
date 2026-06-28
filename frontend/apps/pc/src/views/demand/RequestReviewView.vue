<template>
  <div>
    <a-page-header title="研发总监：需求审核" sub-title="研发总监判断资料是否完整，通过后进入任务池" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag>{{ SAMPLE_STATUS_LABELS[record.status as keyof typeof SAMPLE_STATUS_LABELS] ?? record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="primary" size="small" @click="approve(record.id)">审核通过</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import { SAMPLE_STATUS_LABELS, type SampleRequest } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const auth = useAuthStore();
const loading = ref(false);
const rows = ref<SampleRequest[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "客户", dataIndex: "customerName", key: "customerName" },
  { title: "应用场景", dataIndex: "applicationScenario", key: "applicationScenario", ellipsis: true },
  { title: "口味/风味要求", dataIndex: "flavorRequirement", key: "flavorRequirement", ellipsis: true },
  { title: "申请人", dataIndex: "creatorName", key: "creatorName" },
  { title: "状态", key: "status" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await api.sample.list({ status: "PENDING_REVIEW" }) as SampleRequest[];
  } finally {
    loading.value = false;
  }
}

async function approve(id: string) {
  try {
    await api.sample.approve(id, auth.displayName);
    message.success("审核通过，已进入任务池");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "审核失败");
  }
}

onMounted(load);
</script>
