<template>
 <div class="catalog-page">
  <header><div><h2>物料管理</h2><p>统一维护编码与名称，研发投料直接选用。</p></div><a-space><a-button v-if="access.classifications" @click="classificationsOpen=true">管理分类</a-button><a-button v-if="access.create" type="primary" @click="edit()">新增物料</a-button></a-space></header>
  <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom:16px" />
  <div class="catalog-body">
   <aside role="navigation" aria-label="物料分类"><div class="aside-heading">物料分类<span>按分类查找</span></div>
    <button class="category-all" :class="{active:!classificationId}" :aria-current="!classificationId?'true':undefined" @click="selectClass()">全部物料</button>
    <div class="category-tree">
     <div v-for="root in roots" :key="root.id" class="category-group">
      <div class="category-row" :class="{active:classificationId===root.id,'active-path':children(root.id).some(child=>child.id===classificationId)}">
       <button v-if="children(root.id).length" class="category-toggle" :aria-label="(expanded.has(root.id)?'收起':'展开')+root.name" :aria-expanded="expanded.has(root.id)" :aria-controls="'children-'+root.id" @click="toggleExpanded(root.id)"><span aria-hidden="true" :class="{rotated:expanded.has(root.id)}">›</span></button>
       <span v-else class="category-spacer" />
       <button class="category-name" :title="root.name" :aria-current="classificationId===root.id?'true':undefined" @click="selectClass(root.id)"><span>{{root.name}}</span><small v-if="root.status==='INACTIVE'">停用</small></button>
      </div>
      <div v-if="expanded.has(root.id)" :id="'children-'+root.id" class="category-children">
       <button v-for="child in children(root.id)" :key="child.id" class="category-child" :class="{active:classificationId===child.id}" :title="child.name" :aria-current="classificationId===child.id?'true':undefined" @click="selectClass(child.id)"><span>{{child.name}}</span><small v-if="child.status==='INACTIVE'">停用</small></button>
      </div>
     </div>
    </div>
    <label v-if="access.classifications" class="inactive-toggle"><input v-model="showInactive" type="checkbox" />显示停用分类</label>
   </aside>
   <main><div class="list-heading"><strong>{{classificationId?classLabel(classificationId):'全部物料'}}</strong><span>共 {{total}} 项</span></div><div class="filters"><a-input-search v-model:value="query" placeholder="搜索物料编码或名称" allow-clear @search="search" /><a-select v-model:value="status" :options="statuses" @change="search" /></div>
    <a-table :columns="columns" :data-source="items" row-key="id" :loading="busy" :scroll="{x:970}" :pagination="{current:page,pageSize:20,total,showSizeChanger:false}" @change="paginationChanged">
     <template #bodyCell="{column,record}"><template v-if="column.key==='classification'">{{classLabel(record.classificationId)}}</template><template v-if="column.key==='status'"><a-tag :color="record.status==='ACTIVE'?'green':'default'">{{record.status==='ACTIVE'?'启用':'停用'}}</a-tag></template><template v-if="column.key==='actions'"><a-button v-if="access.edit" type="link" size="small" @click="edit(record)">编辑</a-button><a-popconfirm v-if="access.status" :title="record.status==='ACTIVE'?'停用后不能新增投料，已有草稿正式提交需替换。':'确认启用物料？'" @confirm="toggle(record)"><a-button type="link" size="small">{{record.status==='ACTIVE'?'停用':'启用'}}</a-button></a-popconfirm></template></template>
     <template #emptyText>{{access.create?'暂无物料，点击右上角新增物料':'暂无物料，请联系研发内勤维护'}}</template>
    </a-table>
   </main>
  </div>
  <a-modal v-model:open="editing" :title="selected?'编辑物料':'新增物料'" :confirm-loading="saving" @ok="save">
   <a-form layout="vertical"><a-form-item label="物料编码"><a-input v-model:value="form.code" :disabled="!!selected" placeholder="创建后不可修改" :maxlength="100" /></a-form-item><a-form-item label="物料名称"><a-input v-model:value="form.name" :maxlength="200" /></a-form-item><a-form-item label="分类"><a-select v-model:value="form.classificationId" :options="assignmentOptions" placeholder="选择一级或二级分类" @change="syncUnit" /></a-form-item><a-form-item label="规格"><a-input v-model:value="form.specification" :maxlength="300" placeholder="可选，如部位、等级或规格" /></a-form-item><a-form-item label="计量单位"><a-select v-model:value="form.unit" :options="unitOptions" :disabled="!isPackaging" /></a-form-item></a-form>
  </a-modal>
  <MaterialClassificationManager v-if="access.classifications" v-model:open="classificationsOpen" :items="classifications" @changed="refreshClassifications" />
 </div>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue';import {message} from 'ant-design-vue';
