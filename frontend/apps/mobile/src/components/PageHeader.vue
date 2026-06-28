<template>
  <div class="page-header" :style="compact ? { marginTop: 0 } : undefined">
    <h1 class="page-title">{{ title }}</h1>
    <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    <div v-if="$slots.stats" class="stat-row">
      <slot name="stats" />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
  compact?: boolean;
}>();
</script>
