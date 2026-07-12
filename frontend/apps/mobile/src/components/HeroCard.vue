<template>
  <a :href="href" class="hero-card" @click="onClick">
    <p class="hero-card__eyebrow">{{ eyebrow }}</p>
    <h2 class="hero-card__title">{{ title }}</h2>
    <p class="hero-card__subtitle">{{ subtitle }}</p>
    <div class="hero-card__footer">
      <span class="hero-card__action">{{ actionLabel }}</span>
    </div>
  </a>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { resolveMobileHref, spaNavigate } from "../utils/navigate";

const props = defineProps<{
  to: string;
  eyebrow?: string;
  title: string;
  subtitle: string;
  actionLabel: string;
}>();

const router = useRouter();
const href = computed(() => resolveMobileHref(router, props.to));

function onClick(event: MouseEvent) {
  event.preventDefault();
  spaNavigate(router, props.to);
}
</script>
