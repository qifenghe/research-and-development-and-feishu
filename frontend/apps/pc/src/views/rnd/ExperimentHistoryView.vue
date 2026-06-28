<template>
  <div>
    <a-page-header
      :title="productName"
      sub-title="按样品版本查阅已锁定和草稿实验单，保留每次打样调整记录"
      @back="router.back()"
    />
    <a-card :loading="loading">
      <a-empty v-if="!loading && timelineItems.length === 0" description="暂无历史版本" />
      <a-timeline v-else mode="left">
        <a-timeline-item
          v-for="item in timelineItems"
          :key="item.versionId"
          :color="item.current ? 'blue' : item.locked ? 'green' : 'gray'"
        >
          <a-card
            size="small"
            :bordered="true"
            :style="item.current ? { borderColor: '#246BFE' } : undefined"
          >
            <template #title>
              <a-space>
                <span>{{ item.versionCode }} · {{ item.statusLabel }}</span>
                <a-tag v-if="item.current" color="blue">当前</a-tag>
                <a-tag v-if="item.locked" color="green">已锁定</a-tag>
              </a-space>
            </template>
            <p style="margin: 0; color: #64748b">{{ item.subtitle }}</p>
            <p v-if="item.summary" style="margin: 8px 0 0">{{ item.summary }}</p>
          </a-card>
        </a-timeline-item>
      </a-timeline>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { SampleVersionTimelineItem } from "@rnd/shared";
import { api } from "../../services/api";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const timelineItems = ref<SampleVersionTimelineItem[]>([]);
const productName = ref("实验单历史版本");

onMounted(async () => {
  loading.value = true;
  try {
    const projectId = String(route.query.projectId ?? "");
    const versionId = String(route.params.versionId);
    if (route.query.productName) {
      productName.value = String(route.query.productName);
    }

    if (projectId) {
      timelineItems.value = await api.sample.versionTimeline(projectId);
      return;
    }

    const taskId = String(route.query.taskId ?? "");
    if (taskId) {
      const detail = await api.task.detail(taskId, "RND_DIRECTOR");
      productName.value = detail.task.productName;
      timelineItems.value = await api.sample.versionTimeline(detail.version.projectId);
      return;
    }

    const files = await api.shipment.archiveFiles(versionId);
    timelineItems.value = [
      {
        versionId,
        versionCode: "当前版本",
        statusLabel: "归档附件",
        subtitle: files[0]?.uploadedAt ?? "",
        summary: `附件 ${files.length} 个`,
        locked: true,
        current: true,
      },
    ];
  } finally {
    loading.value = false;
  }
});
</script>
