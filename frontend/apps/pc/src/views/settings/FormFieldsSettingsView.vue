<template>
  <div>
    <a-page-header title="表单字段配置" sub-title="分别维护样品需求、实验单、测试单、寄样反馈和核价文件" />
    <a-alert type="info" show-icon style="margin-bottom: 16px" message="禁用字段会在后续表单渲染时隐藏；核心业务字段建议保留，避免影响历史资料和核价导出。" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="formCode" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'formName'">{{ formName(record.formCode) }}</template>
          <template v-else-if="column.key === 'action'"><a-button type="link" @click="openEdit(record)">配置字段</a-button></template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="editOpen" :title="`${formName(editingCode)} · 字段配置`" width="960">
      <a-space style="margin-bottom: 16px">
        <a-button type="dashed" @click="addField">添加字段</a-button>
        <span class="drawer-tip">可调整名称、类型、必填、显示顺序和提示文字。</span>
      </a-space>
      <a-table :columns="fieldColumns" :data-source="editFields" row-key="rowKey" size="small" :pagination="false" :scroll="{ x: 900 }">
        <template #bodyCell="{ column, record, index }">
          <a-input v-if="column.key === 'fieldCode'" v-model:value="record.fieldCode" :disabled="!record.isNew" />
          <a-input v-else-if="column.key === 'fieldLabel'" v-model:value="record.fieldLabel" />
          <a-select v-else-if="column.key === 'controlType'" v-model:value="record.controlType" style="width: 120px">
            <a-select-option v-for="type in controlTypes" :key="type" :value="type">{{ type }}</a-select-option>
          </a-select>
          <a-switch v-else-if="column.key === 'required'" v-model:checked="record.required" />
          <a-switch v-else-if="column.key === 'enabled'" v-model:checked="record.enabled" />
          <a-input-number v-else-if="column.key === 'sortOrder'" v-model:value="record.sortOrder" :min="1" />
          <a-input v-else-if="column.key === 'placeholder'" v-model:value="record.placeholder" placeholder="输入提示" />
          <a-space v-else-if="column.key === 'action'">
            <a-button size="small" @click="move(index, -1)" :disabled="index === 0">上移</a-button>
            <a-button size="small" @click="move(index, 1)" :disabled="index === editFields.length - 1">下移</a-button>
            <a-button danger type="link" size="small" @click="removeField(index)">删除</a-button>
          </a-space>
        </template>
      </a-table>
      <template #footer>
        <a-space><a-button @click="editOpen = false">取消</a-button><a-button type="primary" :loading="saving" @click="save">保存字段配置</a-button></a-space>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import type { FormFieldConfig, FormFieldConfigItem } from "@rnd/shared";
import { api } from "../../services/api";

type EditableField = FormFieldConfigItem & { rowKey: string; isNew?: boolean };

const formNames: Record<string, string> = {
  SAMPLE_REQUEST: "样品需求单",
  EXPERIMENT_FORM: "打样实验单",
  TEST_RECORD: "内部测试单",
  SHIPMENT_FEEDBACK: "寄样反馈单",
  PRICING_FILE: "核价文件",
};
const controlTypes = ["TEXT", "TEXTAREA", "NUMBER", "SELECT", "USER_SELECT", "TABLE", "UPLOAD"];
const loading = ref(false);
const saving = ref(false);
const rows = ref<Array<FormFieldConfig & { fieldCount: number }>>([]);
const editOpen = ref(false);
const editingCode = ref("");
const editFields = ref<EditableField[]>([]);
const columns = [
  { title: "表单", key: "formName" },
  { title: "表单编码", dataIndex: "formCode", key: "formCode" },
  { title: "字段数", dataIndex: "fieldCount", key: "fieldCount" },
  { title: "操作", key: "action", width: 120 },
];
const fieldColumns = [
  { title: "字段编码", key: "fieldCode", width: 145 }, { title: "字段名称", key: "fieldLabel", width: 140 },
  { title: "控件", key: "controlType", width: 130 }, { title: "必填", key: "required", width: 75 },
  { title: "显示", key: "enabled", width: 75 }, { title: "顺序", key: "sortOrder", width: 85 },
  { title: "提示", key: "placeholder", width: 180 }, { title: "操作", key: "action", width: 190 },
];
function formName(formCode: string) { return formNames[formCode] ?? formCode; }

async function openEdit(record: FormFieldConfig & { fieldCount: number }) {
  editingCode.value = record.formCode;
  try {
    const detail = await api.settings.formField(record.formCode);
    editFields.value = detail.fields.map((field, index) => ({ ...field, rowKey: field.id ?? `${field.fieldCode}-${index}` }));
    editOpen.value = true;
  } catch (error) { message.error(error instanceof Error ? error.message : "读取字段失败"); }
}
function addField() {
  const order = editFields.value.length + 1;
  editFields.value.push({ rowKey: `new-${Date.now()}`, isNew: true, fieldCode: "", fieldLabel: "", controlType: "TEXT", required: false, enabled: true, sortOrder: order });
}
function removeField(index: number) { editFields.value.splice(index, 1); normalizeOrder(); }
function move(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= editFields.value.length) return;
  const [item] = editFields.value.splice(index, 1);
  editFields.value.splice(target, 0, item);
  normalizeOrder();
}
function normalizeOrder() { editFields.value.forEach((field, index) => { field.sortOrder = (index + 1) * 10; }); }
async function save() {
  if (editFields.value.some((field) => !field.fieldCode.trim() || !field.fieldLabel.trim())) { message.warning("请补齐字段编码和字段名称"); return; }
  saving.value = true;
  try {
    await api.settings.saveFormField(editingCode.value, { fields: editFields.value.map(({ rowKey, isNew, id, formCode, updatedAt, ...field }) => field) });
    message.success("字段配置已保存");
    editOpen.value = false;
    await load();
  } catch (error) { message.error(error instanceof Error ? error.message : "保存失败"); }
  finally { saving.value = false; }
}
async function load() {
  loading.value = true;
  try {
    const data = await api.settings.formFields();
    rows.value = data.map((item) => ({ ...item, fieldCount: item.fields?.length ?? 0 }));
  } finally { loading.value = false; }
}
onMounted(load);
</script>

<style scoped>.drawer-tip { color: #8c8c8c; font-size: 13px; }</style>
