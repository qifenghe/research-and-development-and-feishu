<template>
  <div class="timeline">
    <div
      v-for="(entry, index) in entries"
      :key="entry.id"
      class="timeline-item"
      :class="{
        'timeline-item--locked': entry.locked,
        'timeline-item--muted': entry.muted,
      }"
    >
      <div class="timeline-card" :class="{ 'timeline-card--current': entry.current ?? index === 0 }">
        <h3 class="task-card__title">{{ entry.title }}</h3>
        <p class="task-card__meta">{{ entry.subtitle }}</p>
        <p v-if="entry.summary" class="task-card__meta">{{ entry.summary }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface TimelineEntry {
  id: string;
  title: string;
  subtitle: string;
  summary?: string;
  locked?: boolean;
  muted?: boolean;
  current?: boolean;
}

defineProps<{ entries: TimelineEntry[] }>();
</script>
