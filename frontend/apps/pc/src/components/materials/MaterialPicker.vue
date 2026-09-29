<template>
 <div class="material-picker">
  <div class="identity"><strong>{{ currentMaterial?.materialName || '尚未选择物料' }}</strong><small>{{ currentMaterial?.materialCode || '无编码' }}<template v-if="currentMaterial?.catalogSpecification"> · {{ currentMaterial.catalogSpecification }}</template></small></div>
  <a-tag v-if="!currentMaterial?.catalogMaterialId" color="orange">待关联物料</a-tag>
  <a-button v-if="!readonly" size="small" @click="show">{{ currentMaterial?.catalogMaterialId ? '重新选择' : '选择物料' }}</a-button>
  <a-modal v-model:open="open" title="从物料库选择原辅料" :footer="null" :width="800" @cancel="gate.invalidate()">
   <div class="filters"><a-input-search v-model:value="query" placeholder="搜索物料编码或名称" @search="search" allow-clear />
    <a-select v-model:value="classificationId" :options="options" placeholder="全部分类" allow-clear @change="search" /></div>
   <a-alert v-if="error" type="error" :message="error" show-icon />
   <a-table :columns="columns" :data-source="items" row-key="id" :loading="busy" :pagination="{current:page,pageSize:20,total,showSizeChanger:false}" @change="paginationChanged" size="small">
    <template #bodyCell="{column,record}"><template v-if="column.key==='action'"><a-button type="link" @click="choose(record)">选用</a-button></template></template>
    <template #emptyText><div>暂无可选物料</div><a v-if="canCreate" href="/admin/materials" target="_blank" rel="noopener">前往物料管理新增</a><span v-else>请联系研发内勤维护物料</span></template>
   </a-table>
   <p class="hint">只选择启用的原辅料和工艺用水。包装材料不进入投料。</p>
  </a-modal>
 </div>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import {classificationOptions,LatestMaterialQuery,type MaterialCatalogItem,type MaterialClassification,type MaterialIdentity} from '@rnd/shared';
import {materialCatalogApi} from '../../services/materialCatalogApi';
const props=defineProps<{currentMaterial?:MaterialIdentity;readonly?:boolean}>();
const emit=defineEmits<{select:[item:MaterialCatalogItem]}>();
const open=ref(false),query=ref(''),classificationId=ref<string>(),page=ref(1),total=ref(0),busy=ref(false),error=ref(''),canCreate=ref(false);
const items=ref<MaterialCatalogItem[]>([]),classifications=ref<MaterialClassification[]>([]),gate=new LatestMaterialQuery();
const options=computed(()=>classificationOptions(classifications.value.filter(c=>c.businessType!=='PACKAGING')));
const columns=[{title:'编码',dataIndex:'code'},{title:'名称',dataIndex:'name'},{title:'规格',dataIndex:'specification'},{title:'单位',dataIndex:'unit'},{title:'',key:'action',width:70}];
async function show(){if(props.readonly)return;open.value=true;page.value=1;query.value='';classificationId.value=undefined;await load();}
async function load(){busy.value=true;error.value='';try{await gate.run(async()=>({data:await materialCatalogApi.list({query:query.value,classificationId:classificationId.value,page:page.value,size:20,status:'ACTIVE',ingredientsOnly:'true'}),categories:await materialCatalogApi.classifications()}),r=>{items.value=r.data.items;total.value=r.data.total;canCreate.value=!!r.data.access?.create;classifications.value=r.categories;busy.value=false;});}catch(e){busy.value=false;error.value=e instanceof Error?e.message:'物料加载失败';}}
function search(){page.value=1;void load();}
function paginationChanged(p:{current?:number}){page.value=p.current||1;void load();}
function choose(item:MaterialCatalogItem){if(props.readonly)return;emit('select',item);open.value=false;gate.invalidate();}
watch(()=>props.currentMaterial,()=>{if(open.value){open.value=false;gate.invalidate();}});
onBeforeUnmount(()=>gate.invalidate());
</script>
<style scoped>
.material-picker{display:flex;align-items:center;gap:8px;flex-wrap:wrap;grid-column:1/-1;padding:10px 12px;border:1px solid #e5e8ef;border-radius:8px;background:white}.identity{flex:1;min-width:140px;display:flex;flex-direction:column;gap:4px}.identity small,.hint{color:#86909c}.filters{display:grid;grid-template-columns:1fr 240px;gap:12px;margin:16px 0}@media(max-width:650px){.filters{grid-template-columns:1fr}}
</style>
