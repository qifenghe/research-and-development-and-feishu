<template>
  <div class="mobile-shell">
    <van-nav-bar
      v-if="!showTabbar"
      :title="title"
      :left-arrow="showBack"
      @click-left="goBack"
    />
    <main class="mobile-page">
      <RouterView />
    </main>
    <van-tabbar v-if="showTabbar" route active-color="#246BFE" inactive-color="#64748B">
      <van-tabbar-item replace to="/todo" icon="todo-list-o">待办</van-tabbar-item>
      <van-tabbar-item replace to="/samples" icon="search">样品</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const showTabbar = computed(() => Boolean(route.meta.tab));
const showBack = computed(() => !route.meta.tab);

const titleMap: Record<string, string> = {
  todo: "我的待办",
  "request-new": "录入需求",
  "request-review": "审核需求",
  "task-assign": "任务分发",
  samples: "样品",
  profile: "我的",
  "task-detail": "任务详情",
  "experiment-form": "实验单录入",
  "test-confirm": "测试确认",
  "shipment-feedback": "寄样反馈",
  "sample-history": "实验单历史",
};

const title = computed(() => titleMap[String(route.name ?? "")] ?? "研发样品");

function goBack() {
  router.back();
}
</script>

<style scoped>
.mobile-shell {
  min-height: 100vh;
  padding-bottom: 50px;
}
</style>
