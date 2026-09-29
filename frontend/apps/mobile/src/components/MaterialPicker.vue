<template>
 <van-cell :title="currentMaterial.materialName||'选择物料'" :label="`${currentMaterial.materialCode||'无编码'} · ${materialLinkLabel(currentMaterial)}`" :is-link="!readonly" @click="show" />
 <van-popup v-model:show="open" position="bottom" round :style="{height:'80%'}" @closed="gate.invalidate()">
  <van-nav-bar title="选择原辅料" right-text="关闭" @click-right="open=false;gate.invalidate()" />
  <van-search v-model="query" placeholder="搜索编码或名称" @search="search" @clear="search" />
  <div class="classification"><label for="mobile-material-class">分类</label><select id="mobile-material-class" v-model="classificationId" @change="search"><option value="">全部分类</option><option v-for="option in options" :key="option.value" :value="option.value">{{option.label}}</option></select></div>
  <van-notice-bar v-if="error" :text="error" />
  <van-loading v-if="busy" style="padding:24px" />
  <template v-else><van-cell v-for="item in items" :key="item.id" :title="item.name" :label="`${item.code} · ${item.specification||'无规格'} · kg`" is-link @click="choose(item)" />
   <van-empty v-if="!items.length" description="暂无可选物料，请联系研发内勤维护" />
   <van-pagination v-if="total>20" v-model="page" :total-items="total" :items-per-page="20" @change="load" />
  </template>
 </van-popup>
</template>
<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue';
import {classificationOptions,materialLinkLabel,LatestMaterialQuery,type MaterialCatalogItem,type MaterialClassification,type MaterialIdentity} from '@rnd/shared';
import {client} from '../services/api';
const props=defineProps<{currentMaterial:MaterialIdentity;readonly?:boolean}>();const emit=defineEmits<{select:[item:MaterialCatalogItem]}>();
const open=ref(false),query=ref(''),classificationId=ref(''),page=ref(1),total=ref(0),busy=ref(false),error=ref(''),items=ref<MaterialCatalogItem[]>([]),categories=ref<MaterialClassification[]>([]),gate=new LatestMaterialQuery();
const options=computed(()=>classificationOptions(categories.value.filter(c=>c.businessType!=='PACKAGING')));
function show(){if(props.readonly)return;open.value=true;query.value='';classificationId.value='';page.value=1;void load();}
async function load(){busy.value=true;error.value='';const q=new URLSearchParams({query:query.value,classificationId:classificationId.value,page:String(page.value),size:'20',status:'ACTIVE',ingredientsOnly:'true'});try{await gate.run(async()=>({data:await client.get<{items:MaterialCatalogItem[];total:number}>(`/materials?${q}`),classes:await client.get<MaterialClassification[]>('/material-classifications')}),r=>{items.value=r.data.items;total.value=r.data.total;categories.value=r.classes;busy.value=false;});}catch(e){busy.value=false;error.value=e instanceof Error?e.message:'加载失败';}}
function search(){page.value=1;void load();}function choose(item:MaterialCatalogItem){if(props.readonly)return;emit('select',item);open.value=false;gate.invalidate();}
watch(()=>props.currentMaterial,()=>{open.value=false;gate.invalidate();});onBeforeUnmount(()=>gate.invalidate());
</script>
<style scoped>.classification{display:flex;align-items:center;gap:12px;padding:10px 16px}.classification select{flex:1;min-width:0;border:1px solid #ddd;border-radius:6px;padding:8px;background:#fff}</style>
