<template>
  <div>
    <PageHeader title="我的" />

    <div class="profile-card">
      <div class="profile-avatar">{{ avatarText }}</div>
      <div>
        <h2 class="page-title" style="font-size: 20px; margin-bottom: 4px">{{ auth.displayName }}</h2>
        <p class="page-subtitle">{{ roleLabel }} · 飞书已绑定</p>
      </div>
    </div>

    <div class="menu-card">
      <van-cell title="我的草稿实验单" is-link :value="`${draftCount} 项`" @click="router.push('/samples?status=SAMPLING')" />
      <van-cell title="我的已完成记录" is-link @click="router.push('/samples?status=COMPLETED')" />
      <van-cell title="帮助与反馈" is-link />
    </div>

    <div style="padding: 8px 0 24px">
      <van-button block plain @click="signOut">退出登录</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const router = useRouter();
const draftCount = ref(0);

const avatarText = computed(() => auth.displayName.slice(0, 1) || "我");
const roleLabel = computed(() => {
  const map: Record<string, string> = {
    RND_ENGINEER: "研发人员",
    RND_ASSISTANT: "研发内勤",
    TESTER: "测试人员",
    QA_TESTER: "测试人员",
    RND_DIRECTOR: "研发总监",
  };
  return map[auth.role] ?? auth.role ?? "未知";
});

onMounted(async () => {
  try {
    const tasks = (await api.task.list({ status: "SAMPLING" })) as import("@rnd/shared").RndTask[];
    draftCount.value = tasks.length;
  } catch {
    draftCount.value = 0;
  }
});

function signOut() {
  auth.signOut();
  router.push({ name: "login" });
}
</script>
