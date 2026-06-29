<template>
  <div class="fixed-footer" :class="{ 'fixed-footer--with-tabbar': withTabbar }">
    <div class="form-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ withTabbar?: boolean }>(), { withTabbar: false });
</script>
