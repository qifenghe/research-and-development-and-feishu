<template>
  <div>
    <a-page-header title="样品文件归档" sub-title="按产品、版本、阶段保存需求、实验单、测试、寄样、核价文件" />
    <a-card>
      <a-input v-model:value="versionId" placeholder="输入样品版本 ID 查询归档文件" style="width: 360px; margin-bottom: 16px" />
      <a-button type="primary" @click="load">查询</a-button>
      <a-table :columns="columns" :data-source="files" row-key="id" :loading="loading" style="margin-top: 16px" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { ArchiveFileView } from "@rnd/shared";
import { api } from "../../services/api";

const versionId = ref("");
const loading = ref(false);
const files = ref<ArchiveFileView[]>([]);

const columns = [
  { title: "文件名", dataIndex: "fileName", key: "fileName" },
  { title: "类别", dataIndex: "category", key: "category" },
  { title: "上传人", dataIndex: "uploadedBy", key: "uploadedBy" },
  { title: "上传时间", dataIndex: "uploadedAt", key: "uploadedAt" },
];

async function load() {
  if (!versionId.value.trim()) return;
  loading.value = true;
  try {
    files.value = await api.shipment.archiveFiles(versionId.value.trim());
  } finally {
    loading.value = false;
  }
}
</script>
