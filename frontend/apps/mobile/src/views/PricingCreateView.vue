<template>
  <div class="page-with-footer">
    <van-skeleton title :row="5" :loading="loading">
      <PageHeader title="生成核价文件" subtitle="使用已通过并锁定的实验数据生成 Excel" />
      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="load">重新加载</van-button>
      </van-empty>
      <InfoCard v-else-if="version" title="样品版本" :rows="versionRows" />
    </van-skeleton>

    <FixedActionBar v-if="version && !loadError" :with-tabbar="false">
      <van-button type="primary" block :loading="generating" @click="generate">
        生成核价 Excel
      </van-button>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { PricingReadyVersion } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import InfoCard from "../components/InfoCard.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const generating = ref(false);
const loadError = ref("");
const version = ref<PricingReadyVersion | null>(null);

const versionRows = computed(() => version.value ? [
  { label: "产品", value: version.value.productName },
  { label: "样品编号", value: version.value.sampleNo },
  { label: "样品版本", value: version.value.versionCode },
] : []);

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const rows = await api.shipment.pricingReadyVersions();
    version.value = rows.find((item) => item.versionId === String(route.params.id)) ?? null;
    if (!version.value) loadError.value = "该版本已生成核价文件或尚未通过测试";
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "待核价版本加载失败";
  } finally {
    loading.value = false;
  }
}

async function generate() {
  if (!version.value) return;
  generating.value = true;
  try {
    const file = await api.shipment.createPricingFile(version.value.versionId);
    showSuccessToast(`已生成 ${file.pricingVersion}`);
    await router.replace(`/pricing/${file.id}`);
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "核价文件生成失败");
  } finally {
    generating.value = false;
  }
}

onMounted(load);
</script>
