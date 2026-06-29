<template>
  <div>
    <a-page-header title="样品需求" sub-title="研发内勤录需求，研发总监审核，审核通过后进入任务池" />
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
  { title: "录入样品需求", description: "研发内勤从飞书进入，录入需要做的样品清单", path: "/demand/new" },
  { title: "需求列表", description: "查看全部样品需求单与状态", path: "/demand/list" },
  { title: "需求审核", description: "研发总监判断资料是否完整，通过后进入任务池", path: "/demand/review" },
];

const visibleCards = computed(() =>
  cards.filter((card) => canAccessRoute(auth.role, card.path, "pc")),
);
</script>
