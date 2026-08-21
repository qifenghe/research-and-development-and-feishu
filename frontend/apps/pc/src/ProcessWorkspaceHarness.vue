<template>
  <main style="padding: 16px">
    <template v-if="scenario === 'experiment-parent'">
      <button data-testid="route-task-b" @click="router.push('/rnd/tasks/task-b/experiment')">切换任务 B</button>
      <div data-testid="active-task-route">{{ route.params.id }}</div>
      <ExperimentFormView />
    </template>

    <template v-else-if="scenario === 'hydration'">
      <button data-testid="accept-server" @click="acceptServer(false)">接受服务端方案</button>
      <button data-testid="accept-cache" @click="acceptServer(true)">接受服务端后恢复缓存</button>
      <div data-testid="parent-majors">{{ plan.majorProcesses.map(item => item.processName).join("|") }}</div>
      <ProcessPlanWorkspace
        ref="workspace"
        v-model="plan"
        form-id="form-hydration"
        :server-hydration-token="serverHydrationToken"
      />
    </template>

    <template v-else-if="scenario === 'submit'">
      <button data-testid="open-submit" @click="submitOpen = true">打开提交</button>
      <button data-testid="bump-version" @click="submitPlan.versionNo++">更新方案版本</button>
      <ProcessSubmitDialog v-model:open="submitOpen" :form-id="submitFormId" :plan="submitPlan" :ready="true" />
    </template>

    <template v-else-if="scenario === 'submit-revision'">
      <button data-testid="open-submit" @click="submitOpen = true">打开提交</button>
      <button data-testid="change-form" @click="submitFormId = 'form-submit-b'">切换表单</button>
      <ProcessSubmitDialog v-model:open="submitOpen" :form-id="submitFormId" :plan="revisionSubmitPlan" :ready="true" />
    </template>

    <template v-else-if="scenario === 'flow'">
      <button data-testid="inject-broken-flow" @click="injectBrokenFlow">制造待确认的失效流转</button>
      <div data-testid="consumer-count">{{ flowPlan.majorProcesses[1]?.steps[0]?.materials.length }}</div>
      <MinorStepWorkspace v-model="flowPlan" major-key="major-producer" />
    </template>

    <template v-else-if="scenario === 'artifacts'">
      <RndOutputCenter
        v-model:selected-revision-id="selectedRevisionId"
        form-id="form-artifacts"
        :revisions="revisions"
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  createEmptyProcessPlan,
  type ProcessPlanDraft,
  type ProcessRevisionSummary,
} from "@rnd/shared";
import MinorStepWorkspace from "./components/process/MinorStepWorkspace.vue";
import ProcessPlanWorkspace from "./components/process/ProcessPlanWorkspace.vue";
import ProcessSubmitDialog from "./components/process/ProcessSubmitDialog.vue";
import RndOutputCenter from "./components/process/RndOutputCenter.vue";
import ExperimentFormView from "./views/rnd/ExperimentFormView.vue";
import { cloneVueValue } from "./components/process/cloneVueValue";

const scenario = new URLSearchParams(location.search).get("scenario") || "hydration";
const route = useRoute();
const router = useRouter();
const plan = ref(createEmptyProcessPlan());
const serverHydrationToken = ref(0);
const workspace = ref<{ restoreLocalDirty: (value: ProcessPlanDraft) => void }>();
const submitOpen = ref(false);
const submitFormId = ref("form-submit");
const selectedRevisionId = ref("revision-1");

const submitPlan = ref({ ...createEmptyProcessPlan(), versionNo: 7 });
const revisionSubmitPlan = ref({ ...createEmptyProcessPlan(), versionNo: 9, sourceRevisionId: "source-revision" });
const revisions: ProcessRevisionSummary[] = [{
  id: "revision-1",
  revisionNo: 1,
  submittedAt: "2026-08-20T00:00:00Z",
  snapshotHash: "hash",
}];

const flowPlan = ref<ProcessPlanDraft>({
  ...createEmptyProcessPlan(),
  majorProcesses: [
    {
      key: "major-producer", sequence: 1, processName: "前段", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
      steps: [{
        key: "step-producer", sequence: 1, stepName: "产出", stepType: "NORMAL", materials: [], controlPoints: [],
        outputs: [{ id: "output-1", key: "output-1", sequence: 1, outputType: "INTERMEDIATE", outputName: "中间料", materialState: "SOLID", primaryOutput: true, continueFlow: true }],
      }],
    },
    {
      key: "major-consumer", sequence: 2, processName: "后段", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [],
      steps: [{
        key: "step-consumer", sequence: 1, stepName: "使用", stepType: "NORMAL", outputs: [], controlPoints: [],
        materials: [{ key: "material-consumer", sequence: 1, materialRole: "PRIMARY", materialName: "中间料", materialState: "SOLID", sourceType: "STEP_OUTPUT", sourceStepOutputId: "output-1" }],
      }],
    },
  ],
});

function singleMajor(name: string): ProcessPlanDraft {
  return {
    ...createEmptyProcessPlan(),
    versionNo: name === "服务端大工序" ? 4 : 5,
    majorProcesses: [{ key: name, sequence: 1, processName: name, yieldBasis: "NONE", inputs: [], outputs: [], steps: [] }],
  };
}

function injectBrokenFlow() {
  const next = cloneVueValue(flowPlan.value);
  next.majorProcesses[0]!.steps[0]!.outputs![0]!.continueFlow = false;
  flowPlan.value = next;
}

async function acceptServer(withCache: boolean) {
  plan.value = singleMajor("服务端大工序");
  serverHydrationToken.value++;
  await nextTick();
  if (withCache) {
    plan.value = singleMajor("缓存大工序");
    workspace.value?.restoreLocalDirty(plan.value);
  }
}
</script>
