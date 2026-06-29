<template>
  <div>
    <a-page-header title="研发任务" sub-title="任务池、分发、研发接任务、实验单、内部测试和锁版都在这里处理" />
    <a-row :gutter="[16, 16]">
      <a-col v-for="card in visibleCards" :key="card.path" :span="8">
        <a-card class="module-link-card" hoverable @click="router.push(card.path)">
          <a-card-meta :title="card.title" :description="card.description" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { canAccessRoute } from "@rnd/shared";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const auth = useAuthStore();

const cards = [
  { title: "我的打样任务", description: "接受任务、填写实验单并提交内部测试", path: "/rnd/my-tasks" },
  { title: "内部测试待办", description: "测试人员对待测任务进行评价", path: "/rnd/pending-tests" },
  { title: "任务池", description: "所有审核通过但尚未分配的任务", path: "/rnd/pool" },
  { title: "任务分发", description: "研发总监分发给具体研发人员", path: "/rnd/assign" },
  { title: "停止项目池", description: "复打样无效或项目取消时保留历史资料", path: "/rnd/stopped" },
];

const visibleCards = computed(() =>
  cards.filter((card) => canAccessRoute(auth.role, card.path, "pc")),
);
</script>
