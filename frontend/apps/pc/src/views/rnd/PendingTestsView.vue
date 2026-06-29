<template>
  <div>
    <a-page-header title="内部测试待办" sub-title="测试人员对待内部测试的任务进行评价" />
    <a-card class="page-card">
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag color="processing">{{ RND_TASK_STATUS_LABELS[record.status as RndTaskStatus] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="router.push(`/rnd/tasks/${record.id}/test`)">
              填写测试评价
            </a-button>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && rows.length === 0" description="暂无待内部测试任务" />
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
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo", width: 120 },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode", width: 80 },
  { title: "研发负责人", dataIndex: "assigneeName", key: "assigneeName", width: 100 },
  { title: "状态", key: "status", width: 110 },
  { title: "操作", key: "action", width: 120 },
];

onMounted(async () => {
  loading.value = true;
  try {
    rows.value = await api.task.list({ status: "PENDING_TEST" }) as RndTask[];
  } finally {
    loading.value = false;
  }
});
</script>
