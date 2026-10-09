<template>
  <div>
    <a-page-header title="研发总监：需求审核" sub-title="研发总监判断资料是否完整，通过后进入任务池" />
    <a-card>
      <a-table
        :columns="columns"
        :data-source="rows"
        row-key="id"
        :loading="loading"
        :custom-row="customRow"
      >
        <template #expandedRowRender="{record}"><RequestRequirementSummary :request="record" /></template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag>{{ SAMPLE_STATUS_LABELS[record.status as keyof typeof SAMPLE_STATUS_LABELS] ?? record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canApprove"><a-button type="primary" size="small" :disabled="mutating" @click.stop="approve(record)">审核通过</a-button>
            <a-button danger size="small" :disabled="mutating" @click.stop="openReject(record)">驳回重填</a-button></a-space>
          </template>
        </template>
      </a-table>
    </a-card>
    <a-modal v-model:open="rejectOpen" title="驳回重填" ok-text="确认驳回" cancel-text="取消" :confirm-loading="mutating" :closable="!mutating" :mask-closable="!mutating" :cancel-button-props="{disabled:mutating}" @ok="reject">
      <p>{{ rejecting?.productName }} · {{ rejecting?.sampleNo }}</p>
      <a-form layout="vertical"><a-form-item label="驳回批语" :help="rejectError" :validate-status="rejectError?'error':undefined" required><a-textarea id="reject-comment" v-model:value="comment" aria-label="驳回批语" :maxlength="2000" :rows="4" placeholder="请说明缺少的资料或需要修改的内容" /></a-form-item></a-form>
      <p style="color:#86909c">驳回后由内勤修改原需求，重新提交后再次审核。</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { canPerformAction, SAMPLE_STATUS_LABELS, type SampleRequest } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";
import RequestRequirementSummary from '../../components/requests/RequestRequirementSummary.vue';
import { createDemandRowProps } from "./demand-row-navigation";

const auth = useAuthStore();
const router = useRouter();
const loading = ref(false);
const rows = ref<SampleRequest[]>([]);
const mutating=ref(false),rejectOpen=ref(false),rejecting=ref<SampleRequest>(),comment=ref(''),rejectError=ref('');
const canApprove = computed(() => ["RND_DIRECTOR", "ADMIN", "SYSTEM_ADMIN"].includes(auth.role) && canPerformAction(auth.role, "APPROVE_REQUEST"));

const columns = [
  { title: "样品编号", dataIndex: "sampleNo", key: "sampleNo" },
  { title: "产品", dataIndex: "productName", key: "productName" },
  { title: "客户", dataIndex: "customerName", key: "customerName" },
  { title: "应用场景", dataIndex: "applicationScenario", key: "applicationScenario", ellipsis: true },
  { title: "味型", dataIndex: "flavorRequirement", key: "flavorRequirement", ellipsis: true },
  { title: "销售申请人", dataIndex: "creatorName", key: "creatorName" },
  { title: "状态", key: "status" },
  { title: "操作", key: "action" },
];

function customRow(record: SampleRequest) {
  return createDemandRowProps(record.id, (id) => {
    void router.push({ name: "demand-detail", params: { id } });
  });
}

async function load() {
  loading.value = true;
  try {
    rows.value = await api.sample.list({ status: "PENDING_REVIEW" }) as SampleRequest[];
  } finally {
    loading.value = false;
  }
}

async function approve(record: SampleRequest) {
  if(mutating.value)return;
  mutating.value=true;
  try {
    await api.sample.approve(record.id, undefined, record.reviewVersion??0);
    message.success("审核通过，已进入任务池");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "审核失败");
  } finally {mutating.value=false;}
}

function openReject(record:SampleRequest){rejecting.value=record;comment.value='';rejectError.value='';rejectOpen.value=true;}
async function reject(){
 if(mutating.value || !rejecting.value)return;
 if(!comment.value.trim()){rejectError.value='请填写驳回批语';return;}
 mutating.value=true;rejectError.value='';
 try{await api.sample.reject(rejecting.value.id,rejecting.value.reviewVersion??0,comment.value.trim());rejectOpen.value=false;message.success('已驳回，等待内勤修改重新提交');await load();}
 catch(e){rejectError.value=e instanceof Error?e.message:'驳回失败，请重试';}
 finally{mutating.value=false;}
}

onMounted(load);
</script>
