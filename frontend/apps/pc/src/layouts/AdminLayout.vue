<template>
  <a-layout class="admin-layout">
    <a-layout-header class="topbar">
      <div class="brand">
        <img class="brand-logo" :src="longhuiLogo" alt="龙汇食品" />
        <div>
          <div class="brand-title">龙汇研发管理系统</div>
          <div class="brand-sub">从需求到配方 · 让研发有据可循</div>
        </div>
      </div>
      <div class="topbar-actions">
        <a-tag color="cyan">{{ roleLabel(auth.role) }}</a-tag>
        <a-tag>{{ auth.displayName }}</a-tag>
        <a-button size="small" @click="signOut">退出</a-button>
      </div>
    </a-layout-header>
    <a-layout>
      <a-layout-sider width="240" theme="light" class="sidebar">
        <div v-if="panel" class="context-panel">
          <p class="context-panel__eyebrow">当前样品项目</p>
          <h3 class="context-panel__title">{{ panel.title }}</h3>
          <p v-if="panel.subtitle" class="context-panel__subtitle">{{ panel.subtitle }}</p>
          <a-tag v-if="panel.statusLabel" :color="panelTone">{{ panel.statusLabel }}</a-tag>
          <div class="context-panel__rows">
            <div v-for="row in panel.rows" :key="row.label" class="context-row">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </div>
          </div>
        </div>
        <a-menu
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          mode="inline"
          @click="onMenuClick"
        >
          <template v-for="group in navigation" :key="group.key">
            <a-sub-menu v-if="group.children" :key="group.key" :title="group.label">
              <a-menu-item v-for="item in group.children" :key="item.key">{{ item.label }}</a-menu-item>
            </a-sub-menu>
            <a-menu-item v-else :key="group.key">{{ group.label }}</a-menu-item>
          </template>
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
import longhuiLogo from '../assets/longhui-logo.jpg';
import { useRoute, useRouter } from "vue-router";
import { canAccessRoute, roleLabel } from "@rnd/shared";
import { useAuthStore } from "../stores/auth";
import { useAdminContext } from "../composables/adminContext";
import {materialCatalogApi} from '../services/materialCatalogApi';
import {sopCatalogApi} from '../services/sopCatalogApi';
import {buildAdminNavigation,navigationSelection} from './adminNavigation';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const canReadMaterials = ref(false);
const canReadSops = ref(false);
let sopAccessRequest=0;
watch(()=>[auth.token,auth.role],async()=>{
 const request=++sopAccessRequest;canReadSops.value=false;
 try{await sopCatalogApi.list('',1,1);if(request===sopAccessRequest)canReadSops.value=true;}catch{/* Server-authorized SOP index only. */}
},{immediate:true});
let materialAccessRequest = 0;
watch(() => [auth.token,auth.role], async () => {
  const request=++materialAccessRequest; canReadMaterials.value=false;
  try {await materialCatalogApi.list({size:1});if(request===materialAccessRequest)canReadMaterials.value=true;} catch {/* Menu follows server capability. */}
}, {immediate:true});
const { panel } = useAdminContext();

function canSee(path: string) {
  return canAccessRoute(auth.role, path, "pc");
}

const selectedKeys = ref<string[]>([route.path]);
const openKeys = ref<string[]>([]);
const navigation=computed(()=>buildAdminNavigation(canSee,{materials:canReadMaterials.value,sops:canReadSops.value}));

const panelTone = computed(() => {
  switch (panel.value?.statusTone) {
    case "success":
      return "green";
    case "warning":
      return "orange";
    case "error":
      return "red";
    case "processing":
      return "blue";
    default:
      return "default";
  }
});

watch(
  () => route.path,
  (path) => {
    const selection=navigationSelection(path);
    selectedKeys.value = [selection.selectedKey];
    openKeys.value = selection.openKey?[selection.openKey]:[];
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
  border-bottom: 1px solid var(--pc-border);
  display: flex;
  height: 88px;
  line-height: normal;
  justify-content: space-between;
  padding: 0 24px;
}

.brand {
  align-items: center;
  display: flex;
  gap: 18px;
}

.brand-logo {
  width: 62px;
  height: 68px;
  object-fit: contain;
  flex-shrink: 0;
}

.brand-title {
  font-family: var(--pc-title-font);
  font-size: 25px;
  font-weight: 400;
  line-height: 36px;
  letter-spacing: 2px;
}

.brand-sub {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.topbar-actions {
  align-items: center;
  display: flex;
  gap: 12px;
}

.sidebar {
  border-right: 1px solid var(--pc-border);
}

.context-panel {
  background: linear-gradient(180deg, #edf3ef, #fff);
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 8px;
  padding: 16px 16px 12px;
}

.context-panel__eyebrow {
  color: #64748b;
  font-size: 12px;
  margin: 0 0 4px;
}

.context-panel__title {
  font-size: 16px;
  font-weight: 800;
  margin: 0;
}

.context-panel__subtitle {
  color: #64748b;
  font-size: 12px;
  margin: 4px 0 8px;
}

.context-panel__rows {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.context-row {
  display: flex;
  font-size: 12px;
  justify-content: space-between;
  gap: 8px;
}

.context-row span {
  color: #64748b;
}

.context-row strong {
  color: #0f172a;
  font-weight: 600;
  text-align: right;
}

.content {
  min-height: calc(100vh - 88px);
  padding: 30px;
}
@media(max-width:900px){.brand-title{font-size:21px;letter-spacing:1px}.topbar{padding:0 18px}.topbar-actions{gap:6px}.content{padding:20px}}
@media(max-width:640px){.topbar{height:auto;min-height:88px;flex-wrap:wrap;padding:12px;gap:10px}.brand-logo{width:46px;height:50px}.brand{gap:12px}.brand-title{font-size:20px}.brand-sub{font-size:10px}.topbar-actions{margin-left:auto}}
</style>
