<template>
  <section class="sop-page">
    <header><h1>SOP 管理</h1><p>按产品查找正式工艺，查看操作要求并导出 Word。草稿与试验方案不在此列表。</p></header>
    <a-radio-group v-if="canAmend" v-model:value="tab" style="margin-bottom:18px" @change="load"><a-radio-button value="formal">正式 SOP</a-radio-button><a-radio-button value="amendments">调整与审核</a-radio-button></a-radio-group>
    <div v-if="tab==='amendments'" class="catalog-card"><form class="search" @submit.prevent="search"><a-input v-model:value="keyword" placeholder="搜索产品名称" /><a-button html-type="submit">搜索</a-button></form><a-alert v-if="listError" type="error" :message="listError" /><a-spin :spinning="loading"><table><thead><tr><th>来源</th><th>调整原因</th><th>状态</th><th>调整人</th><th>操作</th></tr></thead><tbody><tr v-for="a in amendments" :key="a.id"><td>{{ items.find(i=>i.formId===a.formId)?.productName||a.formId }}</td><td>{{ a.reason }}</td><td>{{ amendmentStatus(a.status) }}</td><td>{{ a.createdBy }}</td><td><a-button type="link" @click="router.push(`/sops/amendments/${a.id}`)">查看 / 处理</a-button></td></tr></tbody></table><a-empty v-if="!loading&&!amendments.length" description="暂无调整单" /></a-spin></div>
    <div v-else class="catalog-card">
      <form class="search" @submit.prevent="search"><a-input v-model:value="keyword" placeholder="搜索产品名称" allow-clear /><a-button html-type="submit" type="primary">搜索</a-button><span>共 {{ total }} 条</span></form>
      <a-alert v-if="listError" type="error" show-icon :message="listError" />
      <a-spin :spinning="loading">
        <div class="table-wrap"><table>
          <thead><tr><th>产品名称 / 规格</th><th>正式工艺</th><th>研发负责人</th><th>提交时间</th><th>操作</th></tr></thead>
          <tbody><template v-for="item in items" :key="item.formId">
            <tr><td><strong>{{ item.productName }}</strong><small>{{ item.specification || '未填写规格' }}</small></td><td><a-tag color="blue">R{{ item.revisionNo }}</a-tag><small>最新可见版本</small></td><td>{{ item.owner || '—' }}</td><td>{{ time(item.updatedAt) }}</td>
              <td><div class="actions"><a-button type="link" @click="openDetails(item,item.revisions[0])">查看 SOP</a-button><a-button type="link" :disabled="exporting" @click="openAndExport(item)">导出 Word</a-button><a-button type="link" @click="toggleHistory(item.formId)">历史版本</a-button><a-button v-if="auth.role==='RND_ASSISTANT'" type="link" @click="adjustmentItem=item;adjustmentReason='';adjustmentOpen=true">依据车间数据调整</a-button></div></td></tr>
            <tr v-if="expanded.has(item.formId)" class="history"><td colspan="5">
              <div v-for="revision in item.revisions" :key="revision.id" class="history-line"><a-tag>R{{ revision.revisionNo }}</a-tag><span>{{ time(revision.submittedAt) }} · {{ revision.submittedBy }}</span><span>{{ revision.changeReason || '无变更说明' }}</span><a-button size="small" @click="openDetails(item,revision)">查看 R{{ revision.revisionNo }}</a-button></div>
            </td></tr>
          </template></tbody>
        </table></div>
        <a-empty v-if="!loading&&!items.length&&!listError" description="暂无匹配的正式工艺；在打样中正式提交后即可查看" />
      </a-spin>
      <a-pagination v-if="total>20" v-model:current="page" :total="total" :page-size="20" :show-size-changer="false" @change="load" />
    </div>
    <a-modal :open="adjustmentOpen" title="创建 SOP 调整单" :confirm-loading="creating" @cancel="adjustmentOpen=false" @ok="createAdjustment"><p>从 {{ adjustmentItem?.productName }} 最新正式版本创建独立草稿，不更改研发记录。</p><a-textarea v-model:value="adjustmentReason" :maxlength="2000" placeholder="调整原因（必填）" /><a-alert v-if="createError" :message="createError" type="error" /></a-modal>
    <a-modal :open="detailOpen" :title="`${selected?.productName || ''} · SOP · R${selectedRevision?.revisionNo || ''}`" :footer="null" width="960px" @cancel="closeDetails">
      <div class="detail-actions"><span>正式工艺快照 · {{ selected?.specification || '未填写规格' }}</span><a-button v-if="canEnterExperiment" @click="enterExperiment">进入对应打样</a-button><a-button type="primary" :loading="exporting" :disabled="detailLoading||!snapshot||Boolean(detailError)" @click="exportCurrent()">导出 Word</a-button></div>
      <p class="notice">研发记录简要查看，不代表生产批准；完整记录请导出 Word。</p>
      <a-alert v-if="detailError" type="error" show-icon :message="detailError" />
      <a-alert v-if="exportError" type="warning" show-icon :message="exportError" />
      <a-button v-if="canRegenerate&&selectedRevision?.canGenerate" :loading="exporting" @click="exportCurrent(true)">重新生成并导出</a-button>
      <a-spin :spinning="detailLoading"><SopSnapshot v-if="snapshot" :plan="snapshot" /></a-spin>
    </a-modal>
  </section>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref} from 'vue';
