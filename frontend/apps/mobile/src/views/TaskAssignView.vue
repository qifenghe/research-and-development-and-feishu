<template>
  <div>
    <PageHeader title="任务分发" subtitle="研发总监把任务池里的样品分给具体研发人员" />

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <div v-for="task in rows" :key="task.id" class="task-card">
        <div class="task-card__row">
          <div>
            <h3 class="task-card__title">{{ task.productName }}</h3>
            <p class="task-card__meta">{{ task.sampleNo }} · {{ task.versionCode }} · 待分发</p>
          </div>
          <StatusBadge label="任务池" variant="primary" />
        </div>

        <van-field
          v-model="assignForms[task.id].assigneeName"
          label="研发人员"
          placeholder="填写研发人员姓名"
          input-align="right"
        />
        <van-field
          v-model="assignForms[task.id].dueDate"
          label="截止日期"
          type="date"
          input-align="right"
        />
        <van-button block round type="primary" :loading="assigningId === task.id" @click="assign(task.id)">
          分配任务
        </van-button>
      </div>

      <van-empty v-if="!loading && rows.length === 0" description="暂无待分发任务" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { showFailToast, showSuccessToast } from "vant";
import type { RndTask } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { api } from "../services/api";

const loading = ref(false);
const refreshing = ref(false);
const assigningId = ref("");
const rows = ref<RndTask[]>([]);
const assignForms = reactive<Record<string, { assigneeName: string; dueDate: string }>>({});

function defaultDueDate() {
  const date = new Date();
  date.setDate(date.getDate() + 3);
  return date.toISOString().slice(0, 10);
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await api.task.list({ status: "PENDING_ASSIGNMENT" })) as RndTask[];
    for (const task of rows.value) {
      assignForms[task.id] ??= {
        assigneeName: "张研发",
        dueDate: defaultDueDate(),
      };
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function assign(id: string) {
  const form = assignForms[id];
  if (!form?.assigneeName || !form.dueDate) {
    showFailToast("请填写研发人员和截止日期");
    return;
  }
  assigningId.value = id;
  try {
    await api.task.assign(id, form.assigneeName, form.dueDate);
    showSuccessToast("任务已分配");
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "分配失败");
  } finally {
    assigningId.value = "";
  }
}

onMounted(load);
</script>
