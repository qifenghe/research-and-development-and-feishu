<template>
  <div>
    <a-page-header title="人员配置与权限" sub-title="维护研发内勤、研发总监、研发、测试、财务、管理员权限" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="toggle(record.id, record.status !== 'ACTIVE')">{{ record.status === "ACTIVE" ? "禁用" : "启用" }}</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import type { UserAccount } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<UserAccount[]>([]);

const columns = [
  { title: "姓名", dataIndex: "name", key: "name" },
  { title: "飞书 ID", dataIndex: "feishuUserId", key: "feishuUserId" },
  { title: "角色", dataIndex: "role", key: "role" },
  { title: "部门", dataIndex: "departmentName", key: "departmentName" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await api.settings.users();
  } finally {
    loading.value = false;
  }
}

async function toggle(id: string, enable: boolean) {
  try {
    if (enable) await api.settings.enableUser(id);
    else await api.settings.disableUser(id);
    message.success("已更新用户状态");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "更新失败");
  }
}

onMounted(load);
</script>