import {classificationOptions,LatestMaterialQuery,type MaterialCatalogItem,type MaterialClassification} from '@rnd/shared';
import {materialCatalogApi,type CatalogAccess} from '../../services/materialCatalogApi';
import MaterialClassificationManager from '../../components/materials/MaterialClassificationManager.vue';
const items=ref<MaterialCatalogItem[]>([]),classifications=ref<MaterialClassification[]>([]),classificationId=ref<string>(),query=ref(''),status=ref('ACTIVE'),page=ref(1),total=ref(0),busy=ref(false),error=ref('');
const expanded=ref(new Set<string>()),showInactive=ref(false);
function toggleExpanded(id:string){const next=new Set(expanded.value);if(next.has(id))next.delete(id);else next.add(id);expanded.value=next;}
const access=ref<CatalogAccess>({create:false,edit:false,status:false,classifications:false}),gate=new LatestMaterialQuery();
const editing=ref(false),saving=ref(false),classificationsOpen=ref(false),selected=ref<MaterialCatalogItem>();
const form=reactive({code:'',name:'',classificationId:undefined as string|undefined,specification:'',unit:'kg'});
const roots=computed(()=>classifications.value.filter(i=>!i.parentId&&(showInactive.value||i.status==='ACTIVE')));
const assignmentOptions=computed(()=>{const list=classificationOptions(classifications.value,true);if(selected.value?.classificationId&&!list.some(i=>i.value===selected.value?.classificationId))list.push({label:classLabel(selected.value.classificationId)+'（保留原分类）',value:selected.value.classificationId});return list;});
const isPackaging=computed(()=>classifications.value.find(i=>i.id===form.classificationId)?.businessType==='PACKAGING');
const unitOptions=computed(()=>(isPackaging.value?['个','张','只','米','袋','盒','箱']:['kg']).map(value=>({label:value,value})));
const statuses=[{label:'全部状态',value:''},{label:'启用',value:'ACTIVE'},{label:'停用',value:'INACTIVE'}];
const columns=[{title:'编码',dataIndex:'code',width:170},{title:'名称',dataIndex:'name',width:220},{title:'分类',key:'classification',width:180},{title:'规格',dataIndex:'specification',width:120},{title:'单位',dataIndex:'unit',width:65},{title:'状态',key:'status',width:75},{title:'操作',key:'actions',width:140}];
function children(id:string){return classifications.value.filter(i=>i.parentId===id&&(showInactive.value||i.status==='ACTIVE'));}
function classLabel(id?:string){return classificationOptions(classifications.value).find(i=>i.value===id)?.label||'未分类';}
async function refreshClassifications(){try{classifications.value=await materialCatalogApi.classifications();}catch(e){error.value=e instanceof Error?e.message:'分类加载失败';}}
async function load(){busy.value=true;error.value='';try{await gate.run(()=>materialCatalogApi.list({query:query.value,status:status.value,page:page.value,size:20,classificationId:classificationId.value}),r=>{items.value=r.items;total.value=r.total;access.value=r.access||{create:false,edit:false,status:false,classifications:false};busy.value=false;});}catch(e){busy.value=false;error.value=e instanceof Error?e.message:'加载失败';}}
function search(){page.value=1;void load();}function selectClass(id?:string){classificationId.value=id;search();}function paginationChanged(p:{current?:number}){page.value=p.current||1;void load();}
watch(showInactive,visible=>{
  if(visible||!classificationId.value)return;
  const selected=classifications.value.find(i=>i.id===classificationId.value);
  const parent=classifications.value.find(i=>i.id===selected?.parentId);
  if(selected?.status==='INACTIVE'||parent?.status==='INACTIVE')selectClass();
});
function edit(item?:MaterialCatalogItem){selected.value=item;Object.assign(form,{code:item?.code||'',name:item?.name||'',classificationId:item?.classificationId||classificationId.value,specification:item?.specification||'',unit:item?.unit||'kg'});syncUnit();editing.value=true;}
function syncUnit(){if(!isPackaging.value)form.unit='kg';else if(form.unit==='kg')form.unit='个';}
async function save(){if(!form.code.trim()||!form.name.trim()||!form.classificationId){message.warning('请填写编码、名称并选择分类');return;}saving.value=true;try{const body={...form,expectedVersion:selected.value?.version};if(selected.value)await materialCatalogApi.update(selected.value.id,body);else await materialCatalogApi.create(body);editing.value=false;message.success('物料已保存');await load();}catch(e){message.error(e instanceof Error?e.message:'保存失败');}finally{saving.value=false;}}
async function toggle(item:MaterialCatalogItem){try{await materialCatalogApi.status(item);await load();}catch(e){message.error(e instanceof Error?e.message:'操作失败');}}
onMounted(()=>{void refreshClassifications();void load();});onBeforeUnmount(()=>gate.invalidate());
</script>
<style scoped>
.catalog-page{max-width:1500px;margin:auto}.catalog-page header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:22px}.catalog-page h2{margin:0 0 6px;font-size:24px}.catalog-page p{margin:0;color:#86909c}.catalog-body{display:grid;grid-template-columns:210px minmax(0,1fr);border:1px solid #e5e8ef;border-radius:12px;background:#fff;overflow:hidden;min-height:560px}.catalog-body aside{border-right:1px solid #eef0f4;padding:16px 12px}.aside-heading{font-weight:600;padding:8px 12px 16px}.catalog-body aside button{width:100%;text-align:left;display:block;border:0;background:transparent;padding:10px 12px;border-radius:6px;cursor:pointer;color:#4e5969}.catalog-body aside button.active{background:#e8f3ff;color:#165dff}.catalog-body aside button.child{padding-left:28px}.catalog-body aside small{float:right;color:#86909c}.catalog-body main{padding:24px;min-width:0}.filters{display:grid;grid-template-columns:minmax(180px,1fr) 120px;gap:12px;margin-bottom:20px}@media(max-width:900px){.catalog-body{grid-template-columns:160px minmax(0,1fr)}.catalog-body main{padding:14px}.catalog-page header{flex-wrap:wrap}}
</style>
<style scoped>
.catalog-body{grid-template-columns:224px minmax(0,1fr);min-height:620px}.catalog-body aside{background:#fafbfc;padding:20px 12px;display:flex;flex-direction:column;gap:4px}.aside-heading{padding:0 12px 16px;font-size:14px}.aside-heading span{display:block;font-size:12px;font-weight:400;color:#9aa2b1;margin-top:5px}.category-tree{margin-top:8px;max-height:62vh;overflow:auto}.category-group{margin:3px 0}.category-row{display:flex;align-items:center;border-radius:7px;min-width:0}.category-row:hover,.category-child:hover,.category-all:hover{background:#f0f2f6}.category-row.active,.catalog-body aside button.active{background:#eaf0ff;color:#2463eb}.category-row.active-path{color:#2463eb}.catalog-body aside .category-toggle{width:28px;flex:0 0 28px;padding:7px 0;text-align:center;color:inherit}.category-toggle span{display:inline-block;font-size:23px;line-height:20px;transition:transform .15s}.category-toggle .rotated{transform:rotate(90deg)}.category-spacer{flex:0 0 28px}.catalog-body aside .category-name{display:flex;align-items:center;gap:6px;padding:10px 10px 10px 0;color:inherit;min-width:0;flex:1}.category-name>span,.category-child>span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;min-width:0;flex:1}.catalog-body aside small{float:none;flex-shrink:0;font-size:10px}.category-children{margin:3px 0 8px 14px;padding-left:13px;border-left:1px solid #e2e7ef}.catalog-body aside .category-child{display:flex;align-items:center;gap:6px;padding:9px 10px;font-size:13px;margin:2px 0}.catalog-body aside .category-all{font-weight:500;padding-left:12px}.inactive-toggle{border-top:1px solid #e7eaf0;padding:16px 8px 0;margin-top:auto;display:flex;gap:7px;align-items:center;color:#86909c;font-size:12px;cursor:pointer}.catalog-body aside button:focus-visible{outline:2px solid #5289ff;outline-offset:-2px}.list-heading{display:flex;align-items:center;gap:12px;margin-bottom:20px}.list-heading strong{font-size:16px}.list-heading span{color:#86909c;font-size:12px}@media(max-width:900px){.catalog-body{grid-template-columns:180px minmax(0,1fr)}}
</style>
<style scoped>
.catalog-page header{margin-bottom:24px;align-items:center}.catalog-page p{font-size:13px;color:#76817b;line-height:1.7}.catalog-body{grid-template-columns:196px minmax(0,1fr);border-color:#ddded5;border-radius:10px;min-height:580px;box-shadow:0 4px 20px #24333004}.catalog-body aside{background:#f8f9f6;border-color:#e5e8df;padding:24px 12px}.aside-heading{color:#344943;letter-spacing:.5px}.aside-heading span{color:#8a948e;letter-spacing:0}.catalog-body main{padding:24px;overflow:hidden}.category-tree{margin-top:12px}.category-group{margin:5px 0}.catalog-body aside button{color:#52635c;transition:background .15s,color .15s}.category-row:hover,.category-child:hover,.category-all:hover{background:#eef2eb}.category-row.active,.catalog-body aside button.active{background:#e5eee7;color:#285d59}.category-row.active-path{color:#285d59}.category-children{border-color:#d9e2d8;margin-left:14px}.catalog-body aside .category-child{padding-top:8px;padding-bottom:8px}.inactive-toggle{border-color:#e0e5da;color:#7b887f}.inactive-toggle input{accent-color:#285d59}.catalog-body aside button:focus-visible{outline-color:#285d59}.list-heading{margin-bottom:18px;justify-content:space-between;padding-bottom:16px;border-bottom:1px solid #edf0e8}.list-heading strong{font-size:16px;color:#344943;font-weight:600}.list-heading span{color:#8a948e;font-variant-numeric:tabular-nums}.filters{margin-bottom:20px;grid-template-columns:minmax(180px,1fr) 120px}.catalog-body :deep(.ant-table){font-size:13px}.catalog-body :deep(.ant-table-thead th){background:#f3f5f0!important;color:#52635c;font-weight:500;white-space:nowrap;padding:13px 16px!important}.catalog-body :deep(.ant-table-tbody td){padding:14px 16px!important;line-height:1.6;vertical-align:middle;border-bottom-color:#edf0e8;overflow-wrap:anywhere}.catalog-body :deep(.ant-table-tbody td:first-child){font-variant-numeric:tabular-nums;color:#748177;font-size:12px}.catalog-body :deep(.ant-table-tbody tr:hover>td){background:#f8faf6!important}.catalog-body :deep(.ant-tag-green){background:#edf4ed;border-color:#d6e5d5;color:#4a7657}.catalog-body :deep(.ant-table-pagination){margin-top:24px}.catalog-page :deep(.ant-btn-link){color:#285d59}
@media(max-width:1100px){.catalog-body{grid-template-columns:172px minmax(0,1fr)}.catalog-body main{padding:20px}}@media(max-width:640px){.catalog-body{grid-template-columns:140px minmax(0,1fr)}.catalog-body aside{padding:18px 8px}.catalog-body main{padding:14px}.filters{grid-template-columns:1fr}.list-heading{flex-wrap:wrap;gap:6px}.catalog-page header{gap:12px}}
</style>
