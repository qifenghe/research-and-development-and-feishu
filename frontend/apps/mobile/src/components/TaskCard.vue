<template>
  <a
    v-if="to"
    :href="href"
    class="task-card task-card--link"
    @click="onClick"
  >
    <div class="task-card__row">
      <div>
        <h3 class="task-card__title">{{ title }}</h3>
        <p class="task-card__meta">{{ meta }}</p>
      </div>
      <span v-if="actionLabel" class="task-card__action">{{ actionLabel }}</span>
    </div>
    <slot />
  </a>
  <div v-else class="task-card">
    <div class="task-card__row">
      <div>
        <h3 class="task-card__title">{{ title }}</h3>
        <p class="task-card__meta">{{ meta }}</p>
      </div>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter, type RouteLocationRaw } from "vue-router";
import { resolveMobileHref, spaNavigate } from "../utils/navigate";

const props = defineProps<{
  title: string;
  meta: string;
  actionLabel?: string;
  to?: string | RouteLocationRaw;
}>();

const router = useRouter();
const href = computed(() => (props.to ? resolveMobileHref(router, props.to) : "#"));

function onClick(event: MouseEvent) {
  if (!props.to) return;
  event.preventDefault();
  spaNavigate(router, props.to);
}
</script>
