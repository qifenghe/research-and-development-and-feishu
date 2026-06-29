<template>
  <div class="forbidden-page">
    <van-empty image="error" description="当前角色无权访问此页面">
      <van-button round type="primary" @click="router.replace('/todo')">返回待办</van-button>
    </van-empty>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<style scoped>
.forbidden-page {
  padding: 48px 16px;
}
</style>
