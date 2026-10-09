<template>
  <div class="demand-paper-page">
    <a-page-header title="样品需求列表" sub-title="查看全部需求单状态与详情"><template #extra><a-button v-if="canCreate" type="primary" @click="router.push('/demand/new')">新增需求</a-button></template></a-page-header>
    <a-card class="demand-paper-sheet">
      <div class="paper-toolbar">
      <a-space wrap class="paper-filters">
        <a-input-search v-model:value="keyword" placeholder="搜索产品/客户" style="width: 260px" @search="load" />
        <a-select v-model:value="status" allow-clear placeholder="状态" style="width: 160px" @change="load">
          <a-select-option value="PENDING_REVIEW">待审核</a-select-option>
          <a-select-option value="PENDING_ASSIGNMENT">已通过</a-select-option>
          <a-select-option value="REJECTED">驳回待修改</a-select-option>
        </a-select>
      </a-space>
      <span class="paper-caption"><i aria-hidden="true">需</i>需求档案</span>
      </div>
      <a-table
        :columns="columns"
        :data-source="rows"
        row-key="id"
        :loading="loading"
        :custom-row="(record:SampleRequest)=>createDemandRowProps(record.id,id=>router.push(`/demand/${id}`))"
      ><template #bodyCell="{column,record}">
        <a-tag v-if="column.key==='status'">{{ SAMPLE_STATUS_LABELS[record.status as keyof typeof SAMPLE_STATUS_LABELS]??record.status }}</a-tag>
        <a-space v-else-if="column.key==='action'"><a-button type="link" @click.stop="router.push(`/demand/${record.id}`)">查看</a-button><a-button v-if="record.status==='REJECTED' && record.canResubmit" type="link" @click.stop="router.push(`/demand/${record.id}/edit`)">修改重填</a-button></a-space>
      </template></a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {canAccessRoute,SAMPLE_STATUS_LABELS,type SampleRequest} from "@rnd/shared";
import {useAuthStore} from '../../stores/auth';
import {createDemandRowProps} from './demand-row-navigation';
import { api } from "../../services/api";

const router = useRouter();
const auth=useAuthStore();
const canCreate=computed(()=>canAccessRoute(auth.role,'/demand/new','pc'));
const loading = ref(false);
const keyword = ref("");
const status = ref<string>();
const rows = ref<SampleRequest[]>([]);

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品名称", dataIndex: "productName", key: "productName" },
  { title: "客户", dataIndex: "customerName", key: "customerName" },
  { title: "规格", dataIndex: "specification", key: "specification" },
  { title: "销售申请人", dataIndex: "creatorName", key: "creatorName" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "创建时间", dataIndex: "createdAt", key: "createdAt", customRender:({text}:{text?:string})=>text?.replace('T',' ').slice(0,16)||'—' },
  { title: "操作", key: "action" },
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

<style scoped>
/* Xuan-paper surface: decorative texture stays underneath all readable data. */
.demand-paper-page .demand-paper-sheet.ant-card{
 background-color:#f8f6ef;border:0;border-radius:3px;
 background-image:radial-gradient(ellipse at 5% 0%,#ffffff66,transparent 55%),linear-gradient(110deg,#bfae8605,transparent 45%,#bfae8606);
 box-shadow:0 1px 0 #c6c1b470,inset 0 2px 0 #77877955;
}
.demand-paper-page .paper-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:16px;padding:2px 0 22px;margin-bottom:18px;border-bottom:1px solid #ccc8ba80}
.demand-paper-page .paper-caption{display:flex;align-items:center;gap:10px;font-family:var(--pc-title-font);font-size:17px;letter-spacing:2px;color:#6c7669}
.paper-caption i{font-style:normal;font-family:var(--pc-title-font);font-size:15px;color:#9b4037;border:1px solid #9b403770;border-radius:2px;width:25px;height:25px;display:grid;place-items:center;letter-spacing:0;transform:rotate(-4deg)}
.demand-paper-page :deep(.ant-card-body){padding:26px 28px}
.demand-paper-page :deep(.ant-table){background:transparent;color:#36473d}
.demand-paper-page :deep(.ant-table-container){border-radius:0}
.demand-paper-page :deep(.ant-table-thead>tr>th){background:#eeebe0!important;color:#566450;border-bottom:1px solid #bdc3b3!important;letter-spacing:.5px}
.demand-paper-page :deep(.ant-table-tbody>tr>td){background:transparent!important;border-bottom:1px solid #ddd8c780;line-height:1.8}
.demand-paper-page :deep(.ant-table-tbody>tr:nth-child(even)>td){background:#eeeadd35!important}
.demand-paper-page :deep(.ant-table-tbody>tr:hover>td){background:#e9ede0!important}
.demand-paper-page :deep(.ant-table-tbody>tr>td:first-child){color:#7c8575;font-size:12px;letter-spacing:.15px}
.demand-paper-page :deep(.ant-tag){background:#eeeee3;border-color:#c9d1bf;color:#566a53}
.demand-paper-page :deep(.ant-input-affix-wrapper),.demand-paper-page :deep(.ant-select-selector){background:#fffdf6!important;border-color:#d2cfbf!important;border-radius:4px}
.demand-paper-page :deep(.ant-input){background:transparent}
.demand-paper-page :deep(.ant-input-search-button){background:#eeeee3;border-color:#d2cfbf;color:#536851;border-radius:0 4px 4px 0}
.demand-paper-page :deep(.ant-pagination-item){background:transparent;border-radius:3px}
.demand-paper-page :deep(.ant-pagination-item-active){background:#e9eee1;border-color:#8fa389}
.demand-paper-page :deep(.ant-pagination-options .ant-select-selector){background:#fffdf6!important}
@media(max-width:900px){.demand-paper-page :deep(.ant-card-body){padding:20px}.demand-paper-page .paper-caption{font-size:15px}}
@media(max-width:640px){.demand-paper-page :deep(.ant-card-body){padding:14px}.demand-paper-page .paper-caption{display:none}.demand-paper-page .paper-toolbar{padding-bottom:16px}}
</style>
