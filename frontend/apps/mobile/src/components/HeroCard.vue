<template>
  <section class="hero-card">
    <p class="section-title" style="color: #bfdbfe; margin-top: 0">{{ eyebrow }}</p>
    <h2 class="hero-card__title">{{ title }}</h2>
    <p class="hero-card__subtitle">{{ subtitle }}</p>
    <div class="hero-card__footer">
      <van-button round type="default" size="small" @click="$emit('action')">
        {{ actionLabel }}
      </van-button>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow?: string;
  title: string;
  subtitle: string;
  actionLabel: string;
}>();

defineEmits<{ action: [] }>();
</script>
