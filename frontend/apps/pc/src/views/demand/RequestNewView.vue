<template>
 <div class="request-entry">
  <a-page-header title="录入样品需求" sub-title="按产品需求填报表填写；其他特殊要求如无，请填写“无”" />
  <a-alert v-if="submitError" type="error" :message="submitError" show-icon />
  <a-alert v-if="success" type="success" message="需求已提交，等待研发总监审核" show-icon />
  <a-form layout="vertical" :model="form" @finish="submit" @finishFailed="submit">
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
    <a-row :gutter="24"><a-col :xs="24" :md="12"><a-form-item label="产品类型" required :help="errors.productType" :validate-status="errors.productType?'error':undefined"><a-input v-model:value="form.productType" placeholder="如：预制菜、冷冻即热菜" /></a-form-item></a-col>
    <a-col :xs="24" :md="12"><a-form-item label="申请人"><a-input :value="auth.displayName" readonly /></a-form-item></a-col></a-row>
   </a-card>
   <div class="actions"><a-button type="primary" html-type="submit" :loading="loading">提交需求</a-button></div>
  </a-form>
 </div>
</template>
<script setup lang="ts">
import {reactive,ref,watch} from 'vue';
import {emptySampleRequestTemplate,sampleRequestTemplateGroups,validateSampleRequestTemplate,sampleRequestMaxLength} from '@rnd/shared';
import {useAuthStore} from '../../stores/auth';
import {api} from '../../services/api';
const auth=useAuthStore(),loading=ref(false),submitError=ref(''),success=ref(false);
const errors=ref<Record<string,string>>({});
const form=reactive({...emptySampleRequestTemplate(),productType:'',creatorName:auth.displayName});
watch(form,()=>{
 if(!Object.keys(errors.value).length)return;
 const current=validateSampleRequestTemplate(form);
 if(!form.productType.trim())current.productType='请填写产品类型';
 else if(form.productType.trim().length>100)current.productType='产品类型不能超过100字';
 errors.value=Object.fromEntries(Object.keys(errors.value).filter(key=>current[key]).map(key=>[key,current[key]]));
 if(!Object.keys(errors.value).length)submitError.value='';
});
async function submit(){
 if(loading.value)return;
 errors.value=validateSampleRequestTemplate(form);
 if(!form.productType.trim())errors.value.productType='请填写产品类型';
 else if(form.productType.trim().length>100)errors.value.productType='产品类型不能超过100字';
 submitError.value='';success.value=false;
 if(Object.keys(errors.value).length){submitError.value='请补充或修正标出的项目';return;}
 loading.value=true;
 try{await api.sample.create({...form,creatorName:auth.displayName});Object.assign(form,emptySampleRequestTemplate(),{productType:'',creatorName:auth.displayName});success.value=true;}
 catch(e){submitError.value=e instanceof Error?e.message:'提交失败，填写内容已保留';}
 finally{loading.value=false;}
}
</script>
<style scoped>
.request-entry{max-width:1100px;margin:0 auto}.ant-card{margin:16px 0;border-radius:12px}.ant-alert{margin-bottom:12px}.actions{padding:8px 0 24px}.ant-form-item{margin-bottom:18px}
</style>