import {useRouter} from 'vue-router';
import {ApiError,canAccessRoute,type ProcessPlanDraft} from '@rnd/shared';
import {sopCatalogApi,type SopCatalogItem,type SopRevision} from '../../services/sopCatalogApi';
import {api} from '../../services/api';
import {sopAmendmentApi,type SopAmendment} from '../../services/sopAmendmentApi';
import {exportApi} from '../../services/processExportApi';
import {useAuthStore} from '../../stores/auth';
import SopSnapshot from './SopSnapshot.vue';
const router=useRouter(),auth=useAuthStore();
const tab=ref('formal'),amendments=ref<SopAmendment[]>([]),adjustmentOpen=ref(false),adjustmentItem=ref<SopCatalogItem>(),adjustmentReason=ref(''),creating=ref(false),createError=ref('');
const canAmend=computed(()=>['RND_ASSISTANT','RND_DIRECTOR'].includes(auth.role));
function amendmentStatus(status:string){return ({DRAFT:'草稿',RETURNED:'已退回',PENDING_REVIEW:'待审核',PUBLISHED:'已发布',CANCELLED:'已取消'}[status]??status);}
async function createAdjustment(){if(!adjustmentItem.value||creating.value||auth.role!=='RND_ASSISTANT')return;if(!adjustmentReason.value.trim()){createError.value='请填写调整原因';return;}creating.value=true;createError.value='';try{const a=await sopAmendmentApi.create(adjustmentItem.value.formId,adjustmentItem.value.revisionId,adjustmentReason.value);adjustmentOpen.value=false;await router.push(`/sops/amendments/${a.id}`);}catch(e){createError.value=e instanceof Error?e.message:'创建失败';}finally{creating.value=false;}}
const keyword=ref(''),query=ref(''),page=ref(1),total=ref(0),items=ref<SopCatalogItem[]>([]),loading=ref(false),listError=ref('');
const expanded=ref(new Set<string>()),detailOpen=ref(false),detailLoading=ref(false),detailError=ref(''),exportError=ref(''),exporting=ref(false);
const selected=ref<SopCatalogItem>(),selectedRevision=ref<SopRevision>(),snapshot=ref<ProcessPlanDraft>();
const canRegenerate=ref(false);
let listRequest=0,detailRequest=0;
const canEnterExperiment=computed(()=>selected.value&&canAccessRoute(auth.role,`/rnd/tasks/${selected.value.taskId}/experiment`,'pc'));
function time(value:string){return value?.replace('T',' ').slice(0,16)||'—';}
function toggleHistory(id:string){const next=new Set(expanded.value);next.has(id)?next.delete(id):next.add(id);expanded.value=next;}
function search(){query.value=keyword.value.trim();page.value=1;void load();}
async function load(){
 const request=++listRequest;loading.value=true;listError.value='';
 if(tab.value==='amendments'){try{const result=await sopAmendmentApi.list(query.value);if(request===listRequest)amendments.value=result;}catch(e){if(request===listRequest)listError.value=e instanceof Error?e.message:'加载失败';}finally{if(request===listRequest)loading.value=false;}return;}
 items.value=[];
 try{const result=await sopCatalogApi.list(query.value,page.value);if(request===listRequest){items.value=result.items;total.value=result.total;}}
 catch(e){if(request===listRequest){total.value=0;listError.value=e instanceof Error?e.message:'列表加载失败';}}
 finally{if(request===listRequest)loading.value=false;}
}
function closeDetails(){detailRequest++;detailOpen.value=false;exporting.value=false;snapshot.value=undefined;}
async function openDetails(item:SopCatalogItem,revision:SopRevision){
 const request=++detailRequest;selected.value=item;selectedRevision.value=revision;detailOpen.value=true;detailLoading.value=true;snapshot.value=undefined;detailError.value='';exportError.value='';exporting.value=false;canRegenerate.value=false;
 try{const result=await (auth.role==='RND_ASSISTANT'?sopAmendmentApi.source(item.formId,revision.id):api.task.getProcessRevision(item.formId,revision.id));if(request!==detailRequest)return false;snapshot.value=result.snapshot;return true;}
 catch(e){if(request===detailRequest)detailError.value=e instanceof Error?e.message:'加载失败';return false;}
 finally{if(request===detailRequest)detailLoading.value=false;}
}
async function openAndExport(item:SopCatalogItem){if(await openDetails(item,item.revisions[0]))await exportCurrent();}
async function exportCurrent(force=false){
 if(!selected.value||!selectedRevision.value||exporting.value||!snapshot.value)return;
 const item=selected.value,revision=selectedRevision.value,request=detailRequest;
 exporting.value=true;exportError.value='';
 try{
  const assistant=auth.role==='RND_ASSISTANT';
  const artifacts=await (assistant?sopAmendmentApi.artifacts(item.formId,revision.id):api.task.getProcessArtifacts(item.formId,revision.id));
  if(request!==detailRequest)return;
  let artifact:{id:string;status:string;failureReason?:string|null;documentVersion:string|number}|undefined=force?undefined:artifacts.find(a=>a.artifactType==='SOP_DOCX'&&a.status==='READY');
  if(!artifact){
   if(!revision.canGenerate)throw new Error('该版本尚未生成 SOP，请联系负责研发或研发总监生成。');
   const check=assistant?{ready:true,issues:[]}:await exportApi.revisionCheck(item.formId,revision.id,'SOP_DOCX');
   if(request!==detailRequest)return;
   if(!check.ready)throw new Error('请先在打样中完善：'+check.issues.map(i=>i.message).join('；'));
   artifact=await (assistant?sopAmendmentApi.generate(item.formId,revision.id):api.task.generateProcessArtifact(item.formId,revision.id,'SOP_DOCX'));
   if(request!==detailRequest)return;
   if(!artifact||artifact.status!=='READY')throw new Error(artifact?.failureReason||'SOP 生成失败，请重试');
  }
  const blob=await (assistant?sopAmendmentApi.download(item.formId,revision.id,artifact.id):api.report.downloadProcessArtifact(item.formId,revision.id,artifact.id));
  if(request!==detailRequest)return;
  canRegenerate.value=false;
  const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;
  link.download=`${item.productName.replace(/[\\/:*?"<>|]/g,'-')}-SOP-R${revision.revisionNo}-${artifact.documentVersion}.docx`;
  document.body.append(link);link.click();link.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000);
 }catch(e){if(request===detailRequest){
  const damaged=e instanceof ApiError&&['PROCESS_ARTIFACT_FILE_NOT_FOUND','PROCESS_ARTIFACT_INTEGRITY_ERROR','PROCESS_ARTIFACT_NOT_READY'].includes(e.code||'');
  canRegenerate.value=damaged;
  exportError.value=(e instanceof Error?e.message:'导出失败')+(damaged&&!revision.canGenerate?'；请联系负责研发或研发总监重新生成。':'');
 }}
 finally{if(request===detailRequest)exporting.value=false;}
}
function enterExperiment(){if(selected.value)void router.push(`/rnd/tasks/${selected.value.taskId}/experiment`);}
onMounted(load);onBeforeUnmount(()=>{listRequest++;detailRequest++;});
</script>
<style scoped>
.sop-page{max-width:1400px;margin:auto}h1{font-size:24px;margin:0 0 8px}.sop-page header p{color:#86909c;margin:0 0 22px}.catalog-card{background:white;border:1px solid #e5e6eb;border-radius:12px;padding:20px}.search{display:flex;align-items:center;gap:12px;margin-bottom:20px}.search .ant-input-affix-wrapper{max-width:380px}.search span{color:#86909c;white-space:nowrap}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;min-width:740px}th{background:#f7f8fa;color:#4e5969;font-weight:500}th,td{padding:15px 12px;border-bottom:1px solid #f0f0f0}td:first-child{max-width:300px;overflow-wrap:anywhere}small{display:block;color:#86909c;margin-top:5px}.actions{display:flex;flex-wrap:wrap}.actions .ant-btn{padding:0 6px}.history{background:#fafbfc}.history-line{display:flex;align-items:center;gap:16px;padding:8px 0;color:#667085}.history-line span:nth-last-child(2){flex:1}.ant-pagination{margin-top:20px;text-align:right}.detail-actions{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin:12px 0 18px}.detail-actions>span{flex:1;color:#86909c}.ant-alert{margin-bottom:12px}
</style>
