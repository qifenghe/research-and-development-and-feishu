<template>
  <div>
    <PageHeader :title="'我的待办'" :subtitle="`${auth.displayName} / ${roleLabel}`">
      <template #stats>
        <span class="stat-chip stat-chip--primary">今日待办 {{ board?.totalCount ?? 0 }}</span>
        <span v-if="(board?.overdueCount ?? 0) > 0" class="stat-chip stat-chip--warning">
          逾期 {{ board?.overdueCount }}
        </span>
      </template>
    </PageHeader>

    <van-pull-refresh v-model="refreshing" @refresh="load">
      <RoleEntryGrid v-if="roleEntries.length" :entries="roleEntries" @select="router.push" />

      <HeroCard
        v-if="board?.hero"
        eyebrow="继续处理"
        :title="`${board.hero.productName} · ${board.hero.versionCode}`"
        :subtitle="board.hero.subtitle ?? board.hero.statusLabel"
        :action-label="board.hero.actionLabel"
        @action="router.push(board.hero!.route)"
      />

      <div v-for="group in displayGroups" :key="group.key">
        <div class="section-title">{{ group.title }}</div>
        <TaskCard
          v-for="item in group.items"
          :key="`${group.key}-${item.id}`"
          :title="item.productName"
          :meta="taskMeta(item)"
          :action-label="item.actionLabel"
          @action="router.push(item.route)"
        />
      </div>

      <van-empty v-if="!loading && (board?.totalCount ?? 0) === 0" description="暂无待办任务" />
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchMobileTodoBoard, type MobileTodoBoard, type MobileTodoItem } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import HeroCard from "../components/HeroCard.vue";
import TaskCard from "../components/TaskCard.vue";
import RoleEntryGrid from "../components/RoleEntryGrid.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const router = useRouter();
const loading = ref(false);
const refreshing = ref(false);
const board = ref<MobileTodoBoard | null>(null);

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    RND_ENGINEER: "研发人员",
    RND_ASSISTANT: "研发内勤",
    TESTER: "测试人员",
    QA_TESTER: "测试人员",
    RND_DIRECTOR: "研发总监",
  };
  return map[auth.role] ?? auth.role ?? "未知";
});

const roleEntries = computed(() => {
  const entries: Array<{ label: string; title: string; desc: string; route: string }> = [];
  if (auth.role === "RND_ASSISTANT") {
    entries.push({
      label: "研发内勤",
      title: "录入需求",
      desc: "提交样品清单",
      route: "/request/new",
    });
  }
  if (auth.role === "RND_DIRECTOR") {
    entries.push(
      {
        label: "研发总监",
        title: "审核需求",
        desc: "通过后进入任务池",
        route: "/requests/review",
      },
      {
        label: "研发总监",
        title: "分配任务",
        desc: "指派给研发人员",
        route: "/tasks/assign",
      },
      {
        label: "研发身份",
        title: "我的研发",
        desc: "自己也可接单打样",
        route: "/samples",
      },
    );
  }
  if (auth.role === "RND_ENGINEER") {
    entries.push({
      label: "研发人员",
      title: "接任务/打样",
      desc: "接受任务并填写实验单",
      route: "/samples",
    });
  }
  if (auth.role === "FINANCE") {
    entries.push({
      label: "财务",
      title: "待核价",
      desc: "查看已生成核价文件",
      route: "/samples",
    });
  }
  return entries;
});

const displayGroups = computed(() => {
  if (!board.value) return [];
  const hero = board.value.hero;
  return board.value.groups
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => !(hero && item.id === hero.id && item.kind === hero.kind),
      ),
    }))
    .filter((group) => group.items.length > 0);
});

function taskMeta(item: MobileTodoItem) {
  const parts = [item.versionCode, item.subtitle ?? item.statusLabel];
  if (item.dueDate) parts.push(`截止 ${item.dueDate}`);
  return parts.filter(Boolean).join(" · ");
}

async function load() {
  loading.value = true;
  try {
    board.value = await fetchMobileTodoBoard({
      listTasks: (params) => api.task.list(params) as Promise<import("@rnd/shared").RndTask[]>,
      listShipments: (params) => api.shipment.list(params),
      listPricingFiles: (params) => api.shipment.pricingFiles(params) as Promise<import("@rnd/shared").PricingFileRecord[]>,
    });
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

onMounted(load);
</script>
