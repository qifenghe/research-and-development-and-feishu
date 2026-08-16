<template>
  <div class="page-with-footer">
    <van-skeleton title :row="6" :loading="loading">
      <PageHeader :title="headerTitle" compact>
        <StatusBadge :label="statusBadge.label" :variant="statusBadge.variant" />
      </PageHeader>

      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="load">重新加载</van-button>
      </van-empty>

      <section v-if="isPackagingDraft && !loadError" class="packaging-panel">
        <div class="packaging-panel__header">
          <div>
            <h2>确认包装</h2>
            <p>包装可在此补充或调整；产品内袋标签、外箱标签已自动带入。</p>
          </div>
          <van-tag type="primary" plain>待确认</van-tag>
        </div>
        <div v-for="(item, index) in packagingItems" :key="item.clientKey" class="packaging-item">
          <div class="packaging-item__topline">
            <van-tag :type="item.source === 'SYSTEM_LABEL' ? 'primary' : item.source === 'MANUAL' ? 'warning' : 'default'">
              {{ sourceLabel(item.source) }}
            </van-tag>
            <van-button v-if="canEditPackaging && item.source !== 'SYSTEM_LABEL'" size="small" plain type="danger" @click="removePackagingItem(index)">删除</van-button>
          </div>
          <van-field v-model="item.materialName" label="包装名称" placeholder="如：内袋、外箱" :readonly="!canEditPackaging || item.source === 'SYSTEM_LABEL'" />
          <van-field v-model.number="item.quantity" label="数量" type="number" inputmode="decimal" placeholder="填写数量" :readonly="!canEditPackaging" />
          <van-field v-model="item.packageSpec" label="规格" placeholder="如：500g/袋、1张/箱" :readonly="!canEditPackaging" />
          <van-field v-model="item.remark" label="备注" placeholder="可选" :readonly="!canEditPackaging" />
        </div>
        <van-button v-if="canEditPackaging" block plain @click="addPackagingItem">+ 添加包装</van-button>
        <van-notice-bar v-else left-icon="info-o" color="#576b95" background="#f5f7fb">
          核价草稿已生成，等待研发总监或产品负责人确认包装。
        </van-notice-bar>
      </section>

      <InfoCard
        v-else-if="!isPackagingDraft"
        v-for="group in resolvedGroups"
        :key="group.title"
        :title="group.title"
        :rows="group.fields"
      />
      <div v-if="canReview && !loadError" class="form-panel pricing-review-note">
        <van-field v-model="reviewComment" label="退回原因" placeholder="退回时必填" />
      </div>
    </van-skeleton>

    <van-action-sheet
      v-model:show="reviewActionsVisible"
      :actions="reviewActions"
      cancel-text="取消"
      close-on-click-action
      @select="handleReviewAction"
    />

    <FixedActionBar
      v-if="detail && !loadError"
      :with-tabbar="false"
      :class="{ 'pricing-review-footer': canReview }"
    >
      <template v-if="canReview">
        <van-button type="primary" block :loading="reviewing" @click="review('APPROVE')">
          审核通过
        </van-button>
        <van-button block @click="reviewActionsVisible = true">更多操作</van-button>
      </template>
      <template v-else-if="isPackagingDraft && canEditPackaging">
        <van-button type="primary" block :loading="confirmingPackaging" @click="confirmPackaging">
          确认包装并生成核价文件
        </van-button>
      </template>
      <template v-else>
        <van-button
          v-if="canDownload"
          :type="hasWorkflowAction ? 'default' : 'primary'"
          :plain="hasWorkflowAction"
          block
          :loading="downloading"
          @click="download"
        >
          下载核价文件
        </van-button>
        <van-button
          v-if="canReceive"
          type="success"
          block
          :loading="receiving"
          @click="receive"
        >
          确认接收
        </van-button>
      </template>
    </FixedActionBar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import { resolvePricingDetailFields, type PricingFileDetailView, type PricingPackagingItem } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import InfoCard from "../components/InfoCard.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const downloading = ref(false);
const receiving = ref(false);
const reviewing = ref(false);
const confirmingPackaging = ref(false);
const reviewComment = ref("");
const reviewActionsVisible = ref(false);
const detail = ref<PricingFileDetailView | null>(null);
type EditablePackagingItem = PricingPackagingItem & { clientKey: string };
const packagingItems = ref<EditablePackagingItem[]>([]);
const loadError = ref("");
const reviewActions = [
  { name: "下载核价文件", value: "download" },
  { name: "退回核价", value: "reject", color: "var(--van-danger-color)" },
];
const canReceive = computed(() => (
  auth.role === "FINANCE" && detail.value?.pricingFile.status === "FINANCE_NOTIFIED"
));
const canDownload = computed(() => [
  "RND_ASSISTANT", "RND_DIRECTOR", "RND_ENGINEER", "FINANCE", "MANAGER", "ADMIN", "SYSTEM_ADMIN",
].includes(auth.role ?? ""));
const canReview = computed(() => (
  (auth.role === "RND_DIRECTOR" || auth.role === "RND_ENGINEER")
  && detail.value?.pricingFile.status === "PENDING_PRICING_REVIEW"
));
const canEditPackaging = computed(() => ["RND_DIRECTOR", "RND_ENGINEER", "SUPER_ADMIN"].includes(auth.role ?? ""));
const isPackagingDraft = computed(() => detail.value?.pricingFile.status === "DRAFT_PACKAGING");
const hasWorkflowAction = computed(() => canReview.value || canReceive.value);

