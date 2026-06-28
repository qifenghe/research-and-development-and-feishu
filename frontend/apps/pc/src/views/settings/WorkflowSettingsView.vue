<template>
  <div>
    <a-page-header title="流程状态配置" sub-title="维护状态、节点、流转按钮和是否需要审批/通知">
      <template #extra>
        <a-button @click="initDefaults">初始化默认流程</a-button>
      </template>
    </a-page-header>
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="workflowCode" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" @click="openEdit(record)">编辑规则</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="editOpen" :title="`编辑流程：${editingCode}`" width="720" @close="editOpen = false">
      <a-form layout="vertical">
        <a-card
          v-for="(rule, index) in editRules"
          :key="index"
          size="small"
          :title="`规则 ${index + 1}`"
          style="margin-bottom: 12px"
        >
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="当前状态">
                <a-input v-model:value="rule.currentStatus" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="下一状态">
                <a-input v-model:value="rule.nextStatus" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="动作编码">
                <a-input v-model:value="rule.actionCode" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="按钮文案">
                <a-input v-model:value="rule.actionLabel" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="启用">
                <a-switch v-model:checked="rule.enabled" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="飞书通知">
                <a-switch v-model:checked="rule.notifyFeishu" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="排序">
                <a-input-number v-model:value="rule.sortOrder" :min="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="通知角色">
                <a-input v-model:value="rule.notifyRole" placeholder="如 RND_ENGINEER" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="备注">
                <a-input v-model:value="rule.remark" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-card>
        <a-button block @click="addRule">+ 添加规则</a-button>
      </a-form>
      <template #footer>
        <a-button type="primary" :loading="saving" @click="save">保存</a-button>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import { api } from "../../services/api";

interface WorkflowRuleForm {
  currentStatus: string;
  actionCode: string;
  actionLabel: string;
  nextStatus: string;
  enabled: boolean;
  notifyFeishu: boolean;
  notifyRole: string;
  sortOrder: number;
  remark: string;
}

interface WorkflowRow {
  workflowCode: string;
  workflowName: string;
  ruleCount: number;
}

const loading = ref(false);
const saving = ref(false);
const rows = ref<WorkflowRow[]>([]);
const editOpen = ref(false);
const editingCode = ref("");
const editRules = ref<WorkflowRuleForm[]>([]);

const workflowNameMap: Record<string, string> = {
  SAMPLE_RND_FLOW: "样品研发主流程",
  CONFIG_SAMPLE_FLOW: "可配置样品流程",
};

const columns = [
  { title: "流程编码", dataIndex: "workflowCode", key: "workflowCode" },
  { title: "流程名称", dataIndex: "workflowName", key: "workflowName" },
  { title: "规则数", dataIndex: "ruleCount", key: "ruleCount" },
  { title: "操作", key: "action" },
];

function emptyRule(): WorkflowRuleForm {
  return {
    currentStatus: "",
    actionCode: "",
    actionLabel: "",
    nextStatus: "",
    enabled: true,
    notifyFeishu: false,
    notifyRole: "",
    sortOrder: (editRules.value.length + 1) * 10,
    remark: "",
  };
}

function addRule() {
  editRules.value.push(emptyRule());
}

async function openEdit(record: WorkflowRow) {
  editingCode.value = record.workflowCode;
  const detail = await api.settings.workflow(record.workflowCode);
  editRules.value = (detail.rules ?? []).map((rule: Record<string, unknown>) => ({
    currentStatus: String(rule.currentStatus ?? rule.fromStatus ?? ""),
    actionCode: String(rule.actionCode ?? ""),
    actionLabel: String(rule.actionLabel ?? ""),
    nextStatus: String(rule.nextStatus ?? rule.toStatus ?? ""),
    enabled: rule.enabled !== false,
    notifyFeishu: Boolean(rule.notifyFeishu ?? rule.notifyRequired),
    notifyRole: String(rule.notifyRole ?? ""),
    sortOrder: Number(rule.sortOrder ?? 0),
    remark: String(rule.remark ?? ""),
  }));
  if (!editRules.value.length) {
    editRules.value = [emptyRule()];
  }
  editOpen.value = true;
}

async function save() {
  saving.value = true;
  try {
    await api.settings.saveWorkflow(editingCode.value, { rules: editRules.value });
    message.success("流程规则已保存");
    editOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function initDefaults() {
  await api.settings.initWorkflowDefaults();
  message.success("默认流程已初始化");
  await load();
}

async function load() {
  loading.value = true;
  try {
    const data = await api.settings.workflows();
    rows.value = data.map((item: { workflowCode: string; rules?: unknown[] }) => ({
      workflowCode: item.workflowCode,
      workflowName: workflowNameMap[item.workflowCode] ?? item.workflowCode,
      ruleCount: item.rules?.length ?? 0,
    }));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
