<template>
  <a-button v-if="canCompare" :disabled="!formId" @click="openComparison">同工序得率对比</a-button>
  <a-drawer v-model:open="open" title="同工序得率对比" width="1000">
    <a-alert message="使用可访问打样单的最新正式版本，最多200份。按工序编码、主料、投入/产出状态分组；缺少编码或主料链不完整的数据不参与比较。" type="info" show-icon />
    <a-space style="margin: 20px 0; width: 100%" wrap>
      <a-select v-model:value="group" :options="groups" placeholder="选择同口径工序" style="width: 440px" show-search :filter-option="filterGroup" />
      <a-select v-model:value="baseline" :options="baselineOptions" placeholder="选择对比基准" style="width: 290px" />
    </a-space>
    <a-alert v-if="error" :message="error" type="error" />
    <a-table :data-source="visibleRows" :columns="columns" :loading="loading" row-key="id" :pagination="{ pageSize: 10 }" :scroll="{ x: 880 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'revision'">R{{ record.revisionNo }} · 工序{{ record.sequence }}</template>
        <template v-else-if="column.key === 'yield'">{{ record.yieldPercent.toFixed(2) }}%</template>
        <template v-else-if="column.key === 'delta'">
          <a-tag v-if="base" :color="record.yieldPercent < base.yieldPercent ? 'orange' : 'green'">{{ delta(record.yieldPercent) }} 个百分点</a-tag>
        </template>
      </template>
    </a-table>
    <p style="color: #8c8c8c">偏差仅供研发定位问题，不代表产品不合格。请结合步骤参数和主料状态确认原因。</p>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { client } from '../../services/api';
import { useAuthStore } from '../../stores/auth';
type Row = {id:string;groupKey:string;productName:string;sampleNo:string;revisionNo:number;sequence:number;submittedAt:string;processCode:string;processName:string;materialName:string;inputState:string;outputState:string;inputKg:number;outputKg:number;yieldPercent:number};
const props = defineProps<{formId?: string}>();
const auth = useAuthStore();
const canCompare = computed(() => ['RND_DIRECTOR','RND_ENGINEER'].includes(auth.role));
const open = ref(false), loading = ref(false), error = ref('');
const rows = ref<Row[]>([]), group = ref<string>(), baseline = ref<string>();
let generation = 0;
const states: Record<string,string> = {SOLID:'固态',LIQUID:'液态',SEMI_SOLID:'半固态'};
const groups = computed(() => [...new Map(rows.value.map(row => [row.groupKey, {value:row.groupKey,label:`${row.processCode} ${row.processName} · ${row.materialName} · ${states[row.inputState] || row.inputState}→${states[row.outputState] || row.outputState}`}])).values()]);
const visibleRows = computed(() => rows.value.filter(row => row.groupKey === group.value));
const baselineOptions = computed(() => visibleRows.value.map(row => ({value:row.id,label:`${row.productName} / ${row.sampleNo} / R${row.revisionNo} / 工序${row.sequence}`})));
const base = computed(() => visibleRows.value.find(row => row.id === baseline.value));
const columns = [{title:'产品',dataIndex:'productName'},{title:'打样单',dataIndex:'sampleNo'},{title:'正式版本',key:'revision'},{title:'主料投入 kg',dataIndex:'inputKg'},{title:'主料产出 kg',dataIndex:'outputKg'},{title:'得率',key:'yield'},{title:'与基准差异',key:'delta'},{title:'提交时间',dataIndex:'submittedAt'}];
const filterGroup = (input: string, option: {label?: string}) => (option.label || '').toLowerCase().includes(input.toLowerCase());
function delta(value: number) { const diff = value - (base.value?.yieldPercent || 0); return `${diff > 0 ? '+' : ''}${diff.toFixed(2)}`; }
watch(group, () => { baseline.value = visibleRows.value[0]?.id; });
watch(() => props.formId, () => { generation++; open.value=false; rows.value=[]; group.value=undefined; baseline.value=undefined; loading.value=false; error.value=''; });
async function openComparison() {
  if (!props.formId) return;
  const current = ++generation;
  open.value = true; loading.value = true; error.value=''; rows.value=[];
  try {
    const response = await client.get<Row[]>(`/experiment-forms/${encodeURIComponent(props.formId)}/process-plan/revisions/comparison`);
    if (current !== generation) return;
    rows.value = response; group.value = groups.value[0]?.value; baseline.value = visibleRows.value[0]?.id;
  } catch(e) { if (current === generation) error.value = e instanceof Error ? e.message : '加载失败，请重试'; }
  finally { if (current === generation) loading.value = false; }
}
</script>
