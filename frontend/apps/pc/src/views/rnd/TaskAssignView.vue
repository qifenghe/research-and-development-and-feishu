<template>
  <div>
    <a-page-header title="PC端：任务分发" sub-title="研发总监从人员列表中选择具体研发人员，飞书自动通知到人" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space v-if="canAssign" wrap>
              <a-select
                v-model:value="assignees[record.id]"
                show-search
                placeholder="选择研发人员"
                style="width: 220px"
                :options="engineerOptions"
                :filter-option="filterEngineer"
              />
              <a-date-picker v-model:value="dueDates[record.id]" value-format="YYYY-MM-DD" placeholder="截止日期" />
              <a-button type="primary" size="small" @click="assign(record.id)">分发</a-button>
              <a-button size="small" @click="router.push(`/rnd/tasks/${record.id}`)">详情</a-button>
            </a-space>
            <a-button v-else size="small" @click="router.push(`/rnd/tasks/${record.id}`)">详情</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { canPerformAction, isRndAssigneeRole, type RndTask, type UserAccount } from "@rnd/shared";
import { api } from "../../services/api";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const auth = useAuthStore();
const canAssign = computed(() => canPerformAction(auth.role, "ASSIGN_TASK"));
const loading = ref(false);
const rows = ref<RndTask[]>([]);
const assignees = reactive<Record<string, string>>({});
const dueDates = reactive<Record<string, string>>({});
const engineerOptions = ref<Array<{ label: string; value: string }>>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode" },
  { title: "操作", key: "action", width: 520 },
];

function filterEngineer(input: string, option?: { label: string; value: string }) {
  return (option?.label ?? "").toLowerCase().includes(input.toLowerCase());
}

async function load() {
  loading.value = true;
  try {
    const [pool, users] = await Promise.all([api.task.pool(), api.settings.users()]);
    rows.value = pool;
    engineerOptions.value = users
      .filter((user: UserAccount) => user.status === "ACTIVE" && isRndAssigneeRole(user.role))
      .map((user) => ({
        label: `${user.name} · ${user.departmentName || "研发部"}`,
        value: user.name,
      }));
  } finally {
    loading.value = false;
  }
}

async function assign(id: string) {
  const assigneeName = assignees[id]?.trim();
  const dueDate = dueDates[id];
  if (!assigneeName || !dueDate) {
    message.warning("请选择研发人员并填写截止日期");
    return;
  }
  try {
    await api.task.assign(id, assigneeName, dueDate);
    message.success(`任务已分发给 ${assigneeName}`);
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "分发失败");
  }
}

onMounted(load);
</script>
