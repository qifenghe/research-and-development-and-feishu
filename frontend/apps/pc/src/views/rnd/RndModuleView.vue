<template>
  <div>
    <a-page-header title="研发任务" sub-title="任务池、分发、研发接任务、实验单、内部测试和锁版都在这里处理" />
    <a-row :gutter="16">
      <a-col :span="8"><a-card hoverable @click="router.push('/rnd/pool')"><a-card-meta title="任务池" description="所有审核通过但尚未分配的任务" /></a-card></a-col>
      <a-col :span="8"><a-card hoverable @click="router.push('/rnd/assign')"><a-card-meta title="任务分发" description="研发总监分发给具体研发人员" /></a-card></a-col>
      <a-col :span="8"><a-card hoverable @click="router.push('/rnd/stopped')"><a-card-meta title="停止项目池" description="复打样无效或项目取消时保留历史资料" /></a-card></a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
const router = useRouter();
</script>
