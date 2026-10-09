<template>
 <div class="temporary-editor">
  <a-tag color="orange">试验临时物料 · 不入库</a-tag>
  <a-input :value="modelValue.materialName" placeholder="临时物料名称" @update:value="change({materialName:$event})" />
  <a-select :value="modelValue.temporaryClassificationId" :options="options" placeholder="选择已有分类" @update:value="change({temporaryClassificationId:$event})" />
  <a-input :value="modelValue.temporarySpecification" placeholder="规格（选填）" @update:value="change({temporarySpecification:$event})" />
  <small>{{ modelValue.materialRole==='PACKAGING'?'包装物按数量和单位记录':'投入单位 kg' }}；定稿时整理、补编码后交总监审核。</small>
 </div>
</template>
<script setup lang="ts">
import {computed,onMounted,ref} from 'vue';
import {classificationOptions,type MaterialClassification,type ProcessStepMaterialDraft} from '@rnd/shared';
import {materialCatalogApi} from '../../services/materialCatalogApi';
const props=defineProps<{modelValue:ProcessStepMaterialDraft}>();
const emit=defineEmits<{'update:modelValue':[value:ProcessStepMaterialDraft]}>();
const classifications=ref<MaterialClassification[]>([]);
const options=computed(()=>classificationOptions(classifications.value.filter(x=>props.modelValue.materialRole==='PACKAGING'?x.businessType==='PACKAGING':x.businessType!=='PACKAGING'),true));
function change(patch:Partial<ProcessStepMaterialDraft>){emit('update:modelValue',{...props.modelValue,...patch});}
onMounted(async()=>{try{classifications.value=await materialCatalogApi.classifications();}catch{classifications.value=[];}});
</script>
<style scoped>
.temporary-editor{grid-column:1/-1;display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:12px;border:1px dashed #cfc6aa;background:#fffdf6}.temporary-editor small{grid-column:1/-1;color:#81785f}
</style>
