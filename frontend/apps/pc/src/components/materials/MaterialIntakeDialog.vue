<template>
 <a-modal v-model:open="open" title="整理新增物料" :width="850" :footer="null" :mask-closable="!busy" :closable="!busy">
  <p>仅整理当前已保存方案的临时外部物料。补充编码提交总监审核；通过后自动入库并关联本方案。</p>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <a-empty v-if="!available.length" description="暂无可提交的临时物料（待审核项不会重复提交）。" />
  <div v-for="item in available" :key="item.temporaryMaterialId" class="intake-line">
   <div><a-checkbox v-model:checked="selection[item.temporaryMaterialId]" :disabled="busy">{{ item.name }}</a-checkbox><small>{{ item.specification || '未填规格' }} · {{ item.materialRole==='PACKAGING'?`${item.quantity ?? '待补充'} ${item.quantityUnit || '待选单位'}`:`${item.totalWeightKg} kg` }}</small><small>{{ item.paths.join('；') }}</small></div>
   <a-input v-model:value="codes[item.temporaryMaterialId]" placeholder="补充物料编码" :disabled="busy" />
  </div>
  <a-space><a-button type="primary" :loading="busy" :disabled="!available.length" @click="submit">确认提交总监审核</a-button><a-button :disabled="busy" @click="open=false">关闭</a-button></a-space>
  <div v-if="requests.length" style="margin-top:20px"><h4>本方案申请记录</h4><p v-for="r in requests" :key="r.id">{{ labels[r.status] }} · {{ r.lines.map(l=>l.name).join('、') }}<span v-if="r.comment"> · 批语：{{ r.comment }}</span></p><small>退回后在步骤中修改临时物料，再重新整理提交。旧退回记录保留。</small></div>
 </a-modal>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue';import {message} from 'ant-design-vue';
import type {ProcessPlanDraft} from '@rnd/shared';import {collectTemporaryMaterials,availableTemporaryGroups} from '../../../../../packages/shared/src/temporary-materials';
import {materialIntakeApi,type IntakeRequest} from '../../services/materialIntakeApi';
const props=defineProps<{modelValue:boolean;plan:ProcessPlanDraft;formId:string;trialId?:string;sourceVersion:number}>();
const emit=defineEmits<{'update:modelValue':[boolean]}>();
const open=computed({get:()=>props.modelValue,set:v=>emit('update:modelValue',v)}),groups=computed(()=>collectTemporaryMaterials(props.plan));
const codes=ref<Record<string,string>>({}),selection=ref<Record<string,boolean>>({}),busy=ref(false),error=ref(''),requests=ref<IntakeRequest[]>([]);
const available=computed(()=>availableTemporaryGroups(groups.value,requests.value.filter(r=>r.status==='PENDING').flatMap(r=>r.lines.map(l=>l.temporaryMaterialId))));
const labels={DRAFT:'申请草稿',PENDING:'待总监审核',APPROVED:'已入库',RETURNED:'退回重填'};
watch(open,async value=>{if(!value)return;error.value='';for(const g of groups.value)selection.value[g.temporaryMaterialId]=true;try{requests.value=(await materialIntakeApi.list()).filter(x=>x.formId===props.formId&&(x.trialId||'')===(props.trialId||''));}catch(e){error.value=e instanceof Error?e.message:'申请记录加载失败';}});
async function submit(){if(busy.value)return;busy.value=true;error.value='';try{
 const lines=available.value.filter(x=>selection.value[x.temporaryMaterialId]).map(x=>({temporaryMaterialId:x.temporaryMaterialId,code:codes.value[x.temporaryMaterialId]?.trim()||''}));
 if(!lines.length)throw new Error('请至少选择一项物料');
 if(lines.some(x=>!x.code))throw new Error('请补全每项物料编码');
 if(requests.value.some(r=>r.status==='PENDING'&&r.lines.some(l=>lines.some(x=>x.temporaryMaterialId===l.temporaryMaterialId))))throw new Error('部分物料已有待审核申请，请等待审核或退回后重提');
 const r=await materialIntakeApi.create({formId:props.formId,trialId:props.trialId,sourceVersion:props.sourceVersion,lines});
 const submitted=await materialIntakeApi.submit(r.id,r.version);requests.value.unshift(submitted);message.success('已提交总监审核');
 }catch(e){error.value=e instanceof Error?e.message:'提交失败';}finally{busy.value=false;}}
</script>
<style scoped>.intake-line{display:grid;grid-template-columns:1fr 240px;gap:16px;padding:14px 0;border-bottom:1px solid #ded8c7;margin-bottom:10px}.intake-line div{display:flex;flex-direction:column;gap:5px}.intake-line small{color:#817c6e}</style>
