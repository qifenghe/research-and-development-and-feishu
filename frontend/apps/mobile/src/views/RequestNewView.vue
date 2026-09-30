<template>
 <div>
  <PageHeader title="录入样品需求" subtitle="其他特殊要求如无，请填写“无”" />
  <van-notice-bar v-if="submitError" :text="submitError" />
  <van-notice-bar v-if="success" text="需求已提交，等待研发总监审核" />
  <van-form @submit="submit">
   <section v-for="group in sampleRequestTemplateGroups" :key="group.title">
    <p class="form-section-title">{{group.title}}</p>
    <van-cell-group inset>
     <template v-for="field in group.fields" :key="field.key">
      <van-field v-if="field.key==='referenceSampleProvided'" :name="field.key" :label="field.label" required :error-message="errors[field.key]">
       <template #input><van-radio-group v-model="form.referenceSampleProvided" direction="horizontal"><van-radio :name="true">是</van-radio><van-radio :name="false">否</van-radio></van-radio-group></template>
      </van-field>
      <van-field v-else v-model="form[field.key]" :name="field.key" :label="field.label" :placeholder="field.placeholder" :required="field.required" :error-message="errors[field.key]" type="textarea" rows="1" autosize maxlength="2000" />
     </template>
    </van-cell-group>
   </section>
   <p class="form-section-title">系统分类与申请信息</p>
   <van-cell-group inset><van-field v-model="form.productType" label="产品类型" placeholder="如：预制菜" required :error-message="errors.productType" /><van-field :model-value="auth.displayName" label="申请人" readonly /></van-cell-group>
   <div class="form-actions"><van-button block type="primary" native-type="submit" :loading="loading">提交需求</van-button></div>
  </van-form>
 </div>
</template>
<script setup lang="ts">
import {reactive,ref,watch} from 'vue';
import {emptySampleRequestTemplate,sampleRequestTemplateGroups,validateSampleRequestTemplate} from '@rnd/shared';
import PageHeader from '../components/PageHeader.vue';
import {useAuthStore} from '../stores/auth';
import {api} from '../services/api';
const auth=useAuthStore(),loading=ref(false),submitError=ref(''),success=ref(false);
const errors=ref<Record<string,string>>({});
const form=reactive({...emptySampleRequestTemplate(),productType:'',creatorName:auth.displayName});
watch(form,()=>{
 if(!Object.keys(errors.value).length)return;
 const current=validateSampleRequestTemplate(form);
 if(!form.productType.trim())current.productType='请填写产品类型';
 errors.value=Object.fromEntries(Object.keys(errors.value).filter(key=>current[key]).map(key=>[key,current[key]]));
 if(!Object.keys(errors.value).length)submitError.value='';
});
async function submit(){
 if(loading.value)return;
 errors.value=validateSampleRequestTemplate(form);
 if(!form.productType.trim())errors.value.productType='请填写产品类型';
 submitError.value='';success.value=false;
 if(Object.keys(errors.value).length){submitError.value='请补充或修正标出的项目';return;}
 loading.value=true;
 try{await api.sample.create({...form,creatorName:auth.displayName});Object.assign(form,emptySampleRequestTemplate(),{productType:'',creatorName:auth.displayName});success.value=true;}
 catch(e){submitError.value=e instanceof Error?e.message:'提交失败，填写内容已保留';}
 finally{loading.value=false;}
}
</script>
<style scoped>.form-actions{padding:24px 16px}.form-section-title{padding:0 16px;margin:22px 0 10px;font-weight:600}.van-notice-bar{margin:12px 16px;border-radius:8px}</style>
