<template>
 <section class="amendment-page">
  <header><div><h1>SOP 调整单</h1><p>依据车间记录调整 · 独立于研发打样，不更改原始记录</p></div><a-tag>{{ statusText }}</a-tag></header>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <a-spin :spinning="loading">
   <template v-if="record&&draft">
    <div class="card">
     <div class="toolbar"><span>来源正式版本 · {{ record.sourceRevisionId }} · 调整人 {{ record.createdBy }}</span>
      <a-space v-if="canEdit"><a-button :loading="busy" @click="save">保存草稿</a-button><a-button type="primary" :disabled="busy||!changes.length" @click="confirmation='submit'">提交总监审核</a-button><a-button danger :disabled="busy" @click="confirmation='cancel'">取消调整</a-button></a-space>
      <a-space v-if="canReview"><a-button :disabled="busy" @click="confirmation='return'">退回修改</a-button><a-button type="primary" :disabled="busy" @click="confirmation='approve'">批准并纳入正式版本</a-button></a-space>
     </div>
     <a-form layout="vertical"><a-form-item label="调整原因"><a-textarea v-model:value="reason" :disabled="!canEdit||busy" :maxlength="2000" /></a-form-item><a-form-item label="车间数据依据"><a-textarea v-model:value="evidenceNote" :disabled="!canEdit||busy" :maxlength="2000" placeholder="记录批次、日期、实测得率与参数；车间无需登录" /></a-form-item></a-form>
    </div>
    <div v-if="record.status!=='PUBLISHED'" class="card differences"><h3>调整对照 <small>{{ changes.length }} 处</small></h3><p v-if="!changes.length">尚无工艺修改。</p><div v-for="(change,index) in changes" :key="index" class="difference"><span>{{ change.fieldPath }}{{ change.derived?'（系统重算）':'' }}</span><del>{{ display(change.before) }}</del><strong>{{ display(change.after) }}</strong></div></div>
    <div class="card"><template v-if="record.status==='PUBLISHED'"><a-alert type="success" show-icon message="已纳入正式版本；正式 SOP 不显示修改红色，调整记录保留在历史中。" /><SopSnapshot :plan="draft" /></template>
     <template v-else><a-button v-if="majorKey" type="link" @click="majorKey=''">返回大工序</a-button><MinorStepWorkspace v-if="majorKey" v-model="draft" :major-key="majorKey" :readonly="!canEdit||busy" confirmation-mode="external"><template #selected-step-extension="{step}"><div v-if="stepChanges(step.id||step.key).length" class="step-changes">本步骤已调整，请对照上方红色变更记录。</div></template></MinorStepWorkspace><MajorProcessBoard v-else v-model="draft" :readonly="!canEdit||busy" @select="majorKey=$event" /></template>
    </div>
    <div class="card"><h3>处理记录</h3><div v-for="event in record.history" :key="event.versionNo" class="history">{{ event.actedAt.replace('T',' ').slice(0,16) }} · {{ event.actorName }} · {{ actionText(event.action) }}<p v-if="event.comment">{{ event.comment }}</p></div></div>
   </template>
  </a-spin>
  <a-modal :open="Boolean(confirmation)" :title="confirmationTitle" :confirm-loading="busy" @cancel="confirmation=''" @ok="confirmAction">
   <a-textarea v-if="confirmation==='return'" v-model:value="returnComment" :maxlength="2000" placeholder="请填写退回原因与修改要求（必填）" />
   <p v-else>{{ confirmation==='approve'?'确认已核对车间数据及全部修改？批准后生成新正式版本，不覆盖旧版本。':confirmation==='cancel'?'取消后本调整单不可再编辑，历史仍然保留。':'提交前将保存当前内容；提交后冻结，等待总监审核。' }}</p>
  </a-modal>
 </section>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import {normalizeProcessPlan,type ProcessPlanDraft} from '@rnd/shared';
