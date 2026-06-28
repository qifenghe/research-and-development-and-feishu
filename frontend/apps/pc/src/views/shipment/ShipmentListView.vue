<template>
  <div>
    <a-page-header title="寄样反馈列表" sub-title="多个样品同时等待反馈，先列表排队，再进入详情处理" />
    <a-card>
      <a-input-search v-model:value="keyword" placeholder="搜索产品" style="width: 280px; margin-bottom: 16px" @search="load" />
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button size="small" @click="router.push(`/shipment/${record.id}`)">处理</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { ShipmentRecord } from "@rnd/shared";
import { api } from "../../services/api";

const router = useRouter();
const loading = ref(false);
const keyword = ref("");
const rows = ref<ShipmentRecord[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "收件人", dataIndex: "receiverName", key: "receiverName" },
  { title: "快递单号", dataIndex: "trackingNo", key: "trackingNo" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await api.shipment.list({ keyword: keyword.value || undefined });
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
