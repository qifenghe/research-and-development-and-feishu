<template>
  <div>
    <a-page-header title="我的打样任务" :sub-title="`${auth.displayName} · 接受任务、填写实验单并提交内部测试`" />
    <a-card class="page-card">
      <a-space wrap style="margin-bottom: 16px">
        <a-radio-group v-model:value="activeStatus" button-style="solid" @change="load">
          <a-radio-button value="">全部</a-radio-button>
          <a-radio-button value="PENDING_ACCEPTANCE">待接受</a-radio-button>
          <a-radio-button value="SAMPLING">打样中</a-radio-button>
          <a-radio-button value="PENDING_TEST">待测试</a-radio-button>
        </a-radio-group>
      </a-space>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ RND_TASK_STATUS_LABELS[record.status as RndTaskStatus] }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="router.push(`/rnd/tasks/${record.id}`)">
              {{ actionLabel(record.status) }}
            </a-button>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && rows.length === 0" description="暂无打样任务，请等待总监分发" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { RND_TASK_STATUS_LABELS, type RndTask, type RndTaskStatus } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

const auth = useAuthStore();
const router = useRouter();
const loading = ref(false);
const rows = ref<RndTask[]>([]);
const activeStatus = ref("");

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo", width: 120 },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode", width: 80 },
  { title: "截止日期", dataIndex: "dueDate", key: "dueDate", width: 120 },
  { title: "状态", key: "status", width: 110 },
  { title: "操作", key: "action", width: 100 },
];

function statusColor(status: string) {
  const map: Record<string, string> = {
    PENDING_ACCEPTANCE: "orange",
    SAMPLING: "green",
    PENDING_TEST: "processing",
  };
  return map[status] ?? "default";
}

function actionLabel(status: string) {
  if (status === "PENDING_ACCEPTANCE") return "去接单";
  if (status === "SAMPLING") return "填实验单";
  return "查看";
}

async function load() {
  loading.value = true;
  try {
    rows.value = await api.task.list({
      assigneeName: auth.displayName,
      status: activeStatus.value || undefined,
    }) as RndTask[];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
