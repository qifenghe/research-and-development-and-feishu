<template>
  <div>
    <a-page-header
      :title="detail?.task.productName ?? '研发任务详情'"
      sub-title="左侧查看样品项目信息，右侧按流程完成打样、测试与反馈"
      @back="router.back()"
    />

    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-alert
          v-if="permissionHint"
          :type="auth.role === 'RND_ASSISTANT' && detail.task.status === 'SAMPLING' ? 'info' : 'warning'"
          show-icon
          :message="permissionHint"
          style="margin-bottom: 16px"
        />

        <a-row :gutter="16">
          <a-col :span="15">
            <a-card title="样品项目" class="page-card">
              <a-descriptions bordered size="small" :column="2">
                <a-descriptions-item v-for="row in summaryRows" :key="row.label" :label="row.label">
                  {{ row.value }}
                </a-descriptions-item>
              </a-descriptions>
            </a-card>

            <a-card
              v-for="group in resolvedGroups"
              :key="group.title"
              :title="group.title"
              class="page-card"
            >
              <a-descriptions bordered size="small" :column="group.title === '实验单' ? 1 : 2">
                <a-descriptions-item v-for="field in group.fields" :key="field.label" :label="field.label">
                  {{ field.value }}
                </a-descriptions-item>
              </a-descriptions>
            </a-card>

            <a-card title="版本与归档" class="page-card">
              <a-space>
                <a-button @click="openHistory">查看实验单历史版本</a-button>
                <a-button v-if="relatedShipment" @click="router.push(`/shipment/${relatedShipment.id}`)">
                  查看寄样详情
                </a-button>
              </a-space>
            </a-card>
          </a-col>

          <a-col :span="9">
            <TaskWorkflowPanel
              :detail="detail"
              :operator-name="auth.displayName"
              :role="auth.role"
              :shipment="relatedShipment"
              @open-experiment="openExperimentForm"
              @submit-sampling="submitSamplingRecord"
              @open-test="openTestForm"
              @create-shipment="openFeedbackFlow"
              @open-feedback="openFeedbackFlow"
              @assign="openAssignModal"
              @accept="acceptTask"
            />
          </a-col>
        </a-row>
      </template>
    </a-spin>

    <a-modal
      v-model:open="assignModalOpen"
      title="分发研发任务"
      ok-text="确认分发"
      :confirm-loading="assignSubmitting"
      @ok="submitAssign"
    >
      <a-form layout="vertical">
        <a-form-item label="研发人员" required>
          <a-select
            v-model:value="assignForm.assigneeName"
            show-search
            placeholder="选择具体研发人员"
            :options="engineerOptions"
            :filter-option="filterEngineer"
          />
        </a-form-item>
        <a-form-item label="截止日期" required>
          <a-date-picker v-model:value="assignForm.dueDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Modal, message } from "ant-design-vue";
import {
  buildTaskSummaryRows,
  isRndAssigneeRole,
  resolveDetailFieldGroups,
  taskStatusLabel,
  type RndTaskDetailView,
  type ShipmentRecord,
  type UserAccount,
} from "@rnd/shared";
import TaskWorkflowPanel from "../../components/TaskWorkflowPanel.vue";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";
import { useAdminContext } from "../../composables/adminContext";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { setPanel, clearPanel } = useAdminContext();
const loading = ref(false);
const detail = ref<RndTaskDetailView | null>(null);
const relatedShipment = ref<ShipmentRecord | null>(null);
const engineerOptions = ref<Array<{ label: string; value: string }>>([]);
const assignModalOpen = ref(false);
const assignSubmitting = ref(false);
const assignForm = reactive({
  assigneeName: "",
  dueDate: "",
});

const resolvedGroups = computed(() => (detail.value ? resolveDetailFieldGroups(detail.value) : []));
const summaryRows = computed(() => (detail.value ? buildTaskSummaryRows(detail.value) : []));

