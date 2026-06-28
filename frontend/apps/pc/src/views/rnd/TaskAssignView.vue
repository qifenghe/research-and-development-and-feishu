<template>
  <div>
    <a-page-header title="PC端：任务分发" sub-title="研发总监分发给具体研发人员，飞书自动通知到人" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-input v-model:value="assignees[record.id]" placeholder="研发人员" style="width: 140px" />
              <a-date-picker v-model:value="dueDates[record.id]" value-format="YYYY-MM-DD" />
              <a-button type="primary" size="small" @click="assign(record.id)">分发</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import type { RndTask } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<RndTask[]>([]);
const assignees = reactive<Record<string, string>>({});
const dueDates = reactive<Record<string, string>>({});

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await api.task.pool();
  } finally {
    loading.value = false;
  }
}

async function assign(id: string) {
  const assigneeName = assignees[id]?.trim();
  const dueDate = dueDates[id];
  if (!assigneeName || !dueDate) {
    message.warning("请填写研发人员和截止日期");
    return;
  }
  try {
    await api.task.assign(id, assigneeName, dueDate);
    message.success("任务已分发");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "分发失败");
  }
}

onMounted(load);
</script>
