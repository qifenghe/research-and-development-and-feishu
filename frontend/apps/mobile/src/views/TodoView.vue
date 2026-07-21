<template>
  <div>
    <van-skeleton title :row="4" :loading="loading">
      <van-empty v-if="loadError" class="mobile-empty" image="error" :description="loadError">
        <van-button type="primary" @click="load">重新加载</van-button>
      </van-empty>

      <template v-else>
      <PageHeader :title="'我的待办'" :subtitle="`${auth.displayName} / ${roleLabel}`">
        <template #stats>
          <span
            v-for="stat in todoStats"
            :key="stat.label"
            class="stat-chip"
            :class="stat.tone === 'warning' ? 'stat-chip--warning' : 'stat-chip--primary'"
            data-testid="todo-summary"
          >{{ stat.label }} {{ stat.count }}</span>
        </template>
      </PageHeader>

      <HeroCard
        v-if="board?.hero?.route"
        :to="board.hero.route"
        :eyebrow="heroEyebrow"
        :title="`${board.hero.productName} · ${board.hero.versionCode}`"
        :subtitle="heroSubtitle"
        :action-label="board.hero.actionLabel"
        data-testid="todo-priority-task"
      />

      <div v-if="roleEntries.length" class="todo-entries">
        <RoleEntryGrid :entries="roleEntries" />
      </div>

      <div v-for="group in displayGroups" :key="group.key">
        <div class="section-header">
          <div class="section-title">{{ group.title }}</div>
          <span class="section-count">{{ group.items.length }}</span>
        </div>
        <TaskCard
          v-for="item in group.items"
          :key="`${group.key}-${item.id}`"
          :to="item.route"
          :title="item.productName"
          :meta="taskMeta(item)"
          :action-label="item.actionLabel"
          :status-symbol="taskSymbol(item)"
        />
      </div>

      <van-empty
        v-if="!loading && (board?.totalCount ?? 0) === 0"
        :description="emptyDescription"
      />
      </template>
    </van-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  fetchMobileTodoBoard,
  roleLabel as sharedRoleLabel,
  shouldFilterTasksByAssignee,
  type MobileTodoBoard,
  type MobileTodoItem,
} from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import HeroCard from "../components/HeroCard.vue";
import TaskCard from "../components/TaskCard.vue";
import RoleEntryGrid from "../components/RoleEntryGrid.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const loading = ref(false);
const board = ref<MobileTodoBoard | null>(null);
const loadError = ref("");

const roleLabel = computed(() => sharedRoleLabel(auth.role));

const heroEyebrow = computed(() => {
  const hero = board.value?.hero;
  if (!hero) return "继续处理";
  if (hero.kind === "shipment") {
    return hero.statusLabel === "待核价" ? "待生成核价" : "待寄样反馈";
  }
  if (hero.kind === "task") {
    if (hero.subtitle?.includes("打样")) return "打样中";
    if (hero.subtitle?.includes("测试")) return "待内部测试";
    if (hero.subtitle?.includes("接受")) return "待接受任务";
  }
  return "继续处理";
});

const heroSubtitle = computed(() => {
  const hero = board.value?.hero;
  if (!hero) return "";
  return [hero.subtitle ?? hero.statusLabel, hero.actionLabel].filter(Boolean).join(" · ");
});

const emptyDescription = computed(() => {
  if (auth.role === "RND_ASSISTANT") {
    return "暂无待办，可先录入需求或登记寄样";
  }
  return "暂无待办任务";
});

const todoStats = computed(() => {
  const groupStats: Array<{ label: string; count: number; tone: "primary" | "warning" }> = (board.value?.groups ?? []).slice(0, 3).map((group) => ({
    label: group.title.replace(/^待/, ""),
    count: group.items.length,
    tone: "primary" as const,
  }));
  if ((board.value?.overdueCount ?? 0) > 0 && groupStats.length < 3) {
    groupStats.push({ label: "逾期", count: board.value!.overdueCount, tone: "warning" as const });
  }
  while (groupStats.length < 3) groupStats.push({ label: groupStats.length === 0 ? "今日待办" : "处理中", count: board.value?.totalCount ?? 0, tone: "primary" as const });
  return groupStats;
});

const roleEntries = computed(() => {
  const entries: Array<{ label: string; title: string; desc: string; route: string }> = [];
  if (auth.role === "RND_ASSISTANT") {
    entries.push(
      {
        label: "研发内勤",
        title: "录入需求",
        desc: "提交样品清单",
        route: "/request/new",
      },
      {
        label: "研发内勤",
        title: "登记寄样/反馈",
        desc: "维护寄样与客户反馈表",
        route: "/shipments/record",
      },
    );
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
  if (auth.role === "TESTER" || auth.role === "QA_TESTER") {
    entries.push({
      label: "测试人员",
      title: "内部测试",
      desc: "在下方「待内部测试」列表处理",
      route: "/todo",
    });
  }
  if (auth.role === "FINANCE") {
    entries.push({
      label: "财务",
      title: "核价文件",
      desc: "查看并下载核价文件",
      route: "/pricing",
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

function taskSymbol(item: MobileTodoItem) {
  const source = `${item.statusLabel} ${item.subtitle ?? ""} ${item.actionLabel}`;
  if (source.includes("审核")) return "审";
  if (source.includes("核价") || source.includes("财务")) return "价";
  if (source.includes("测试")) return "测";
  return "样";
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const assigneeName = shouldFilterTasksByAssignee(auth.role) ? auth.displayName : undefined;
    board.value = await fetchMobileTodoBoard({
      role: auth.role,
      listTasks: (params) => api.task.list({ ...params, assigneeName }) as Promise<import("@rnd/shared").RndTask[]>,
      listShipments: (params) => api.shipment.list(params),
      listPricingFiles: (params) => api.shipment.pricingFiles(params) as Promise<import("@rnd/shared").PricingFileRecord[]>,
      listPricingReadyVersions: () => api.shipment.pricingReadyVersions(),
    });
  } catch (error) {
    board.value = null;
    loadError.value = error instanceof Error ? error.message : "待办加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
