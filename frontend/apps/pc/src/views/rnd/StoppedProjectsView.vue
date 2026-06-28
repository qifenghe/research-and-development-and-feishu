<template>
  <div>
    <a-page-header title="停止/废弃项目池" sub-title="复打样无效或项目取消时，记录原因并保留历史资料" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="projectId" :loading="loading" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { StoppedSampleProjectView } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<StoppedSampleProjectView[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "客户", dataIndex: "customerName", key: "customerName" },
  { title: "最后版本", dataIndex: "lastVersionCode", key: "lastVersionCode" },
  { title: "停止原因", dataIndex: "stopReason", key: "stopReason" },
  { title: "停止人", dataIndex: "stoppedBy", key: "stoppedBy" },
];

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await api.sample.stoppedProjects();
  } finally {
    loading.value = false;
  }
});
</script>
