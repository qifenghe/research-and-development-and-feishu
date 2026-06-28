<template>
  <div>
    <a-page-header title="样品需求列表" sub-title="查看全部需求单状态与详情" />
    <a-card>
      <a-space style="margin-bottom: 16px">
        <a-input-search v-model:value="keyword" placeholder="搜索产品/客户" style="width: 260px" @search="load" />
        <a-select v-model:value="status" allow-clear placeholder="状态" style="width: 160px" @change="load">
          <a-select-option value="PENDING_REVIEW">待审核</a-select-option>
          <a-select-option value="APPROVED">已通过</a-select-option>
          <a-select-option value="REJECTED">已退回</a-select-option>
        </a-select>
      </a-space>
      <a-table
        :columns="columns"
        :data-source="rows"
        row-key="id"
        :loading="loading"
        @row-click="(record: SampleRequest) => router.push(`/demand/${record.id}`)"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { SampleRequest } from "@rnd/shared";
import { api } from "../../services/api";

const router = useRouter();
const loading = ref(false);
const keyword = ref("");
const status = ref<string>();
const rows = ref<SampleRequest[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品名称", dataIndex: "productName", key: "productName" },
  { title: "客户", dataIndex: "customerName", key: "customerName" },
  { title: "规格", dataIndex: "specification", key: "specification" },
  { title: "创建人", dataIndex: "creatorName", key: "creatorName" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "创建时间", dataIndex: "createdAt", key: "createdAt" },
];

async function load() {
  loading.value = true;
  try {
    rows.value = (await api.sample.list({
      keyword: keyword.value || undefined,
      status: status.value,
    })) as SampleRequest[];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
