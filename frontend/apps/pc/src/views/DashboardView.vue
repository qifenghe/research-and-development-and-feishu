<template>
  <div>
    <a-page-header title="工作台" :sub-title="dashboardSubtitle" />

    <a-spin :spinning="loading">
      <a-row v-if="primaryActions.length" :gutter="16" style="margin-bottom: 16px">
        <a-col v-for="link in primaryActions" :key="link.path" :span="8">
          <a-card class="quick-action-card" hoverable @click="router.push(link.path)">
            <a-card-meta :title="link.label" :description="link.description" />
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="[16, 16]">
        <a-col v-for="item in visibleStats" :key="item.key" :span="6">
          <a-card class="stat-card" :bordered="false">
            <a-statistic :title="item.label" :value="item.value" :value-style="{ color: item.color }" />
          </a-card>
        </a-col>
      </a-row>

      <a-card title="最近待办任务" class="page-card" style="margin-top: 16px">
        <a-table
          :columns="taskColumns"
          :data-source="overview?.recentTasks ?? []"
          row-key="taskId"
          :pagination="false"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="taskStatusColor(record.status)">
                {{ taskStatusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="router.push(`/rnd/tasks/${record.taskId}`)">
                {{ taskActionLabel(record.status) }}
              </a-button>
            </template>
          </template>
        </a-table>
        <a-empty v-if="!loading && !(overview?.recentTasks?.length)" description="暂无进行中的研发任务" />
      </a-card>

      <a-card v-if="showPricingSection" title="待核价文件" class="page-card">
        <a-table
          :columns="pricingColumns"
          :data-source="overview?.pendingPricingFiles ?? []"
          row-key="pricingFileId"
          :pagination="false"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag color="processing">{{ pricingStatusLabel(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="router.push(`/pricing/${record.pricingFileId}`)">
                查看核价
              </a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-row v-if="drilldowns.length" :gutter="16" style="margin-top: 8px">
        <a-col v-for="link in drilldowns" :key="link.path" :span="6">
          <a-card class="module-link-card" hoverable @click="router.push(link.path)">
            <a-card-meta :title="link.label" :description="link.description" />
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { canAccessRoute, roleLabel, RND_TASK_STATUS_LABELS, type DashboardOverview, type RndTaskStatus } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const overview = ref<DashboardOverview | null>(null);

const dashboardSubtitle = computed(() => {
  const label = roleLabel(auth.role);
  const hints: Record<string, string> = {
    RND_ASSISTANT: "录入样品需求、跟进寄样反馈和核价文件",
    RND_DIRECTOR: "审核需求、分发任务，也可亲自打样",
    RND_ENGINEER: "接受任务、填写实验单并提交内部测试",
    TESTER: "处理待内部测试任务",
    QA_TESTER: "处理待内部测试任务",
    FINANCE: "查看待核价文件并处理通知",
  };
  return `${label} · ${hints[auth.role ?? ""] ?? "查看样品研发进度"}`;
});

type StatItem = {
  key: string;
  label: string;
  value: number;
  color: string;
  roles: string[];
};

const allStats = computed((): StatItem[] => {
  const data = overview.value;
  if (!data) return [];
  return [
    { key: "review", label: "待总监审核", value: data.pendingReviewCount, color: "#f59e0b", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER"] },
    { key: "assign", label: "任务池待分发", value: data.pendingAssignmentCount, color: "#6d5bd0", roles: ["RND_DIRECTOR", "MANAGER"] },
    { key: "rnd", label: "研发处理中", value: data.pendingAcceptanceCount + data.samplingCount, color: "#16a34a", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "MANAGER"] },
    { key: "test", label: "待内部测试", value: data.pendingTestCount, color: "#0d9488", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER"] },
    { key: "completed", label: "样品已完成", value: data.completedSampleCount, color: "#16a34a", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER"] },
    { key: "pricing", label: "待生成核价", value: data.pendingPricingCount, color: "#ef4444", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "FINANCE", "MANAGER"] },
    { key: "finance", label: "已通知财务", value: data.financeNotifiedCount, color: "#246bfe", roles: ["RND_ASSISTANT", "FINANCE", "MANAGER"] },
    { key: "stopped", label: "停止/废弃", value: data.stoppedCount, color: "#ef4444", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER"] },
  ];
});

const visibleStats = computed(() => {
  const role = auth.role ?? "";
  return allStats.value.filter((item) => item.roles.includes(role) || ["ADMIN", "SYSTEM_ADMIN"].includes(role));
});

const actionCatalog = [
  { label: "寄样反馈", description: "查看待反馈样品，登记客户是否通过或继续打样", path: "/shipment/list", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "MANAGER"] },
  { label: "录入客户反馈", description: "登记寄样并录入客户试吃后的通过、复打样或停止结论", path: "/shipment/record", roles: ["RND_ASSISTANT", "RND_DIRECTOR"] },
  { label: "录入样品需求", description: "研发内勤录入业务员或客户提出的样品清单", path: "/demand/new", roles: ["RND_ASSISTANT", "RND_DIRECTOR"] },
  { label: "核价文件", description: "样品通过后生成核价文件，审核通过后自动移交财务", path: "/pricing/list", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER"] },
  { label: "需求审核", description: "研发总监判断资料是否完整，通过后进入任务池", path: "/demand/review", roles: ["RND_DIRECTOR", "MANAGER"] },
  { label: "任务分发", description: "研发总监分发给具体研发人员", path: "/rnd/assign", roles: ["RND_DIRECTOR"] },
  { label: "我的打样任务", description: "接受任务、填写实验单并提交内部测试", path: "/rnd/my-tasks", roles: ["RND_ENGINEER", "RND_DIRECTOR"] },
  { label: "内部测试待办", description: "任何登录人员均可填写并提交测试结论", path: "/rnd/pending-tests", roles: ["RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "TESTER", "QA_TESTER", "FINANCE", "MANAGER"] },
];

const primaryActions = computed(() => {
  const role = auth.role ?? "";
  return actionCatalog
    .filter((item) => canAccessRoute(role, item.path, "pc"))
    .sort((left, right) => {
      const leftPriority = left.roles.indexOf(role);
      const rightPriority = right.roles.indexOf(role);
      const leftScore = leftPriority >= 0 ? leftPriority : 99;
      const rightScore = rightPriority >= 0 ? rightPriority : 99;
      return leftScore - rightScore;
    })
    .slice(0, 3);
});

const drilldownCatalog = [
  { label: "已提交需求", description: "查看需求审核进度", path: "/demand/review" },
  { label: "任务池进度", description: "查看需求通过后的研发任务状态", path: "/rnd/pool" },
  { label: "停止项目池", description: "查看停止或废弃的样品记录", path: "/rnd/stopped" },
  { label: "文件归档", description: "查看需求、实验、测试、寄样、核价归档", path: "/archive" },
];

const drilldowns = computed(() =>
  drilldownCatalog.filter((item) => canAccessRoute(auth.role, item.path, "pc")),
);

const showPricingSection = computed(() =>
  canAccessRoute(auth.role, "/pricing/list", "pc") && (overview.value?.pendingPricingFiles?.length ?? 0) > 0,
);

const taskColumns = [
  { title: "任务编号", dataIndex: "taskId", key: "taskId", width: 120 },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode", width: 80 },
  { title: "负责人", dataIndex: "assigneeName", key: "assigneeName", width: 100 },
  { title: "状态", key: "status", width: 110 },
  { title: "操作", key: "action", width: 100 },
];

const pricingColumns = [
  { title: "文件编号", dataIndex: "pricingFileId", key: "pricingFileId" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "版本", dataIndex: "versionCode", key: "versionCode", width: 80 },
  { title: "状态", key: "status", width: 110 },
  { title: "操作", key: "action", width: 100 },
];

function taskStatusLabel(status: string) {
  return RND_TASK_STATUS_LABELS[status as RndTaskStatus] ?? status;
}

function taskStatusColor(status: string) {
  const map: Record<string, string> = {
    PENDING_ACCEPTANCE: "orange",
    SAMPLING: "green",
    PENDING_TEST: "processing",
    PENDING_ASSIGNMENT: "gold",
    COMPLETED: "success",
  };
  return map[status] ?? "default";
}

function taskActionLabel(status: string) {
  if (status === "PENDING_ACCEPTANCE") return "去接单";
  if (status === "SAMPLING") return auth.role === "RND_ASSISTANT" ? "去跟进" : "去打样";
  if (status === "PENDING_TEST") return "去测试";
  return "查看";
}

function pricingStatusLabel(status: string) {
  return status === "GENERATED" ? "已生成" : status === "FINANCE_NOTIFIED" ? "已通知财务" : status;
}

onMounted(async () => {
  loading.value = true;
  try {
    overview.value = await api.dashboard.overview();
  } finally {
    loading.value = false;
  }
});
</script>
