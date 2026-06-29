<template>
  <section class="role-entry-grid">
    <a
      v-for="entry in entries"
      :key="entry.route"
      :href="resolveHref(entry.route)"
      class="role-entry-card"
      @click="onClick($event, entry.route)"
    >
      <span class="role-entry-card__label">{{ entry.label }}</span>
      <strong class="role-entry-card__title">{{ entry.title }}</strong>
      <span class="role-entry-card__desc">{{ entry.desc }}</span>
    </a>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { resolveMobileHref, spaNavigate } from "../utils/navigate";

export interface RoleEntry {
  label: string;
  title: string;
  desc: string;
  route: string;
}

defineProps<{ entries: RoleEntry[] }>();

const router = useRouter();

function resolveHref(route: string) {
  return resolveMobileHref(router, route);
}

function onClick(event: MouseEvent, route: string) {
  event.preventDefault();
  spaNavigate(router, route);
}
</script>
