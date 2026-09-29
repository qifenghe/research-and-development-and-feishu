<template>
 <a-modal v-model:open="open" title="分类管理 · 仅超级管理员" :width="760" :footer="null">
  <a-button type="primary" @click="start()">新增分类</a-button>
  <a-table :columns="columns" :data-source="items" row-key="id" :pagination="false" size="small" style="margin-top:16px">
   <template #bodyCell="{column,record}"><template v-if="column.key==='name'">{{ label(record.id) }}</template><template v-if="column.key==='status'">{{record.status==='ACTIVE'?'启用':'停用'}}</template><template v-if="column.key==='actions'"><a-button type="link" size="small" @click="start(record)">编辑</a-button><a-popconfirm :title="record.status==='ACTIVE'?'停用分类将阻止新物料归入，已有物料状态不变。':'确认启用分类？'" @confirm="toggle(record)"><a-button type="link" size="small">{{record.status==='ACTIVE'?'停用':'启用'}}</a-button></a-popconfirm></template></template>
  </a-table>
  <a-modal v-model:open="editing" :title="selected?'编辑分类':'新增分类'" :confirm-loading="saving" @ok="save">
   <a-form layout="vertical"><a-form-item label="分类名称"><a-input v-model:value="form.name" :maxlength="100" /></a-form-item>
    <a-form-item label="上级分类"><a-select v-model:value="form.parentId" :disabled="!!selected" allow-clear placeholder="不选则新建一级分类" :options="roots" /></a-form-item>
    <a-form-item v-if="!form.parentId" label="业务类型"><a-select v-model:value="form.businessType" :disabled="!!selected" :options="types" /></a-form-item>
    <a-form-item label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" /></a-form-item>
   </a-form>
  </a-modal>
 </a-modal>
</template>
<script setup lang="ts">
import {computed,reactive,ref} from 'vue';import {message} from 'ant-design-vue';
import {classificationOptions,type MaterialClassification} from '@rnd/shared';import {materialCatalogApi} from '../../services/materialCatalogApi';
const open=defineModel<boolean>('open',{default:false});const props=defineProps<{items:MaterialClassification[]}>();const emit=defineEmits<{changed:[]}>();
const editing=ref(false),saving=ref(false),selected=ref<MaterialClassification>();const form=reactive({name:'',parentId:undefined as string|undefined,businessType:'RAW',sortOrder:0});
const types=[{label:'原料',value:'RAW'},{label:'辅料',value:'AUXILIARY'},{label:'工艺用水',value:'PROCESS_WATER'},{label:'包装材料',value:'PACKAGING'}];
const roots=computed(()=>props.items.filter(i=>!i.parentId&&i.status==='ACTIVE').map(i=>({label:i.name,value:i.id})));
const columns=[{title:'分类',key:'name'},{title:'排序',dataIndex:'sortOrder'},{title:'状态',key:'status'},{title:'操作',key:'actions'}];
function label(id:string){return classificationOptions(props.items).find(i=>i.value===id)?.label||id;}
function start(item?:MaterialClassification){selected.value=item;Object.assign(form,{name:item?.name||'',parentId:item?.parentId||undefined,businessType:item?.businessType||'RAW',sortOrder:item?.sortOrder||0});editing.value=true;}
async function save(){if(!form.name.trim()){message.warning('请输入分类名称');return;}saving.value=true;try{const body={...form,businessType:form.parentId?undefined:form.businessType,expectedVersion:selected.value?.version};if(selected.value)await materialCatalogApi.updateClassification(selected.value.id,body);else await materialCatalogApi.createClassification(body);editing.value=false;emit('changed');message.success('分类已保存');}catch(e){message.error(e instanceof Error?e.message:'保存失败');}finally{saving.value=false;}}
async function toggle(item:MaterialClassification){try{await materialCatalogApi.classificationStatus(item);emit('changed');}catch(e){message.error(e instanceof Error?e.message:'状态修改失败');}}
</script>
