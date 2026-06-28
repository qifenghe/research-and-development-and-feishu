<template>
  <span class="status-badge" :class="`status-badge--${variant}`">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string;
    variant?: "primary" | "success" | "warning" | "danger";
  }>(),
  { variant: "primary" },
);
</script>
