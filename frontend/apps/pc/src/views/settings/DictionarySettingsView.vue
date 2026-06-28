<template>
  <div>
    <a-page-header title="基础字典配置" sub-title="维护产品类型、单位、客户标签、状态标签和物料类别" />
    <a-card>
      <a-table :columns="columns" :data-source="rows" row-key="category" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" @click="openEdit(record)">编辑条目</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="editOpen" title="编辑字典条目" width="520" @close="editOpen = false">
      <a-form layout="vertical">
        <a-form-item v-for="(item, index) in editItems" :key="index" :label="`条目 ${index + 1}`">
          <a-space>
            <a-input v-model:value="item.code" placeholder="编码" />
            <a-input v-model:value="item.label" placeholder="名称" />
          </a-space>
        </a-form-item>
        <a-button block @click="editItems.push({ code: '', label: '' })">+ 添加条目</a-button>
      </a-form>
      <template #footer>
        <a-button type="primary" @click="save">保存</a-button>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { message } from "ant-design-vue";
import type { DictionaryConfig } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const rows = ref<Array<DictionaryConfig & { itemCount: number }>>([]);
const editOpen = ref(false);
const editingCategory = ref("");
const editItems = ref<Array<{ code: string; label: string }>>([]);

const columns = [
  { title: "分类编码", dataIndex: "category", key: "category" },
  { title: "分类名称", dataIndex: "categoryName", key: "categoryName" },
  { title: "条目数", dataIndex: "itemCount", key: "itemCount" },
  { title: "操作", key: "action" },
];

function openEdit(record: DictionaryConfig & { itemCount: number }) {
  editingCategory.value = record.category;
  editItems.value = (record.items ?? []).map((item) => ({ code: item.code, label: item.label }));
  if (!editItems.value.length) {
    editItems.value = [{ code: "", label: "" }];
  }
  editOpen.value = true;
}

async function save() {
  await api.settings.saveDictionary(editingCategory.value, {
    categoryName: rows.value.find((r) => r.category === editingCategory.value)?.categoryName,
    items: editItems.value.filter((item) => item.code && item.label),
  });
  message.success("字典已保存");
  editOpen.value = false;
  await load();
}

async function load() {
  loading.value = true;
  try {
    const data = await api.settings.dictionaries();
    rows.value = data.map((item: DictionaryConfig) => ({ ...item, itemCount: item.items?.length ?? 0 }));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
