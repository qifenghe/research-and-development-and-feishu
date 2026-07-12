<template>
  <div>
    <PageHeader title="任务分发" subtitle="研发总监从人员列表中选择具体研发人员" />

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <van-empty v-if="loadError" class="mobile-empty" image="error" :description="loadError">
        <van-button type="primary" @click="load">重新加载</van-button>
      </van-empty>
      <div v-for="task in rows" :key="task.id" class="task-card">
        <div class="task-card__row">
          <div>
            <h3 class="task-card__title">{{ task.productName }}</h3>
            <p class="task-card__meta">{{ task.sampleNo }} · {{ task.versionCode }} · 待分发</p>
          </div>
          <StatusBadge label="任务池" variant="primary" />
        </div>

        <van-field
          :model-value="assignForms[task.id]?.assigneeName"
          is-link
          readonly
          label="研发人员"
          placeholder="选择研发人员"
          input-align="right"
          @click="openPicker(task.id)"
        />
        <van-field
          v-model="assignForms[task.id].dueDate"
          label="截止日期"
          type="date"
          input-align="right"
        />
        <van-button
          v-if="canAssign"
          block
          type="primary"
          :loading="assigningId === task.id"
          @click="assign(task.id)"
        >
          分配任务
        </van-button>
      </div>

      <van-empty v-if="!loading && !loadError && rows.length === 0" description="暂无待分发任务，可下拉刷新" />
    </van-pull-refresh>

    <van-popup v-model:show="pickerOpen" position="bottom" round>
      <van-picker
        :columns="engineerColumns"
        @confirm="onPickEngineer"
        @cancel="pickerOpen = false"
      />
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { showFailToast, showSuccessToast } from "vant";
import { canPerformAction, isRndAssigneeRole, type RndTask, type UserAccount } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const canAssign = computed(() => canPerformAction(auth.role, "ASSIGN_TASK"));

const loading = ref(false);
const refreshing = ref(false);
const assigningId = ref("");
const pickerOpen = ref(false);
const activeTaskId = ref("");
const rows = ref<RndTask[]>([]);
const loadError = ref("");
const engineerColumns = ref<Array<{ text: string; value: string }>>([]);
const assignForms = reactive<Record<string, { assigneeName: string; dueDate: string }>>({});

function defaultDueDate() {
  const date = new Date();
  date.setDate(date.getDate() + 3);
  return date.toISOString().slice(0, 10);
}

function openPicker(taskId: string) {
  activeTaskId.value = taskId;
  pickerOpen.value = true;
}

function onPickEngineer({ selectedOptions }: { selectedOptions: Array<{ text: string; value: string }> }) {
  const option = selectedOptions[0];
  if (activeTaskId.value && option) {
    assignForms[activeTaskId.value].assigneeName = option.value;
  }
  pickerOpen.value = false;
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [pool, users] = await Promise.all([
      api.task.list({ status: "PENDING_ASSIGNMENT" }) as Promise<RndTask[]>,
      api.settings.users(),
    ]);
    rows.value = pool;
    engineerColumns.value = users
      .filter((user: UserAccount) => user.status === "ACTIVE" && isRndAssigneeRole(user.role))
      .map((user) => ({
        text: `${user.name} · ${user.departmentName || "研发部"}`,
        value: user.name,
      }));
    for (const task of rows.value) {
      assignForms[task.id] ??= {
        assigneeName: engineerColumns.value[0]?.value ?? "",
        dueDate: defaultDueDate(),
      };
    }
  } catch (error) {
    rows.value = [];
    loadError.value = error instanceof Error ? error.message : "任务池加载失败";
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function assign(id: string) {
  const form = assignForms[id];
  if (!form?.assigneeName || !form.dueDate) {
    showFailToast("请选择研发人员并填写截止日期");
    return;
  }
  assigningId.value = id;
  try {
    await api.task.assign(id, form.assigneeName, form.dueDate);
    showSuccessToast(`任务已分配给 ${form.assigneeName}`);
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "分配失败");
  } finally {
    assigningId.value = "";
  }
}

onMounted(load);
</script>
