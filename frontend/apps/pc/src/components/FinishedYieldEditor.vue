<template>
  <div>
    <a-alert
      v-if="primaryInputWeight <= 0"
      type="warning"
      show-icon
      message="请先在配方中选择一个主原料，系统才能计算成品得率。"
      style="margin-bottom: 12px"
    />
    <a-row :gutter="16">
      <a-col :span="8">
        <a-statistic title="主原料投入重量" :value="primaryInputWeight" suffix="kg" :precision="3" />
      </a-col>
      <a-col :span="8">
        <a-form-item label="成品实际产出重量 kg">
          <a-input-number
            :value="modelValue"
            :min="0"
            style="width: 100%"
            :disabled="readonly"
            @change="emit('update:modelValue', Number($event ?? 0))"
          />
        </a-form-item>
      </a-col>
      <a-col :span="8">
        <a-statistic title="成品得率" :value="yieldPercent" suffix="%" :precision="2" />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { finishedYieldPercent } from "@rnd/shared";

const props = defineProps<{
  modelValue: number | null;
  primaryInputWeight: number;
  readonly?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: number | null];
}>();

const yieldPercent = computed(() => finishedYieldPercent(props.modelValue, props.primaryInputWeight));
</script>
