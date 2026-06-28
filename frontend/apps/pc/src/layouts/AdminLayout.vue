<template>
  <a-layout class="admin-layout">
    <a-layout-header class="topbar">
      <div class="brand">
        <div class="brand-mark">研</div>
        <div>
          <div class="brand-title">飞书自建应用研发样品管理系统</div>
          <div class="brand-sub">PC 管理后台</div>
        </div>
      </div>
      <div class="topbar-actions">
        <a-tag color="blue">{{ auth.displayName }}</a-tag>
        <a-button size="small" @click="signOut">退出</a-button>
      </div>
    </a-layout-header>
    <a-layout>
      <a-layout-sider width="220" theme="light" class="sidebar">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          mode="inline"
          @click="onMenuClick"
        >
          <a-menu-item key="/dashboard">
            <span>工作台</span>
          </a-menu-item>
          <a-sub-menu key="demand" title="样品需求">
            <a-menu-item key="/demand">模块首页</a-menu-item>
            <a-menu-item key="/demand/new">录入需求</a-menu-item>
            <a-menu-item key="/demand/review">需求审核</a-menu-item>
          </a-sub-menu>
          <a-sub-menu key="rnd" title="研发任务">
            <a-menu-item key="/rnd">模块首页</a-menu-item>
            <a-menu-item key="/rnd/pool">任务池</a-menu-item>
            <a-menu-item key="/rnd/assign">任务分发</a-menu-item>
            <a-menu-item key="/rnd/stopped">停止项目池</a-menu-item>
          </a-sub-menu>
          <a-sub-menu key="shipment" title="寄样核价">
            <a-menu-item key="/shipment">模块首页</a-menu-item>
            <a-menu-item key="/shipment/list">寄样反馈</a-menu-item>
            <a-menu-item key="/pricing/list">核价文件</a-menu-item>
            <a-menu-item key="/finance">通知财务</a-menu-item>
            <a-menu-item key="/archive">文件归档</a-menu-item>
          </a-sub-menu>
          <a-sub-menu key="settings" title="系统设置">
            <a-menu-item key="/settings">配置中心</a-menu-item>
            <a-menu-item key="/settings/users">人员权限</a-menu-item>
            <a-menu-item key="/settings/form-fields">表单字段</a-menu-item>
            <a-menu-item key="/settings/workflows">流程状态</a-menu-item>
            <a-menu-item key="/settings/dictionaries">基础字典</a-menu-item>
            <a-menu-item key="/settings/role-permissions">角色权限</a-menu-item>
            <a-menu-item key="/settings/data-models">数据模型</a-menu-item>
          </a-sub-menu>
          <a-menu-item key="/flowchart">
            <span>流程图</span>
          </a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout-content class="content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const selectedKeys = ref<string[]>([route.path]);
const openKeys = ref<string[]>([]);

const moduleOpenKey = computed(() => {
  if (route.path.startsWith("/demand")) return "demand";
  if (route.path.startsWith("/rnd")) return "rnd";
  if (route.path.startsWith("/shipment") || route.path.startsWith("/pricing") || route.path === "/finance" || route.path === "/archive") return "shipment";
  if (route.path.startsWith("/settings")) return "settings";
  return "";
});

watch(
  () => route.path,
  (path) => {
    selectedKeys.value = [path];
    if (moduleOpenKey.value) openKeys.value = [moduleOpenKey.value];
  },
  { immediate: true },
);

function onMenuClick({ key }: { key: string }) {
  router.push(key);
}

function signOut() {
  auth.signOut();
  router.push({ name: "login" });
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.topbar {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  padding: 0 24px;
}

.brand {
  align-items: center;
  display: flex;
  gap: 12px;
}

.brand-mark {
  align-items: center;
  background: #246bfe;
  border-radius: 12px;
  color: #fff;
  display: flex;
  font-weight: 900;
  height: 40px;
  justify-content: center;
  width: 40px;
}

.brand-title {
  font-size: 16px;
  font-weight: 800;
}

.brand-sub {
  color: #64748b;
  font-size: 12px;
}

.topbar-actions {
  align-items: center;
  display: flex;
  gap: 12px;
}

.sidebar {
  border-right: 1px solid #e5e7eb;
}

.content {
  min-height: calc(100vh - 64px);
  padding: 24px;
}
</style>
