<template>
  <div>
    <PageHeader :title="productName" subtitle="版本时间线 · 只读" compact />

    <VersionTimeline :entries="timelineEntries" />

    <van-empty v-if="!loading && timelineEntries.length === 0" description="暂无历史版本" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import type { SampleVersionTimelineItem } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import VersionTimeline, { type TimelineEntry } from "../components/VersionTimeline.vue";
import { api } from "../services/api";

const route = useRoute();
const loading = ref(false);
const items = ref<SampleVersionTimelineItem[]>([]);
const productName = ref("实验单历史");

const timelineEntries = computed<TimelineEntry[]>(() =>
  items.value.map((item, index) => ({
    id: item.versionId,
    title: `${item.versionCode} · ${item.statusLabel}`,
    subtitle: item.subtitle,
    summary: item.summary,
    locked: item.locked,
    muted: index > 1,
    current: item.current ?? index === 0,
  })),
);

onMounted(async () => {
  loading.value = true;
  try {
    const versionId = String(route.params.versionId);
    const projectId = String(route.query.projectId ?? "");
    if (projectId) {
      items.value = await api.sample.versionTimeline(projectId);
    } else {
      const files = await api.shipment.archiveFiles(versionId);
      if (files[0]?.fileName) {
        productName.value = files[0].fileName.replace(/_.*$/, "");
      }
      items.value = [
        {
          versionId,
          versionCode: "当前版本",
          statusLabel: "归档文件",
          subtitle: files[0]?.uploadedAt ?? "",
          summary: `附件 ${files.length} 个`,
          locked: true,
          current: true,
        },
      ];
    }
    if (items.value[0]?.versionCode) {
      productName.value = route.query.productName?.toString() || productName.value;
    }
  } finally {
    loading.value = false;
  }
});
</script>
