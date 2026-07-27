<template>
  <div>
    <a-page-header title="寄样核价" sub-title="寄样反馈、客户意见、核价文件生成及审核后自动移交财务" />
    <a-row :gutter="[16, 16]">
      <a-col v-for="card in visibleCards" :key="card.path" :span="6">
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
  { title: "寄样反馈", description: "查看待反馈样品，登记客户是否通过或继续打样", path: "/shipment/list" },
  { title: "录入反馈", description: "登记寄样并录入客户试吃结论", path: "/shipment/record" },
  { title: "核价文件", description: "按 Excel 模板生成版本化核价文件", path: "/pricing/list" },
  { title: "文件归档", description: "按产品、版本、阶段保存文件", path: "/archive" },
];

const visibleCards = computed(() =>
  cards.filter((card) => canAccessRoute(auth.role, card.path, "pc")),
);
</script>
