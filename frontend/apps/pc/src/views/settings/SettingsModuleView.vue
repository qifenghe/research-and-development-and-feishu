<template>
  <div>
    <a-page-header title="配置中心" sub-title="把流程里可变化的规则解耦出来，统一维护、统一被流程调用" />
    <a-row :gutter="16">
      <a-col v-for="item in links" :key="item.path" :span="8">
        <a-card hoverable @click="router.push(item.path)">
          <a-card-meta :title="item.title" :description="item.description" />
        </a-card>
      </a-col>
    </a-row>
    <a-alert style="margin-top: 16px" type="info" show-icon message="飞书通知配置后端 API 待 HTTPS 公网联调；模板与字典配置已支持编辑保存。" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
const links = [
  { path: "/settings/users", title: "人员配置与权限", description: "维护各角色账号与状态" },
  { path: "/settings/form-fields", title: "表单字段配置", description: "维护需求、实验、测试等字段" },
  { path: "/settings/workflows", title: "流程状态配置", description: "维护状态、按钮和流转规则" },
  { path: "/settings/dictionaries", title: "基础字典配置", description: "维护产品类型、单位、状态标签" },
  { path: "/settings/templates", title: "核价模板配置", description: "维护 Excel 核价导出模板版本" },
  { path: "/settings/role-permissions", title: "角色权限", description: "维护接口与按钮权限" },
  { path: "/settings/data-models", title: "数据模型确认清单", description: "按业务关系确认数据模型" },
];
</script>
