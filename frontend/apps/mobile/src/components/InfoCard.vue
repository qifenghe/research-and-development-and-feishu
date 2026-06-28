<template>
  <div class="info-card">
    <p v-if="title" class="info-card__title">{{ title }}</p>
    <div v-for="row in rows" :key="row.label" class="info-row">
      <span class="info-row__label">{{ row.label }}</span>
      <span class="info-row__value">{{ row.value }}</span>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  rows?: Array<{ label: string; value: string }>;
}>();
</script>
