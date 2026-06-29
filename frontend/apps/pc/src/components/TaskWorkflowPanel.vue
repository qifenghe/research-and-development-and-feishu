<template>
  <div class="workflow-panel">
    <div class="workflow-panel__header">
      <h3>打样与测试流程</h3>
      <p>按顺序完成打样录入、提交记录、内部测试与外部反馈</p>
    </div>

    <a-steps direction="vertical" :current="currentStep" size="small" class="workflow-steps">
      <a-step
        v-for="step in steps"
        :key="step.key"
        :title="step.title"
        :description="step.description"
        :status="step.status"
      />
    </a-steps>

    <div class="workflow-panel__actions">
      <a-card
        v-for="block in actionBlocks"
        :key="block.key"
        size="small"
        :class="['workflow-block', { 'workflow-block--active': block.active }]"
      >
        <template #title>
          <a-space>
            <span>{{ block.title }}</span>
            <a-tag v-if="block.badge" :color="block.badgeColor">{{ block.badge }}</a-tag>
          </a-space>
        </template>
        <p class="workflow-block__desc">{{ block.description }}</p>
        <a-space direction="vertical" style="width: 100%">
          <a-button
            v-for="btn in block.buttons"
            :key="btn.label"
            :type="btn.primary ? 'primary' : 'default'"
            block
            :disabled="btn.disabled"
            @click="btn.onClick()"
          >
            {{ btn.label }}
          </a-button>
        </a-space>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { RndTaskDetailView, ShipmentRecord } from "@rnd/shared";
import { canEditExperiment, canNotifyInternalTest, canOperateTask } from "@rnd/shared";

