<template>
  <a-drawer :open="open" title="配方汇总" width="min(760px, 100vw)" @close="$emit('update:open', false)">
    <div class="source"><b>{{ sourceLabel }}</b><a-tag :color="dirty ? 'orange' : 'green'">{{ dirty ? '页面未保存数据' : '已保存数据' }}</a-tag></div>
    <p class="hint">仅汇总外部投料，中间产物流转不重复计入。下载文件请回到“检查与研发预览”；有修改时须先保存。</p>
    <div class="total">外部投料合计 <strong>{{ kg(summary.externalTotalKg) }}</strong></div>
    <a-empty v-if="!summary.rows.length" description="尚未添加外部投料" />
    <article v-for="(row, index) in summary.rows" :key="index" class="material">
      <header><div><b>{{ row.materialName || '未命名物料' }}</b><small>{{ roles[row.materialRole] }} · {{ row.materialCode || '未填编码' }}</small></div><div class="amount"><b>{{ kg(row.weightKg) }}</b><small>{{ row.proportionPercent == null ? '比例待补充' : `${Number(row.proportionPercent.toFixed(4))}%` }}</small></div></header>
      <details><summary>查看 {{ row.occurrences.length }} 次投料</summary><div v-for="(item, i) in row.occurrences" :key="i" class="addition"><b>{{ item.majorName }} / {{ item.stepName }}</b><span>{{ kg(item.weightKg) }}</span><p>加入时机：{{ item.additionTiming || '未填写' }} · 加入方式：{{ item.additionMethod || '未填写' }}</p></div></details>
    </article>
  </a-drawer>
</template>
<script setup lang="ts">
import { computed } from "vue";
import type { ProcessPlanDraft } from "@rnd/shared";
import { buildRecipeSummary } from "./recipeSummary";
const props=defineProps<{open:boolean;plan:ProcessPlanDraft;sourceLabel:string;dirty:boolean}>();
defineEmits<{"update:open":[value:boolean]}>();
const summary=computed(()=>buildRecipeSummary(props.plan));
const roles:Record<string,string>={PRIMARY:"主料",AUXILIARY:"辅料",PROCESS_WATER:"工艺用水"};
function kg(value:number|null) {return value==null ? "待补充" : `${Number(value.toFixed(8))} kg`;}
</script>
<style scoped>
.source,.material header {display:flex;justify-content:space-between;gap:16px;align-items:start;}
.hint,small,summary {color:#86909c;font-size:12px;} .hint {line-height:1.7;}
.total {background:#f2f6ff;padding:16px;border-radius:8px;margin:16px 0;} .total strong {float:right;color:#165dff;}
.material {border:1px solid #e5e6eb;border-radius:8px;padding:16px;margin-bottom:12px;}
small {display:block;margin-top:4px;} .amount {text-align:right;white-space:nowrap;}
summary {cursor:pointer;margin-top:12px;} .addition {border-top:1px solid #f0f0f0;padding-top:10px;margin-top:10px;font-size:12px;}
.addition span {float:right;} .addition p {color:#4e5969;white-space:pre-wrap;overflow-wrap:anywhere;}
</style>
