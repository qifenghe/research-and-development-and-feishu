<template>
  <div>
    <a-page-header title="寄样核价" sub-title="寄样反馈、客户意见、核价文件生成和通知财务集中处理" />
    <a-row :gutter="16">
      <a-col :span="6"><a-card hoverable @click="router.push('/shipment/list')"><a-card-meta title="寄样反馈" description="多个样品同时等待反馈" /></a-card></a-col>
      <a-col :span="6"><a-card hoverable @click="router.push('/pricing/list')"><a-card-meta title="核价文件" description="按 Excel 模板生成版本化核价文件" /></a-card></a-col>
      <a-col :span="6"><a-card hoverable @click="router.push('/finance')"><a-card-meta title="通知财务" description="核价文件生成后推送财务" /></a-card></a-col>
      <a-col :span="6"><a-card hoverable @click="router.push('/archive')"><a-card-meta title="文件归档" description="按产品、版本、阶段保存文件" /></a-card></a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
const router = useRouter();
</script>