const headerTitle = computed(() => {
  if (!detail.value) return "核价文件";
  return `${detail.value.pricingFile.productName} ${detail.value.pricingFile.versionCode}`;
});

const statusBadge = computed(() => {
  const status = detail.value?.pricingFile.status;
  if (status === "FINANCE_NOTIFIED") {
    return { label: "待财务接收", variant: "primary" as const };
  }
  if (status === "FINANCE_RECEIVED") {
    return { label: "财务已接收", variant: "success" as const };
  }
  if (status === "PRICING_APPROVED") return { label: "待自动移交", variant: "primary" as const };
  if (status === "PRICING_REJECTED") return { label: "核价退回", variant: "danger" as const };
  return { label: "待核价审核", variant: "primary" as const };
});

const resolvedGroups = computed(() => {
  if (!detail.value) return [];
  return resolvePricingDetailFields(detail.value).map((group) => ({
    title: group.title,
    fields: group.fields.map((field) => ({
      label: field.label,
      value: String(field.value ?? "-"),
    })),
  }));
});

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    detail.value = await api.shipment.pricingDetail(String(route.params.id));
    packagingItems.value = (detail.value.packagingItems ?? []).map((item, index) => ({
      ...item,
      clientKey: item.id ?? `packaging-${index}`,
    }));
  } catch (error) {
    detail.value = null;
    loadError.value = error instanceof Error ? error.message : "核价文件加载失败";
  } finally {
    loading.value = false;
  }
}

function sourceLabel(source: PricingPackagingItem["source"]) {
  return { TEMPLATE: "模板", SYSTEM_LABEL: "自动标签", MANUAL: "手工添加" }[source];
}

function addPackagingItem() {
  packagingItems.value.push({
    clientKey: `manual-${Date.now()}`,
    source: "MANUAL",
    materialName: "",
    quantity: 1,
    packageSpec: "",
    remark: "",
  });
}

function removePackagingItem(index: number) {
  packagingItems.value.splice(index, 1);
}

async function confirmPackaging() {
  if (!packagingItems.value.length) {
    showFailToast("请至少保留一项包装");
    return;
  }
  if (packagingItems.value.some((item) => !item.materialName.trim() || !Number(item.quantity) || Number(item.quantity) <= 0)) {
    showFailToast("请补全每项包装的名称和数量");
    return;
  }
  confirmingPackaging.value = true;
  try {
    await api.shipment.confirmPricingPackaging(String(route.params.id), packagingItems.value.map(({ clientKey, ...item }, index) => ({
      ...item,
      sequence: index + 1,
      materialCode: item.source === "SYSTEM_LABEL" ? undefined : item.materialCode?.trim() || undefined,
      materialName: item.materialName.trim(),
      packageSpec: item.packageSpec?.trim() || undefined,
      remark: item.remark?.trim() || undefined,
      confirmationStatus: "CONFIRMED",
    })));
    showSuccessToast("包装已确认，等待核价审核");
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "确认包装失败");
  } finally {
    confirmingPackaging.value = false;
  }
}

async function receive() {
  receiving.value = true;
  try {
    await api.shipment.receivePricingFile(String(route.params.id), auth.displayName);
    showSuccessToast("已确认接收");
    await router.replace("/todo");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "确认接收失败");
  } finally {
    receiving.value = false;
  }
}

async function review(decision: "APPROVE" | "REJECT") {
  if (decision === "REJECT" && !reviewComment.value.trim()) {
    showFailToast("请填写退回原因");
    return;
  }
  reviewing.value = true;
  try {
    await api.shipment.reviewPricingFile(String(route.params.id), {
      decision,
      comment: reviewComment.value.trim() || undefined,
    });
    showSuccessToast(decision === "APPROVE" ? "审核通过，已移交财务" : "核价已退回");
    await load();
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "核价审核失败");
  } finally {
    reviewing.value = false;
  }
}

function handleReviewAction(action: { value?: unknown }) {
  if (action.value === "download") {
    void download();
    return;
  }
  if (action.value === "reject") {
    void review("REJECT");
  }
}

async function download() {
  downloading.value = true;
  try {
    const blob = await api.shipment.downloadPricingFile(String(route.params.id));
    downloadBlob(blob, detail.value?.pricingFile.fileName ?? "pricing.xlsx");
    showSuccessToast("已开始下载");
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "下载失败");
  } finally {
    downloading.value = false;
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

onMounted(load);
</script>

<style scoped>
.packaging-panel { margin: 12px; padding: 16px; border-radius: 12px; background: #fff; }
.packaging-panel__header { display: flex; gap: 12px; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.packaging-panel h2 { margin: 0 0 6px; font-size: 18px; }
.packaging-panel p { margin: 0; color: #64748b; font-size: 13px; line-height: 1.5; }
.packaging-item { margin-bottom: 12px; overflow: hidden; border: 1px solid #e7ebf2; border-radius: 10px; }
.packaging-item__topline { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: #f8fafc; }
</style>
