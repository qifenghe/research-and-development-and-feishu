<template>
  <div>
    <a-page-header title="通知财务核价" sub-title="核价文件生成后推送财务，流程到这里结束" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button size="small" @click="router.push(`/pricing/${record.id}`)">处理</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { PricingFileRecord } from "@rnd/shared";
import { api } from "../../services/api";

const router = useRouter();
const loading = ref(false);
const rows = ref<PricingFileRecord[]>([]);

const columns = [
  { title: "文件名", dataIndex: "fileName", key: "fileName" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await api.shipment.pricingFiles({ status: "GENERATED" }) as PricingFileRecord[];
  } finally {
    loading.value = false;
  }
});
</script>
