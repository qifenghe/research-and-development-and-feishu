<template>
  <div class="conclusion-row">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="conclusion-btn"
      :class="[
        `conclusion-btn--${option.variant}`,
        { 'conclusion-btn--active': modelValue === option.value },
      ]"
      @click="$emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
export interface ConclusionOption {
  value: string;
  label: string;
  variant: "pass" | "resample" | "stop";
}

defineProps<{
  modelValue: string;
  options: ConclusionOption[];
}>();

defineEmits<{ "update:modelValue": [value: string] }>();
</script>
