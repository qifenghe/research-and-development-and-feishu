<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#246BFE',
        borderRadius: 8,
        fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
      },
    }"
  >
    <RouterView />
  </a-config-provider>
</template>

<script setup lang="ts">
</script>
