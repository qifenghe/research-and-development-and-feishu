<template>
  <div>
    <a-page-header :title="detail?.productName ?? '需求详情'" @back="router.back()">
      <template #extra>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { SAMPLE_STATUS_LABELS, type SampleRequest } from "@rnd/shared";
import { api } from "../../services/api";
import RequestRequirementSummary from '../../components/requests/RequestRequirementSummary.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<SampleRequest | null>(null);
const error=ref('');
const actionLabels:Record<string,string>={SUBMITTED:'首次提交',REJECTED:'驳回重填',RESUBMITTED:'修改后重新提交',APPROVED:'审核通过'};

const statusColor = computed(() => {
  if (!detail.value) return "default";
  if (["APPROVED", "PENDING_ASSIGNMENT"].includes(detail.value.status)) return "green";
  if (detail.value.status === "PENDING_REVIEW") return "orange";
  return "red";
});

onMounted(async () => {
  loading.value = true;
  try {
    detail.value = await api.sample.detail(String(route.params.id));
  } catch(e){error.value=e instanceof Error?e.message:'需求读取失败';
  } finally {
    loading.value = false;
  }
});
</script>
