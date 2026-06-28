<template>
  <div>
    <a-page-header title="研发内勤：录入样品需求" sub-title="研发内勤从飞书进入，录入需要做的样品清单" />
    <a-card>
      <a-form layout="vertical" :model="form" @finish="submit">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="产品名称" name="productName" :rules="[{ required: true }]"><a-input v-model:value="form.productName" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="产品类型" name="productType" :rules="[{ required: true }]"><a-input v-model:value="form.productType" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="客户名称" name="customerName" :rules="[{ required: true }]"><a-input v-model:value="form.customerName" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="规格" name="specification" :rules="[{ required: true }]"><a-input v-model:value="form.specification" /></a-form-item></a-col>
          <a-col :span="24">
            <a-form-item label="应用场景" name="applicationScenario" :rules="[{ required: true, message: '请填写应用场景' }]">
              <a-textarea
                v-model:value="form.applicationScenario"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                placeholder="例如：商超零售冷冻即热、餐饮渠道、团餐、外卖、节礼等"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="口味/风味要求" name="flavorRequirement" :rules="[{ required: true, message: '请填写口味/风味要求' }]">
              <a-textarea
                v-model:value="form.flavorRequirement"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                placeholder="例如：香卤、麻辣、酱香、微辣/重辣、咸淡、复热后风味保持等"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12"><a-form-item label="申请人" name="creatorName" :rules="[{ required: true }]"><a-input v-model:value="form.creatorName" /></a-form-item></a-col>
        </a-row>
        <a-button type="primary" html-type="submit" :loading="loading">提交需求</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { useAuthStore } from "../../stores/auth";
import { api } from "../../services/api";

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

async function submit() {
  loading.value = true;
  try {
    await api.sample.create({ ...form });
    message.success("需求已提交");
    Object.assign(form, {
      productName: "",
      productType: "",
      customerName: "",
      specification: "",
      applicationScenario: "",
      flavorRequirement: "",
    });
  } catch (error) {
    message.error(error instanceof Error ? error.message : "提交失败");
  } finally {
    loading.value = false;
  }
}
</script>
