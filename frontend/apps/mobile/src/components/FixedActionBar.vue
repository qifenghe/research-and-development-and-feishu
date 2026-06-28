<template>
  <div class="fixed-footer" :class="{ 'fixed-footer--with-tabbar': withTabbar }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ withTabbar?: boolean }>(), { withTabbar: true });
</script>
