<template>
  <div>
    <PageHeader title="录入样品需求" subtitle="研发内勤现场或日常办公直接提交样品清单" />

    <section class="quick-card">
      <div>
        <p class="quick-card__eyebrow">当前申请人</p>
        <h2 class="quick-card__title">{{ form.creatorName }}</h2>
      </div>
      <StatusBadge label="待总监审核" variant="primary" />
    </section>

    <van-form class="mobile-form" @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.productName"
          name="productName"
          label="产品名称"
          placeholder="如 500g香卤大肠头"
          required
          :rules="[{ required: true, message: '请填写产品名称' }]"
        />
        <van-field
          v-model="form.productType"
          name="productType"
          label="产品类型"
          placeholder="如 冷冻即热菜"
          required
          :rules="[{ required: true, message: '请填写产品类型' }]"
        />
        <van-field
          v-model="form.customerName"
          name="customerName"
          label="客户名称"
          placeholder="如 LHYC / 业务员客户"
          required
          :rules="[{ required: true, message: '请填写客户名称' }]"
        />
        <van-field
          v-model="form.specification"
          name="specification"
          label="规格"
          placeholder="如 500g/袋"
          required
          :rules="[{ required: true, message: '请填写规格' }]"
        />
        <van-field
          v-model="form.applicationScenario"
          name="applicationScenario"
          label="应用场景"
          type="textarea"
          rows="3"
          autosize
          placeholder="如商超零售冷冻即热、餐饮渠道、团餐、外卖、节礼等"
          required
          :rules="[{ required: true, message: '请填写应用场景' }]"
        />
        <van-field
          v-model="form.flavorRequirement"
          name="flavorRequirement"
          label="口味/风味要求"
          type="textarea"
          rows="3"
          autosize
          placeholder="如香卤、麻辣、酱香、微辣/重辣、复热后风味保持等"
          required
          :rules="[{ required: true, message: '请填写口味/风味要求' }]"
        />
        <van-field
          v-model="form.creatorName"
          name="creatorName"
          label="申请人"
          placeholder="申请人姓名"
          required
          :rules="[{ required: true, message: '请填写申请人' }]"
        />
      </van-cell-group>

      <div class="form-actions">
        <van-button block round type="primary" native-type="submit" :loading="loading">
          提交需求
        </van-button>
        <van-button block round plain type="primary" @click="resetForm">
          清空重填
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { showFailToast, showSuccessToast } from "vant";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

const auth = useAuthStore();
const loading = ref(false);
const form = reactive({
  productName: "",
  productType: "",
  customerName: "",
  specification: "",
  applicationScenario: "",
  flavorRequirement: "",
  creatorName: auth.displayName,
});

watch(
  () => auth.displayName,
  (name) => {
    if (!form.creatorName || form.creatorName === "未登录") {
      form.creatorName = name;
    }
  },
);

async function submit() {
  loading.value = true;
  try {
    await api.sample.create({ ...form });
    showSuccessToast("需求已提交，等待研发总监审核");
    resetForm();
    form.creatorName = auth.displayName;
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : "提交失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, {
    productName: "",
    productType: "",
    customerName: "",
    specification: "",
    applicationScenario: "",
    flavorRequirement: "",
  });
}
</script>
