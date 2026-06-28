<template>
  <div>
    <PageHeader title="样品" />

    <van-search v-model="keyword" shape="round" placeholder="搜索产品名称 / 样品编号" @search="load" @update:model-value="load" />

    <div class="chip-row">
      <button
        v-for="chip in chips"
        :key="chip.value"
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': activeStatus === chip.value }"
        @click="selectStatus(chip.value)"
      >
        {{ chip.label }}
      </button>
    </div>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load">
      <TaskCard
        v-for="task in rows"
        :key="task.id"
        :title="task.productName"
        :meta="`${task.versionCode} · ${taskStatusLabel(task.status)}`"
        action-label="历史"
        @click="openSample(task)"
        @action="openHistory(task)"
      />
    </van-list>

    <van-empty v-if="!loading && rows.length === 0" description="暂无样品" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { taskPrimaryRoute, taskStatusLabel, type RndTask } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import TaskCard from "../components/TaskCard.vue";
import { api } from "../services/api";

const router = useRouter();
const route = useRoute();
const keyword = ref("");
const activeStatus = ref("");
const loading = ref(false);
const finished = ref(true);
const rows = ref<RndTask[]>([]);

const chips = [
  { label: "全部", value: "" },
  { label: "打样中", value: "SAMPLING" },
  { label: "待测试", value: "PENDING_TEST" },
  { label: "待寄样", value: "PENDING_ACCEPTANCE" },
];

function selectStatus(value: string) {
  activeStatus.value = value;
  load();
}

function openSample(task: RndTask) {
  router.push(taskPrimaryRoute(task));
}

function openHistory(task: RndTask) {
  router.push({
    path: `/samples/${task.versionId}/history`,
    query: { projectId: task.projectId, productName: task.productName },
  });
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await api.task.list({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
    })) as RndTask[];
  } finally {
    loading.value = false;
    finished.value = true;
  }
}

watch(
  () => route.query.status,
  (value) => {
    if (typeof value === "string") {
      activeStatus.value = value;
      load();
    }
  },
  { immediate: true },
);

onMounted(load);
</script>
