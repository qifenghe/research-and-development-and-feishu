<template>
  <div>
    <a-page-header title="PC端：研发任务池" sub-title="所有审核通过但尚未分配的任务先进入任务池" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag>{{ RND_TASK_STATUS_LABELS[record.status as RndTaskStatus] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button size="small" @click="router.push(`/rnd/tasks/${record.id}`)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { RND_TASK_STATUS_LABELS, type RndTask, type RndTaskStatus } from "@rnd/shared";
import { api } from "../../services/api";

const router = useRouter();
const loading = ref(false);
const rows = ref<RndTask[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "状态", key: "status" },
  { title: "操作", key: "action" },
];

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await api.task.pool();
  } finally {
    loading.value = false;
  }
});
</script>
