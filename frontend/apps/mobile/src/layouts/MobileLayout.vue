<template>
  <div class="mobile-shell" :class="{ 'mobile-shell--subpage': !showTabbar }">
    <BrandHeader />
    <van-nav-bar
      v-if="!showTabbar"
      :title="title"
      :left-arrow="showBack"
      @click-left="goBack"
    >
      <template #left><button type="button" class="mobile-back" aria-label="返回上一页" @click.stop="goBack">‹ 返回</button></template>
    </van-nav-bar>
    <main class="mobile-page">
      <RouterView v-slot="{ Component }">
        <component :is="Component" />
      </RouterView>
    </main>
    <van-tabbar v-if="showTabbar" route active-color="var(--mobile-primary)" inactive-color="var(--mobile-muted)">
      <van-tabbar-item replace to="/todo" icon="todo-list-o">工作台</van-tabbar-item>
      <van-tabbar-item v-if="showSamplesTab" replace to="/samples" icon="search">样品</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import BrandHeader from '../components/BrandHeader.vue';
import { useRoute, useRouter } from "vue-router";
import { canAccessRoute } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const showTabbar = computed(() => Boolean(route.meta.tab));
const showBack = computed(() => !route.meta.tab);
const showSamplesTab = computed(() => canAccessRoute(auth.role, "/samples", "mobile"));

const titleMap: Record<string, string> = {
  todo: "工作台",
  "request-new": "录入需求",
  "request-review": "需求审核",
  "task-assign": "待分配任务",
  samples: "样品",
  profile: "我的",
  "task-detail": "任务详情",
  "experiment-form": "实验单录入",
  "test-confirm": "测试确认",
  "shipment-feedback": "寄样反馈",
  "sample-history": "实验单历史",
  'shipment-record':'寄样登记',
  'task-customer-feedback':'客户反馈',
  'pricing-list':'核价文件',
  'pricing-create':'编制核价文件',
  'pricing-detail':'核价详情',
};

const title = computed(() => titleMap[String(route.name ?? "")] ?? "龙汇研发管理系统");

function goBack() {
  router.back();
}
</script>

<style scoped>
.mobile-shell {
  min-height: 100vh;
  padding-bottom: 50px;
}

.mobile-shell--subpage .mobile-page {
  padding-bottom: 120px;
}
</style>
