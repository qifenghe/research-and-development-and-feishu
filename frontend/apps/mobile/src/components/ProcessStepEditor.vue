<template>
  <div class="process-editor">
    <template v-if="mode === 'arrange'">
      <div v-for="(step, index) in model" :key="index" class="process-arrange-row">
        <div class="process-arrange-row__number">{{ index + 1 }}</div>
        <div class="process-arrange-row__body">
          <van-field v-model="step.processName" label="工序" placeholder="填写工序名称" :readonly="readonly" />
          <van-field v-model="step.remark" label="控制要点" placeholder="可选" :readonly="readonly" />
          <div v-if="!readonly" class="process-arrange-row__actions">
            <van-button size="mini" plain :disabled="index === 0" @click="move(index, index - 1)">上移</van-button>
            <van-button size="mini" plain :disabled="index === model.length - 1" @click="move(index, index + 1)">下移</van-button>
            <van-button size="mini" plain @click="copy(index)">复制</van-button>
            <van-button size="mini" plain type="danger" :disabled="model.length === 1" @click="remove(index)">删除</van-button>
          </div>
        </div>
      </div>
      <van-button v-if="!readonly" block plain type="primary" size="small" @click="addStep">+ 新增工序</van-button>
    </template>

    <template v-else-if="mode === 'execute'">
      <div class="process-progress">
        <span>当前工序</span>
        <strong>{{ currentIndex + 1 }} / {{ model.length }}</strong>
      </div>
      <van-progress :percentage="progress" :show-pivot="false" color="#246BFE" />
      <div v-if="currentStep" class="process-execute-card">
        <p class="process-execute-card__eyebrow">工序 {{ currentIndex + 1 }}</p>
        <h2>{{ currentStep.processName || "未命名工序" }}</h2>
        <p v-if="currentStep.remark" class="process-execute-card__note">{{ currentStep.remark }}</p>
        <van-field
          v-model="currentStep.beforeWeightKg"
          label="投入重量"
          type="number"
          placeholder="请输入"
          :readonly="readonly"
        >
          <template #button>kg</template>
        </van-field>
        <van-field
          v-model="currentStep.afterWeightKg"
          label="下一步出成"
          type="number"
          placeholder="进入下一工序的重量"
          :readonly="readonly"
        >
          <template #button>kg</template>
        </van-field>
        <van-field
          v-model="currentStep.remainingWeightKg"
          label="余料重量"
          type="number"
          placeholder="没有余料可不填"
          :readonly="readonly"
        >
          <template #button>kg</template>
        </van-field>
        <van-field label="余料去向">
          <template #input>
            <van-radio-group v-model="currentStep.remainingDisposition" direction="horizontal" :disabled="readonly">
              <van-radio name="REUSE">回用</van-radio>
              <van-radio name="RETURN">退回</van-radio>
              <van-radio name="DISCARD">废弃</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <div v-if="lossWeightKg(currentStep) !== null" class="process-result-row">
          <span>工序损耗 {{ lossWeightKg(currentStep) }}kg</span>
          <strong>损耗率 {{ lossRatePercent(currentStep) }}%</strong>
        </div>
        <van-field
          v-model="currentStep.remark"
          rows="2"
          autosize
          type="textarea"
          label="备注"
          placeholder="异常情况或现场说明（选填）"
          :readonly="readonly"
        />
      </div>
      <div class="process-step-switcher">
        <van-button plain :disabled="currentIndex === 0" @click="emit('previous')">上一工序</van-button>
        <van-button
          type="primary"
          :disabled="currentIndex === model.length - 1"
          @click="emit('next')"
        >
          下一工序
        </van-button>
      </div>
    </template>

    <template v-else>
      <div v-for="(step, index) in model" :key="index" class="task-card process-step-card">
        <van-field v-model="step.processName" label="工序" readonly />
        <van-field v-model="step.beforeWeightKg" label="投入重量" readonly>
          <template #button>kg</template>
        </van-field>
        <van-field v-model="step.afterWeightKg" label="下一步出成" readonly>
          <template #button>kg</template>
        </van-field>
        <van-field v-model="step.remainingWeightKg" label="余料重量" readonly>
          <template #button>kg</template>
        </van-field>
        <p v-if="lossWeightKg(step) !== null" class="process-step-card__loss">
          工序损耗：{{ lossWeightKg(step) }}kg，损耗率：{{ lossRatePercent(step) }}%
        </p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import {
  blankProcessStep,
  copyProcessStep,
  lossWeightKg,
  lossRatePercent,
  moveProcessStep,
  removeProcessStep,
  type EditableProcessStep,
} from "./ProcessStepEditor.helpers";

const model = defineModel<EditableProcessStep[]>({ required: true });
const props = withDefaults(defineProps<{
  readonly?: boolean;
  mode?: "arrange" | "execute" | "readonly";
  currentIndex?: number;
}>(), {
  readonly: false,
  mode: "arrange",
  currentIndex: 0,
});
const emit = defineEmits<{ previous: []; next: [] }>();

const currentStep = computed(() => model.value[props.currentIndex]);
const progress = computed(() => model.value.length
  ? Math.round(((props.currentIndex + 1) / model.value.length) * 100)
  : 0);

function addStep() {
  model.value = [...model.value, blankProcessStep()];
}

function move(fromIndex: number, toIndex: number) {
  model.value = moveProcessStep(model.value, fromIndex, toIndex);
}

function copy(index: number) {
  model.value = copyProcessStep(model.value, index);
}

function remove(index: number) {
  model.value = removeProcessStep(model.value, index);
}
</script>

<style scoped>
.process-arrange-row{display:flex;gap:10px;margin-bottom:10px;padding:10px;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.process-arrange-row__number{display:flex;align-items:center;justify-content:center;width:30px;height:30px;margin-top:8px;border-radius:50%;background:#eaf2ff;color:#246bfe;font-weight:700}.process-arrange-row__body{min-width:0;flex:1}.process-arrange-row__actions{display:flex;flex-wrap:wrap;gap:6px;padding:8px 16px 4px}.process-progress{display:flex;justify-content:space-between;margin-bottom:8px;color:#64748b}.process-execute-card{margin-top:16px;padding:18px 14px;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.process-execute-card__eyebrow{margin:0 0 4px;color:#64748b;font-size:12px}.process-execute-card h2{margin:0 0 8px;font-size:22px}.process-execute-card__note{margin:0 0 14px;padding:10px 12px;background:#f7f9fc;border-radius:6px;color:#64748b;line-height:1.6}.process-result-row{display:flex;justify-content:space-between;margin:10px 16px;padding:12px;background:#eaf2ff;border-radius:6px;color:#246bfe}.process-step-switcher{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px}.process-step-card__loss{padding:0 16px;color:#246bfe}
</style>
