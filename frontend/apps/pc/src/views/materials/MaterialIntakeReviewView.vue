<template>
 <section><header><h2>物料入库审核</h2><p>总监确认后自动入库，并关联来源方案；不会自动发布正式工艺。</p></header>
  <a-card><a-space style="margin-bottom:18px"><a-input-search v-model:value="query" placeholder="搜索物料或申请人" @search="load"/><a-select v-model:value="status" :options="options" style="width:160px" @change="load"/><a-button @click="load">刷新</a-button></a-space>
   <a-alert v-if="error" type="error" :message="error" show-icon />
   <a-table :data-source="items" :columns="columns" row-key="id" :loading="busy" :pagination="{pageSize:15}">
    <template #bodyCell="{column,record}"><template v-if="column.key==='names'">{{ record.lines.map((l:IntakeLine)=>l.name).join('、') }}</template><template v-if="column.key==='status'">{{ labels[record.status as keyof typeof labels] }}</template><template v-if="column.key==='action'"><a-button type="link" @click="show(record)">查看审核</a-button></template></template>
   </a-table>
  </a-card>
  <a-modal v-model:open="open" title="审核新增物料" :footer="null" :width="820" :mask-closable="!saving" :closable="!saving">
   <template v-if="selected"><p>申请人：{{ selected.applicantName }} · 来源版本：V{{ selected.sourceVersion }}</p><a-alert v-if="reviewError" type="error" :message="reviewError" show-icon/>
    <div v-for="line in selected.lines" :key="line.temporaryMaterialId" class="review-line"><b>{{ line.name }} · {{ line.code }}</b><p>{{ line.specification || '未填规格' }} · {{ line.materialRole==='PACKAGING'?`${line.quantity ?? '待补充'} ${line.quantityUnit || '待选单位'}`:`${line.totalWeightKg} kg` }}</p>
     <a-radio-group v-if="selected.status==='PENDING'" v-model:value="decisions[line.temporaryMaterialId].mode"><a-radio value="CREATE">新增入库</a-radio><a-radio value="LINK_EXISTING">关联已有物料</a-radio></a-radio-group>
     <MaterialPicker v-if="decisions[line.temporaryMaterialId]?.mode==='LINK_EXISTING'" :purpose="line.materialRole==='PACKAGING'?'packaging':'ingredient'" :current-material="linked[line.temporaryMaterialId]" @select="choose(line.temporaryMaterialId,$event)" />
    </div>
    <a-textarea v-model:value="comment" placeholder="退回批语（驳回必填）" :rows="2"/><a-space style="margin-top:16px" v-if="selected.status==='PENDING'"><a-button type="primary" :loading="saving" @click="approve">确认通过</a-button><a-button danger :disabled="saving" @click="reject">驳回重填</a-button></a-space><p v-else>{{ labels[selected.status] }} · {{ selected.comment }}</p>
   </template>
  </a-modal>
 </section>
</template>
<script setup lang="ts">
import {onMounted,ref} from 'vue';import {message,Modal} from 'ant-design-vue';import type {MaterialCatalogItem} from '@rnd/shared';
import {materialIntakeApi,type IntakeRequest,type IntakeLine} from '../../services/materialIntakeApi';import MaterialPicker from '../../components/materials/MaterialPicker.vue';
const query=ref(''),status=ref('PENDING'),items=ref<IntakeRequest[]>([]),busy=ref(false),error=ref(''),open=ref(false),selected=ref<IntakeRequest>(),saving=ref(false),reviewError=ref(''),comment=ref('');
const decisions=ref<Record<string,{mode:string;catalogMaterialId?:string}>>({}),linked=ref<Record<string,{materialName:string;materialCode:string;catalogMaterialId:string}>>({});
const labels={DRAFT:'草稿',PENDING:'待审核',APPROVED:'已入库',RETURNED:'退回重填'};const options=[{label:'全部状态',value:''},...Object.entries(labels).map(([value,label])=>({value,label}))];
const columns=[{title:'物料名称',key:'names'},{title:'申请人',dataIndex:'applicantName'},{title:'状态',key:'status'},{title:'操作',key:'action'}];
async function load(){busy.value=true;error.value='';try{items.value=await materialIntakeApi.list(query.value,status.value);}catch(e){error.value=e instanceof Error?e.message:'加载失败';}finally{busy.value=false;}}
function show(r:IntakeRequest){selected.value=r;decisions.value=Object.fromEntries(r.lines.map(l=>[l.temporaryMaterialId,{mode:'CREATE'}]));linked.value={};comment.value='';reviewError.value='';open.value=true;}
function choose(id:string,item:MaterialCatalogItem){decisions.value[id]={mode:'LINK_EXISTING',catalogMaterialId:item.id};linked.value[id]={materialName:item.name,materialCode:item.code,catalogMaterialId:item.id};}
async function perform(action:()=>Promise<unknown>){if(saving.value)return;saving.value=true;reviewError.value='';try{await action();open.value=false;message.success('审核处理完成');await load();}catch(e){reviewError.value=e instanceof Error?e.message:'处理失败';}finally{saving.value=false;}}
function approve(){const r=selected.value;if(!r)return;const values=r.lines.map(l=>({lineId:l.temporaryMaterialId,...decisions.value[l.temporaryMaterialId]}));if(values.some(x=>x.mode==='LINK_EXISTING'&&!x.catalogMaterialId)){reviewError.value='请选择要关联的已有物料';return;}Modal.confirm({title:'确认通过入库？',content:`新增 ${values.filter(x=>x.mode==='CREATE').length} 项，关联已有 ${values.filter(x=>x.mode==='LINK_EXISTING').length} 项。将更新来源方案关联。`,onOk:()=>perform(()=>materialIntakeApi.approve(r.id,r.version,values))});}
function reject(){const r=selected.value;if(!r)return;if(!comment.value.trim()){reviewError.value='请填写退回批语';return;}void perform(()=>materialIntakeApi.reject(r.id,r.version,comment.value));}
onMounted(load);
</script>
<style scoped>.review-line{padding:14px 0;border-bottom:1px solid #ddd7c7;margin-bottom:12px}header p{color:#817b6a}</style>