export interface WorkflowButton {
  label: string;
  primary?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

export interface WorkflowActionBlock {
  key: string;
  title: string;
  description: string;
  badge?: string;
  badgeColor?: string;
  active: boolean;
  buttons: WorkflowButton[];
}

const props = defineProps<{
  detail: RndTaskDetailView;
  operatorName: string;
  role: string;
  shipment: ShipmentRecord | null;
}>();

const emit = defineEmits<{
  openExperiment: [];
  submitSampling: [];
  openTest: [];
  createShipment: [];
  openFeedback: [];
  assign: [];
  accept: [];
}>();

const engineerCanOperate = computed(() =>
  canOperateTask(props.detail, props.operatorName, props.role),
);

const canEditExp = computed(() =>
  canEditExperiment(props.detail, props.operatorName, props.role),
);

const canNotifyTest = computed(() =>
  canNotifyInternalTest(props.detail, props.operatorName, props.role),
);

const isAssistant = computed(() => props.role === "RND_ASSISTANT");

const experiment = computed(() => props.detail.currentExperimentForm);
const test = computed(() => props.detail.currentTestAssignment);
const task = computed(() => props.detail.task);

const steps = computed(() => {
  const status = task.value.status;
  const expStatus = experiment.value?.status;
  const shipStatus = props.shipment?.status;

  const stepStatus = (done: boolean, active: boolean) => {
    if (done) return "finish" as const;
    if (active) return "process" as const;
    return "wait" as const;
  };

  const samplingDone = expStatus === "SUBMITTED_FOR_TEST" || expStatus === "LOCKED";
  const testDone = status === "COMPLETED" || expStatus === "LOCKED";
  const shipped = Boolean(props.shipment);
  const feedbackDone = shipStatus === "FEEDBACK_PASSED" || shipStatus === "FEEDBACK_FAILED_RESAMPLE" || shipStatus === "STOPPED";

  return [
    {
      key: "experiment",
      title: "填写打样实验单",
      description: experiment.value ? `已录入 ${experiment.value.materials?.length ?? 0} 行物料` : "现场录入原辅料与工序",
      status: stepStatus(samplingDone, status === "SAMPLING"),
    },
    {
      key: "submit",
      title: "提交打样记录",
      description: samplingDone ? "已提交内部测试" : "保存后提交给测试人员",
      status: stepStatus(samplingDone, status === "SAMPLING" && expStatus === "DRAFT"),
    },
    {
      key: "internal-test",
      title: "内部测试评价",
      description: testDone ? "测试已完成" : "口味、口感、复热等评价",
      status: stepStatus(testDone, status === "PENDING_TEST"),
    },
    {
      key: "shipment",
      title: "寄样登记",
      description: shipped ? `快递 ${props.shipment?.trackingNo || "已登记"}` : "登记快递与客户收件信息",
      status: stepStatus(shipped, status === "COMPLETED" && !shipped),
    },
    {
      key: "feedback",
      title: "外部反馈意见",
      description: feedbackDone ? "客户反馈已录入" : "客户/业务试吃结论",
      status: stepStatus(feedbackDone, shipped && shipStatus === "SHIPPED"),
    },
  ];
});

const currentStep = computed(() => {
  const idx = steps.value.findIndex((s) => s.status === "process");
  if (idx >= 0) return idx;
  const lastDone = [...steps.value].reverse().findIndex((s) => s.status === "finish");
  return lastDone >= 0 ? steps.value.length - 1 - lastDone : 0;
});

const actionBlocks = computed((): WorkflowActionBlock[] => {
  const blocks: WorkflowActionBlock[] = [];
  const status = task.value.status;
  const expStatus = experiment.value?.status;

  if (status === "PENDING_ASSIGNMENT" && props.role === "RND_DIRECTOR") {
    blocks.push({
      key: "assign",
      title: "任务分发",
      description: "选择研发人员并设置截止日期",
      active: true,
      badge: "待分发",
      badgeColor: "orange",
      buttons: [{ label: "分发给研发人员", primary: true, onClick: () => emit("assign") }],
    });
    return blocks;
  }

  if (status === "PENDING_ACCEPTANCE" && engineerCanOperate.value) {
    blocks.push({
      key: "accept",
      title: "接受任务",
      description: "确认接受后开始打样",
      active: true,
      badge: "待接受",
      badgeColor: "blue",
      buttons: [{ label: "接受研发任务", primary: true, onClick: () => emit("accept") }],
    });
    return blocks;
  }

  blocks.push({
    key: "experiment",
    title: "① 打样实验单",
    description: isAssistant.value
      ? "查看研发已录入的实验单内容"
      : "录入原辅料、工序称重、出成与现场照片",
    active: status === "SAMPLING",
    badge: expStatus === "DRAFT" ? "草稿" : expStatus === "LOCKED" ? "已锁定" : undefined,
    badgeColor: expStatus === "DRAFT" ? "processing" : "success",
    buttons: [
      {
        label: canEditExp.value
          ? (experiment.value ? "继续填写打样实验单" : "开始填写打样实验单")
          : "查看打样实验单",
        primary: status === "SAMPLING" && (canEditExp.value || isAssistant.value),
        disabled: status !== "SAMPLING" || (!canEditExp.value && !isAssistant.value),
        onClick: () => emit("openExperiment"),
      },
    ],
  });

  blocks.push({
    key: "submit",
    title: "② 提交打样记录",
    description: isAssistant.value
      ? "研发保存草稿后，内勤可代为通知内部测试"
      : "将实验单提交给内部测试，提交后研发不可再改",
    active: status === "SAMPLING" && expStatus === "DRAFT" && canNotifyTest.value,
    badge: expStatus === "SUBMITTED_FOR_TEST" ? "已提交" : undefined,
    badgeColor: "blue",
    buttons: [
      {
        label: isAssistant.value ? "通知内部测试" : "提交打样记录并通知测试",
        primary: true,
        disabled: status !== "SAMPLING" || expStatus !== "DRAFT" || !canNotifyTest.value,
        onClick: () => emit("submitSampling"),
      },
    ],
  });

  blocks.push({
    key: "internal-test",
    title: "③ 内部测试评价单",
    description: "测试人员填写口味、口感、出水、复热与测试结论",
    active: status === "PENDING_TEST",
    badge: status === "PENDING_TEST" ? "待测试" : expStatus === "LOCKED" ? "已通过" : undefined,
    badgeColor: status === "PENDING_TEST" ? "purple" : "green",
    buttons: [
      {
        label: "填写内部测试评价",
        primary: status === "PENDING_TEST" && ["TESTER", "QA_TESTER"].includes(props.role),
        disabled: status !== "PENDING_TEST",
        onClick: () => emit("openTest"),
      },
    ],
  });

  blocks.push({
    key: "shipment",
    title: "④ 寄样登记",
    description: "内部测试通过后登记寄样快递信息",
    active: status === "COMPLETED" && !props.shipment,
    badge: props.shipment ? "已寄样" : undefined,
    badgeColor: "cyan",
    buttons: [
      {
        label: props.shipment ? "填写外部反馈" : "登记寄样",
        primary: (status === "COMPLETED" && !props.shipment && props.role === "RND_ASSISTANT")
          || (props.shipment?.status === "SHIPPED" && props.role === "RND_ASSISTANT"),
        disabled: status !== "COMPLETED" && !props.shipment,
        onClick: () => (props.shipment ? emit("openFeedback") : emit("createShipment")),
      },
    ],
  });

  blocks.push({
    key: "feedback",
    title: "⑤ 外部反馈意见",
    description: "录入客户或业务试吃后的通过/复打样/停止结论",
    active: props.shipment?.status === "SHIPPED",
    badge: props.shipment?.status === "FEEDBACK_PASSED" ? "客户通过" : undefined,
    badgeColor: "green",
    buttons: [
      {
        label: "填写外部反馈意见",
        primary: props.shipment?.status === "SHIPPED" && props.role === "RND_ASSISTANT",
        disabled: !props.shipment || props.shipment.status !== "SHIPPED",
        onClick: () => emit("openFeedback"),
      },
    ],
  });

  return blocks;
});
</script>

<style scoped>
.workflow-panel {
  position: sticky;
  top: 16px;
}

.workflow-panel__header h3 {
  font-size: 16px;
  font-weight: 800;
  margin: 0 0 4px;
}

.workflow-panel__header p {
  color: #64748b;
  font-size: 12px;
  margin: 0 0 16px;
}

.workflow-steps {
  margin-bottom: 16px;
}

.workflow-panel__actions {
  display: grid;
  gap: 12px;
}

.workflow-block {
  border: 1px solid #e5e7eb;
}

.workflow-block--active {
  border-color: #246bfe;
  box-shadow: 0 0 0 1px rgba(36, 107, 254, 0.15);
}

.workflow-block__desc {
  color: #64748b;
  font-size: 12px;
  margin: 0 0 12px;
}
</style>
