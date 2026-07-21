<template>
  <div>
    <a-page-header title="表单字段配置" sub-title="维护需求单、实验单、测试单、寄样反馈、核价文件字段" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="formCode" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" @click="openEdit(record)">查看/编辑</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="editOpen" :title="`表单：${editingCode}`" width="640">
      <a-table :columns="fieldColumns" :data-source="editFields" row-key="fieldCode" size="small" />
      <template #footer>
        <a-button type="primary" @click="save">保存字段配置</a-button>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import type { FormFieldConfig } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<Array<FormFieldConfig & { fieldCount: number }>>([]);
const editOpen = ref(false);
const editingCode = ref("");
const editFields = ref<Array<Record<string, unknown>>>([]);

const columns = [
  { title: "表单编码", dataIndex: "formCode", key: "formCode" },
  { title: "表单名称", dataIndex: "formName", key: "formName" },
  { title: "字段数", dataIndex: "fieldCount", key: "fieldCount" },
  { title: "操作", key: "action" },
];

const fieldColumns = [
  { title: "字段", dataIndex: "fieldCode", key: "fieldCode" },
  { title: "名称", dataIndex: "fieldName", key: "fieldName" },
  { title: "类型", dataIndex: "fieldType", key: "fieldType" },
  { title: "必填", dataIndex: "required", key: "required" },
];

async function openEdit(record: FormFieldConfig & { fieldCount: number }) {
  editingCode.value = record.formCode;
  const detail = await api.settings.formField(record.formCode);
  editFields.value = (detail.fields ?? []) as unknown as Array<Record<string, unknown>>;
  editOpen.value = true;
}

async function save() {
  await api.settings.saveFormField(editingCode.value, { fields: editFields.value });
  message.success("表单字段已保存");
  editOpen.value = false;
  await load();
}

async function load() {
  loading.value = true;
  try {
    const data = await api.settings.formFields();
    rows.value = data.map((item: FormFieldConfig) => ({ ...item, fieldCount: item.fields?.length ?? 0 }));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
