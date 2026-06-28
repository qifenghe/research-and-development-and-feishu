<template>
  <div>
    <a-page-header title="核价模板配置" sub-title="维护 Excel 核价文件导出模板版本" />
    <a-table :columns="columns" :data-source="rows" row-key="templateCode" :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-button type="link" @click="openEdit(record)">编辑</a-button>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="editOpen" title="编辑模板" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="模板名称">
          <a-input v-model:value="form.templateName" />
        </a-form-item>
        <a-form-item label="文件路径">
          <a-input v-model:value="form.filePath" />
        </a-form-item>
        <a-form-item label="版本号">
          <a-input v-model:value="form.versionNo" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status">
            <a-select-option value="ACTIVE">启用</a-select-option>
            <a-select-option value="INACTIVE">停用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import type { TemplateConfigItem } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<TemplateConfigItem[]>([]);
const editOpen = ref(false);
const editingCode = ref("");
const form = reactive({
  templateName: "",
  filePath: "",
  versionNo: "",
  status: "ACTIVE",
});

const columns = [
  { title: "模板编码", dataIndex: "templateCode", key: "templateCode" },
  { title: "模板名称", dataIndex: "templateName", key: "templateName" },
  { title: "类型", dataIndex: "templateType", key: "templateType" },
  { title: "版本", dataIndex: "versionNo", key: "versionNo" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action" },
];

function openEdit(record: TemplateConfigItem) {
  editingCode.value = record.templateCode;
  form.templateName = record.templateName;
  form.filePath = record.filePath ?? "";
  form.versionNo = record.versionNo;
  form.status = record.status;
  editOpen.value = true;
}

async function save() {
  await api.settings.saveTemplate(editingCode.value, { ...form });
  message.success("模板已保存");
  editOpen.value = false;
  await load();
}

async function load() {
  loading.value = true;
  try {
    rows.value = await api.settings.templates();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
