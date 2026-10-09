<template>
 <div class="request-entry">
  <a-page-header :title="editId?'修改被驳回需求':'录入样品需求'" sub-title="内勤代销售录入；其他特殊要求如无，请填写“无”" />
  <a-alert v-if="editId && sampleNo" type="info" :message="`原样品编号：${sampleNo}，修改后重新提交审核`" show-icon />
  <a-alert v-if="rejectionComment" type="warning" :message="`驳回批语：${rejectionComment}`" show-icon />
  <a-alert v-if="submitError" type="error" :message="submitError" show-icon />
  <a-alert v-if="success" type="success" message="需求已提交，等待研发总监审核" show-icon />
  <a-form layout="vertical" :model="form" :disabled="initialLoading || blocked" @finish="submit" @finishFailed="submit">
   <a-card v-for="group in sampleRequestTemplateGroups" :key="group.title" :title="group.title">
    <a-row :gutter="[24,0]">
     <a-col v-for="field in group.fields" :key="field.key" :xs="24" :md="field.key==='specialRequirements'||field.key==='additionalNotes'?24:12">
      <a-form-item :label="field.label" :name="field.key" :required="field.required" :validate-status="errors[field.key]?'error':undefined" :help="errors[field.key]">
       <a-radio-group v-if="field.key==='referenceSampleProvided'" v-model:value="form.referenceSampleProvided"><a-radio :value="true">是</a-radio><a-radio :value="false">否</a-radio></a-radio-group>
       <a-textarea v-else v-model:value="form[field.key]" :placeholder="field.placeholder" :auto-size="{minRows:1,maxRows:5}" :maxlength="sampleRequestMaxLength(field.key)" />
      </a-form-item>
     </a-col>
    </a-row>
   </a-card>
   <a-card title="系统分类与申请信息">
    <a-row :gutter="24"><a-col :xs="24" :md="12"><a-form-item label="产品类型" name="productType" required :help="errors.productType" :validate-status="errors.productType?'error':undefined"><a-input v-model:value="form.productType" :maxlength="100" placeholder="如：预制菜、冷冻即热菜" /></a-form-item></a-col>
    <a-col :xs="24" :md="12"><a-form-item label="销售申请人" name="creatorName" required :help="errors.creatorName" :validate-status="errors.creatorName?'error':undefined"><a-auto-complete v-model:value="form.creatorName" :options="salesOptions" :filter-option="filterSales"><a-input placeholder="输入实际销售姓名，无需开通账号" :maxlength="100" /></a-auto-complete></a-form-item></a-col>
    <a-col :xs="24" :md="12"><a-form-item label="录入人" name="recorderName"><a-input :value="recorderName" readonly /></a-form-item></a-col></a-row>
   </a-card>
   <div class="actions"><a-button type="primary" html-type="submit" :loading="loading" :disabled="initialLoading || blocked">{{ editId?'重新提交审核':'提交需求' }}</a-button></div>
  </a-form>
 </div>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {emptySampleRequestTemplate,sampleRequestTemplateGroups,validateSampleRequestTemplate,sampleRequestMaxLength} from '@rnd/shared';
import {useAuthStore} from '../../stores/auth';
import {api} from '../../services/api';
const auth=useAuthStore(),loading=ref(false),submitError=ref(''),success=ref(false);
const route=useRoute(),router=useRouter();
const editId=computed(()=>String(route?.params.id||''));
const sampleNo=ref(''),rejectionComment=ref(''),reviewVersion=ref(0),recorderName=ref(auth.displayName),initialLoading=ref(false),blocked=ref(false);
const salesOptions=ref<Array<{value:string}>>([]);
const filterSales=(input:string,option:{value:string})=>option.value.includes(input.trim());
const errors=ref<Record<string,string>>({});
const form=reactive({...emptySampleRequestTemplate(),productType:'',creatorName:''});
function validate(){
 const current=validateSampleRequestTemplate(form);
 if(!form.productType.trim())current.productType='请填写产品类型';
 else if(form.productType.trim().length>100)current.productType='产品类型不能超过100字';
 if(!form.creatorName.trim())current.creatorName='请填写销售申请人';
 else if(form.creatorName.trim().length>100)current.creatorName='销售申请人不能超过100字';
 return current;
}
let loadGeneration=0;
watch(editId,async id=>{
 const generation=++loadGeneration;
 Object.assign(form,emptySampleRequestTemplate(),{productType:'',creatorName:''});
 sampleNo.value='';rejectionComment.value='';reviewVersion.value=0;recorderName.value=auth.displayName;errors.value={};submitError.value='';success.value=false;blocked.value=false;
 initialLoading.value=!!id;
 if(id)try{
  const detail=await api.sample.detail(id);
  if(generation!==loadGeneration)return;
  if(detail.status!=='REJECTED' || !detail.canResubmit)throw Error('当前需求不可修改，请从需求列表查看最新状态');
  for(const key of Object.keys(form))if(key in detail)(form as any)[key]=(detail as any)[key]??(key==='referenceSampleProvided'?null:'');
  sampleNo.value=detail.sampleNo;reviewVersion.value=detail.reviewVersion??0;recorderName.value=detail.recorderName||'历史记录未记录';
  rejectionComment.value=[...(detail.reviewHistory??[])].reverse().find(item=>item.action==='REJECTED')?.comment||'';
 }catch(e){if(generation===loadGeneration){blocked.value=true;submitError.value=e instanceof Error?e.message:'无法读取原需求';}}
 finally{if(generation===loadGeneration)initialLoading.value=false;}
 try{
  const rows=await api.sample.list();if(generation!==loadGeneration)return;
  salesOptions.value=[...new Set((rows as import('@rnd/shared').SampleRequest[]).map(row=>row.creatorName?.trim()).filter(Boolean))].map(value=>({value}));
 }catch{/* Historical suggestions are optional; users can type a new sales name. */}
},{immediate:true});
watch(form,()=>{
 if(!Object.keys(errors.value).length)return;
 const current=validate();
 errors.value=Object.fromEntries(Object.keys(errors.value).filter(key=>current[key]).map(key=>[key,current[key]]));
 if(!Object.keys(errors.value).length)submitError.value='';
});
async function submit(){
 if(loading.value || initialLoading.value || blocked.value)return;
 errors.value=validate();
 submitError.value='';success.value=false;
 if(Object.keys(errors.value).length){submitError.value='请补充或修正标出的项目';return;}
 loading.value=true;
 try{
  const payload={...form,creatorName:form.creatorName.trim()};
  if(editId.value){const id=editId.value;await api.sample.resubmit(id,reviewVersion.value,payload);await router.push(`/demand/${id}`);}
  else {await api.sample.create(payload);Object.assign(form,emptySampleRequestTemplate(),{productType:'',creatorName:''});success.value=true;}
 }
 catch(e){submitError.value=e instanceof Error?e.message:'提交失败，填写内容已保留';}
 finally{loading.value=false;}
}
</script>
<style scoped>
.request-entry{max-width:1100px;margin:0 auto}.ant-card{margin:16px 0;border-radius:12px}.ant-alert{margin-bottom:12px}.actions{padding:8px 0 24px}.ant-form-item{margin-bottom:18px}
</style>
