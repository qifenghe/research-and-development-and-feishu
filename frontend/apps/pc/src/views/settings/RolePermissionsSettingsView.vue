<template>
  <div>
    <a-page-header title="角色权限" sub-title="配置菜单、按钮和数据范围权限" />
    <a-card>
      <a-space style="margin-bottom: 16px">
        <a-input v-model:value="roleCode" placeholder="角色编码，如 RND_DIRECTOR" style="width: 260px" />
        <a-button type="primary" @click="load">查询</a-button>
      </a-space>
      <a-descriptions v-if="config" bordered :column="1" title="权限规则">
        <a-descriptions-item v-for="rule in config.rules" :key="`${rule.httpMethod}-${rule.pathPattern}`" :label="`${rule.httpMethod} ${rule.pathPattern}`">
          {{ rule.allowed ? "允许" : "拒绝" }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { message } from "ant-design-vue";
import type { RolePermissionConfig } from "@rnd/shared";
import { api } from "../../services/api";

const roleCode = ref("RND_DIRECTOR");
const config = ref<RolePermissionConfig | null>(null);

async function load() {
  if (!roleCode.value.trim()) return;
  try {
    config.value = await api.settings.rolePermissions(roleCode.value.trim());
  } catch (error) {
    message.error(error instanceof Error ? error.message : "查询失败");
  }
}
</script>
