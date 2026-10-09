<template>
  <a-config-provider
    :locale="zhCN"
    :theme="{
      token: {
        colorPrimary: '#285D59',
        colorInfo: '#285D59',
        colorInfoBg: '#EDF4F0',
        colorInfoBorder: '#D5E4DA',
        colorBgLayout: '#F6F4EF',
        colorText: '#243330',
        colorBorder: '#DDDCD4',
        borderRadius: 8,
        fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
      },
    }"
  >
    <RouterView />
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN';
</script>