const permissionHint = computed(() => {
  if (!detail.value) return "";
  const { task } = detail.value;
  if ((task.status === "PENDING_ACCEPTANCE" || task.status === "SAMPLING")
    && ["RND_ENGINEER", "RND_DIRECTOR", "RND"].includes(auth.role)
    && task.assigneeName
    && task.assigneeName !== auth.displayName) {
    return `该任务已分发给 ${task.assigneeName}，当前账号 ${auth.displayName} 不能代为操作。`;
  }
  if (task.status === "PENDING_ASSIGNMENT" && auth.role !== "RND_DIRECTOR") {
    return "该任务待研发总监分发，当前角色仅可查看项目信息。";
  }
  if (task.status === "SAMPLING" && auth.role === "RND_ASSISTANT") {
    if (!detail.value.currentExperimentForm) {
      return "研发尚未开始填写实验单，暂无法通知内部测试。";
    }
    if (detail.value.currentExperimentForm.status !== "DRAFT") {
      return "实验单已提交测试，内勤无需再次通知。";
    }
    return `研发负责人 ${task.assigneeName || "—"} 已保存草稿，内勤可代为「通知内部测试」。`;
  }
  return "";
});

async function load() {
  loading.value = true;
  try {
    detail.value = await api.task.detail(String(route.params.id), auth.role, auth.displayName);
    if (detail.value) {
      setPanel({
        title: detail.value.task.productName,
        subtitle: `${detail.value.task.sampleNo} · ${detail.value.task.versionCode}`,
        statusLabel: taskStatusLabel(detail.value.task.status),
        statusTone: detail.value.task.status === "SAMPLING" ? "success" : "processing",
        rows: buildTaskSummaryRows(detail.value).slice(0, 6),
      });
      const shipments = await api.shipment.list({ keyword: detail.value.task.sampleNo });
      relatedShipment.value =
        shipments.find((item) => item.versionId === detail.value!.version.id) ?? shipments[0] ?? null;
    }
  } finally {
    loading.value = false;
  }
}

async function loadEngineers() {
  const users = await api.settings.users();
  engineerOptions.value = users
    .filter((user: UserAccount) => user.status === "ACTIVE" && isRndAssigneeRole(user.role))
    .map((user) => ({
      label: `${user.name} · ${user.departmentName || "研发部"}`,
      value: user.name,
    }));
}

function filterEngineer(input: string, option?: { label: string; value: string }) {
  return (option?.label ?? "").toLowerCase().includes(input.toLowerCase());
}

function openExperimentForm() {
  router.push(`/rnd/tasks/${route.params.id}/experiment`);
}

function openTestForm() {
  router.push(`/rnd/tasks/${route.params.id}/test`);
}

function openFeedbackFlow() {
  router.push(`/rnd/tasks/${route.params.id}/feedback`);
}

function openHistory() {
  if (!detail.value) return;
  router.push({
    path: `/rnd/history/${detail.value.version.id}`,
    query: {
      projectId: detail.value.version.projectId,
      productName: detail.value.task.productName,
      taskId: detail.value.task.id,
    },
  });
}

function openAssignModal() {
  assignForm.assigneeName = "";
  assignForm.dueDate = "";
  assignModalOpen.value = true;
}

async function acceptTask() {
  try {
    await api.task.accept(String(route.params.id), auth.displayName);
    message.success("已接受任务，请开始填写打样实验单");
    openExperimentForm();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "接受失败");
  }
}

async function submitSamplingRecord() {
  const experimentId = detail.value?.currentExperimentForm?.id;
  if (!experimentId) {
    message.warning("请先填写并保存打样实验单");
    openExperimentForm();
    return;
  }
  Modal.confirm({
    title: "提交打样记录",
    content: "提交后将通知内部测试人员，当前实验单进入待测试状态，是否继续？",
    okText: "确认提交",
    onOk: async () => {
      try {
        await api.task.submitExperimentForTest(experimentId, auth.displayName);
        message.success("打样记录已提交，已通知内部测试");
        await load();
      } catch (error) {
        message.error(error instanceof Error ? error.message : "提交失败");
      }
    },
  });
}

async function submitAssign() {
  if (!assignForm.assigneeName || !assignForm.dueDate) {
    message.warning("请选择研发人员并填写截止日期");
    return;
  }
  assignSubmitting.value = true;
  try {
    await api.task.assign(String(route.params.id), assignForm.assigneeName, assignForm.dueDate);
    assignModalOpen.value = false;
    message.success("任务已分发");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "分发失败");
  } finally {
    assignSubmitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([load(), loadEngineers()]);
});

onUnmounted(() => {
  clearPanel();
});
</script>
