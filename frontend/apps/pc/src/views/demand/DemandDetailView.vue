<template>
  <div>
    <a-page-header :title="detail?.productName ?? '需求详情'" @back="router.back()">
      <template #extra>
        <a-button v-if="canReview" type="primary" :disabled="mutating" @click="openApprove">审核通过</a-button>
        <a-button v-if="canReview" danger :disabled="mutating" @click="openReject">驳回重填</a-button>
        <a-button v-if="detail?.status==='REJECTED' && detail.canResubmit" type="primary" @click="router.push(`/demand/${detail.id}/edit`)">修改并重新提交</a-button>
        <a-tag v-if="detail" :color="statusColor">{{ SAMPLE_STATUS_LABELS[detail.status] ?? detail.status }}</a-tag>
      </template>
    </a-page-header>
    <a-card v-if="detail" :loading="loading">
      <a-descriptions bordered :column="2">
        <a-descriptions-item label="样品编号">{{ detail.sampleNo }}</a-descriptions-item>
        <a-descriptions-item label="产品类型">{{ detail.productType }}</a-descriptions-item>
        <a-descriptions-item label="销售申请人">{{ detail.creatorName }}</a-descriptions-item>
        <a-descriptions-item label="录入人">{{ detail.recorderName || '历史记录未记录' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detail.createdAt }}</a-descriptions-item>
      </a-descriptions>
      <a-divider />
      <RequestRequirementSummary :request="detail" />
    </a-card>
    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-card v-if="detail" title="提交与审核记录" style="margin-top:16px">
      <a-empty v-if="!detail.reviewHistory?.length" description="历史需求未记录审核明细" :image="false" />
      <a-timeline v-else><a-timeline-item v-for="item in detail.reviewHistory" :key="item.versionNo" :color="item.action==='REJECTED'?'red':'blue'">
        <b>{{ actionLabels[item.action]??item.action }}</b><span style="margin-left:12px">{{ item.actorName }} · {{ item.actedAt.replace('T',' ') }}</span><p v-if="item.comment" style="white-space:pre-wrap;margin:8px 0 0">{{ item.comment }}</p>
      </a-timeline-item></a-timeline>
    </a-card>
    <a-modal v-model:open="approveOpen" title="确认审核通过" ok-text="审核通过" cancel-text="取消" :confirm-loading="mutating" :closable="!mutating" :mask-closable="!mutating" :cancel-button-props="{disabled:mutating}" @ok="approve">
      <p>确认通过「{{ detail?.productName }}」的需求审核？通过后将进入待分配任务池。</p>
      <a-alert v-if="reviewError" type="error" :message="reviewError" show-icon />
    </a-modal>
    <a-modal v-model:open="rejectOpen" title="驳回重填" ok-text="确认驳回" cancel-text="取消" :confirm-loading="mutating" :closable="!mutating" :mask-closable="!mutating" :cancel-button-props="{disabled:mutating}" @ok="reject">
      <p>{{ detail?.productName }} · {{ detail?.sampleNo }}</p>
      <a-form layout="vertical"><a-form-item label="驳回批语" required :help="rejectError" :validate-status="rejectError?'error':undefined">
        <a-textarea v-model:value="comment" aria-label="驳回批语" :maxlength="2000" :rows="4" placeholder="请说明缺少的资料或需要修改的内容" />
      </a-form-item></a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { canPerformAction, SAMPLE_STATUS_LABELS, type SampleRequest } from "@rnd/shared";
import { message } from "ant-design-vue";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";
import RequestRequirementSummary from '../../components/requests/RequestRequirementSummary.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<SampleRequest | null>(null);
const error=ref('');
const auth=useAuthStore();
const mutating=ref(false),approveOpen=ref(false),rejectOpen=ref(false);
const comment=ref(''),rejectError=ref(''),reviewError=ref('');
const canReview=computed(()=>!loading.value && detail.value?.status==='PENDING_REVIEW'
  && ['RND_DIRECTOR','ADMIN','SYSTEM_ADMIN'].includes(auth.role)
  && canPerformAction(auth.role,'APPROVE_REQUEST'));
function openApprove(){if(!canReview.value||mutating.value)return;reviewError.value='';approveOpen.value=true;}
function openReject(){if(!canReview.value||mutating.value)return;comment.value='';rejectError.value='';rejectOpen.value=true;}
async function approve(){
  if(!canReview.value||mutating.value||!detail.value)return;
  mutating.value=true;reviewError.value='';
  try{await api.sample.approve(detail.value.id,undefined,detail.value.reviewVersion??0);approveOpen.value=false;message.success('审核通过，已进入任务池');await load();}
  catch(e){reviewError.value=e instanceof Error?e.message:'审核失败，请重试';}
  finally{mutating.value=false;}
}
async function reject(){
  if(!canReview.value||mutating.value||!detail.value)return;
  if(!comment.value.trim()){rejectError.value='请填写驳回批语';return;}
  mutating.value=true;rejectError.value='';
  try{await api.sample.reject(detail.value.id,detail.value.reviewVersion??0,comment.value.trim());rejectOpen.value=false;message.success('已驳回，等待内勤修改重新提交');await load();}
  catch(e){rejectError.value=e instanceof Error?e.message:'驳回失败，请重试';}
  finally{mutating.value=false;}
}
const actionLabels:Record<string,string>={SUBMITTED:'首次提交',REJECTED:'驳回重填',RESUBMITTED:'修改后重新提交',APPROVED:'审核通过'};

const statusColor = computed(() => {
  if (!detail.value) return "default";
  if (["APPROVED", "PENDING_ASSIGNMENT"].includes(detail.value.status)) return "green";
  if (detail.value.status === "PENDING_REVIEW") return "orange";
  return "red";
});

async function load() {
  loading.value = true;
  error.value='';
  try {
    detail.value = await api.sample.detail(String(route.params.id));
  } catch(e){error.value=e instanceof Error?e.message:'需求读取失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
