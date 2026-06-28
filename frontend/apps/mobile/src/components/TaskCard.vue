<template>
  <div class="task-card">
    <div class="task-card__row">
      <div>
        <h3 class="task-card__title">{{ title }}</h3>
        <p class="task-card__meta">{{ meta }}</p>
      </div>
      <van-button v-if="actionLabel" size="small" type="primary" round @click.stop="$emit('action')">
        {{ actionLabel }}
      </van-button>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  meta: string;
  actionLabel?: string;
}>();

defineEmits<{ action: [] }>();
</script>
