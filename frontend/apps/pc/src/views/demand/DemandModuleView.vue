<template>
  <div>
    <a-page-header title="样品需求" sub-title="研发内勤录需求，研发总监审核，审核通过后进入任务池" />
    <a-row :gutter="16">
      <a-col :span="8"><a-card hoverable @click="router.push('/demand/new')"><a-card-meta title="录入样品需求" description="研发内勤从飞书进入，录入需要做的样品清单" /></a-card></a-col>
      <a-col :span="8"><a-card hoverable @click="router.push('/demand/list')"><a-card-meta title="需求列表" description="查看全部样品需求单与状态" /></a-card></a-col>
      <a-col :span="8"><a-card hoverable @click="router.push('/demand/review')"><a-card-meta title="需求审核" description="研发总监判断资料是否完整，通过后进入任务池" /></a-card></a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
const router = useRouter();
</script>
