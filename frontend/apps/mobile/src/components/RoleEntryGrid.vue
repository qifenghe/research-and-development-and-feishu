<template>
  <section class="role-entry-grid">
    <button
      v-for="entry in entries"
      :key="entry.route"
      type="button"
      class="role-entry-card"
      @click="$emit('select', entry.route)"
    >
      <span class="role-entry-card__label">{{ entry.label }}</span>
      <strong class="role-entry-card__title">{{ entry.title }}</strong>
      <span class="role-entry-card__desc">{{ entry.desc }}</span>
    </button>
  </section>
</template>

<script setup lang="ts">
export interface RoleEntry {
  label: string;
  title: string;
  desc: string;
  route: string;
}

defineProps<{ entries: RoleEntry[] }>();
defineEmits<{ select: [route: string] }>();
</script>