import {useAuthStore} from '../../stores/auth';
import {sopAmendmentApi as api,type SopAmendment} from '../../services/sopAmendmentApi';
import {prepareSopSnapshot,sopDraftDifferences} from '../../services/sopAmendmentDraft';
import MajorProcessBoard from '../../components/process/MajorProcessBoard.vue';
import MinorStepWorkspace from '../../components/process/MinorStepWorkspace.vue';
import SopSnapshot from './SopSnapshot.vue';
const props=defineProps<{id:string}>(),auth=useAuthStore();
const record=ref<SopAmendment>(),draft=ref<ProcessPlanDraft>(),source=ref<ProcessPlanDraft>();
const reason=ref(''),evidenceNote=ref(''),error=ref(''),loading=ref(false),busy=ref(false),majorKey=ref(''),confirmation=ref(''),returnComment=ref('');
let generation=0;
const clone=<T,>(value:T):T=>JSON.parse(JSON.stringify(value));
const canEdit=computed(()=>auth.role==='RND_ASSISTANT'&&record.value?.createdById===auth.principal?.userId&&['DRAFT','RETURNED'].includes(record.value?.status??''));
const canReview=computed(()=>auth.role==='RND_DIRECTOR'&&record.value?.status==='PENDING_REVIEW');
const changes=computed(()=>source.value&&draft.value?sopDraftDifferences(source.value,draft.value):[]);
const statusText=computed(()=>({DRAFT:'草稿',RETURNED:'已退回',PENDING_REVIEW:'待审核',PUBLISHED:'已发布',CANCELLED:'已取消'}[record.value?.status??'']??'加载中'));
const confirmationTitle=computed(()=>({submit:'提交审核确认',cancel:'取消调整确认',return:'退回修改',approve:'发布正式 SOP 确认'}[confirmation.value]??''));
function display(value:unknown){return value==null?'未填写':typeof value==='object'?JSON.stringify(value):String(value);}
function actionText(action:string){return ({CREATED:'创建',SAVED:'保存',SUBMITTED:'提交审核',RETURNED:'退回',PUBLISHED:'批准发布',CANCELLED:'取消'}[action]??action);}
function stepChanges(id:string){return changes.value.filter(d=>d.entityId===id||d.fieldPath.includes(`/steps/${id}/`));}
function accept(value:SopAmendment){record.value=value;draft.value=normalizeProcessPlan(clone(value.snapshot));reason.value=value.reason;evidenceNote.value=value.evidenceNote??'';}
async function load(){const g=++generation;loading.value=true;error.value='';record.value=undefined;draft.value=undefined;source.value=undefined;try{const a=await api.get(props.id);const r=await api.source(a.formId,a.sourceRevisionId);if(g!==generation)return;accept(a);source.value=normalizeProcessPlan(clone(r.snapshot));}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'加载失败';}finally{if(g===generation)loading.value=false;}}
async function save(){if(!record.value||!draft.value||!canEdit.value||busy.value)return false;const g=generation;busy.value=true;error.value='';try{const a=await api.save(record.value.id,{versionNo:record.value.versionNo,reason:reason.value,evidenceNote:evidenceNote.value,snapshot:prepareSopSnapshot(clone(draft.value))});if(g!==generation)return false;accept(a);return true;}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'保存失败';return false;}finally{if(g===generation)busy.value=false;}}
async function submit(){if(!await save()||!record.value)return;await run(()=>api.submit(record.value!.id,record.value!.versionNo));}
async function run(action:()=>Promise<SopAmendment>){if(busy.value)return;const g=generation;busy.value=true;error.value='';try{const a=await action();if(g===generation){accept(a);confirmation.value='';}}catch(e){if(g===generation)error.value=e instanceof Error?e.message:'操作失败';}finally{if(g===generation)busy.value=false;}}
async function confirmAction(){if(!record.value)return;const a=record.value;if(confirmation.value==='submit'&&canEdit.value)await submit();else if(confirmation.value==='cancel'&&canEdit.value)await run(()=>api.cancel(a.id,a.versionNo));else if(confirmation.value==='return'&&canReview.value){if(!returnComment.value.trim()){error.value='请填写退回原因';return;}await run(()=>api.returnForChanges(a.id,a.versionNo,returnComment.value));}else if(confirmation.value==='approve'&&canReview.value)await run(async()=>(await api.approve(a.id,a.versionNo)).amendment);}
watch(()=>props.id,load,{immediate:true});onBeforeUnmount(()=>{generation++;});
</script>
<style scoped>
.amendment-page{max-width:1280px;margin:auto}.amendment-page header,.toolbar{display:flex;justify-content:space-between;align-items:center;gap:20px;flex-wrap:wrap}h1{margin:0}header p,.toolbar span{color:#86909c}.card{margin-top:18px;padding:24px;background:white;border:1px solid #e5e6eb;border-radius:12px}.toolbar{margin-bottom:20px}.difference{display:grid;grid-template-columns:2fr 1fr 1fr;gap:12px;padding:12px 0;border-top:1px solid #eee;color:#cf1322;overflow-wrap:anywhere}.difference del{color:#86909c}.step-changes{padding:12px;background:#fff2f0;color:#cf1322;border-radius:8px}.history{padding:12px 0;border-top:1px solid #eee}.history p{margin:8px 0;color:#666}@media(max-width:800px){.difference{grid-template-columns:1fr}.card{padding:16px}}
</style>
