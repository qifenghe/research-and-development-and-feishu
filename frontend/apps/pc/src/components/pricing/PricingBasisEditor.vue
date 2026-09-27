<template>
 <a-card title="核价数据确认" class="basis-card">
  <a-alert v-if="error" type="error" :message="error" show-icon closable @close="error=''" />
  <a-spin :spinning="session.busy">
   <template v-if="session.draft && session.view">
    <div class="intro"><span>按原材料清单确认领料依据，不改变实际试验记录。</span><a-tag :color="session.dirty?'orange':'blue'">{{session.dirty?'有修改，待保存':session.view.confirmedBy?'已确认':'草稿'}}</a-tag></div>
    <a-alert v-if="session.view.stale" type="warning" message="来源工艺已变化，请重新取数并核对。当前草稿已保留。" show-icon />
    <a-alert v-if="!editable" type="info" message="当前仅可查看，确认操作由研发总监或产品负责人完成。" />
    <fieldset :disabled="!editable || session.busy">
     <section><h3>1. 文件信息</h3><div class="fields">
      <label v-for="field in headerFields" :key="field.key">{{field.label}}<a-input v-model:value="session.draft.header[field.key]" :disabled="disabled || field.key==='productName'" :placeholder="field.key==='effectiveDate'?'YYYY-MM-DD':'待填写'" /></label>
     </div><p class="muted">研发批准由后续审核产生，此处不填写批准人。</p></section>
     <section><h3>2. 原辅料清单</h3><p class="muted">逐行选择重量口径。利用率是处理损耗，不是工序得率；中间产物不重复领料。</p>
      <div v-for="(m,index) in session.draft.materials" :key="m.id" class="ingredient">
       <h4 v-if="index===0 || m.majorId!==session.draft.materials[index-1]?.majorId">{{m.stage || '未命名工序'}}</h4>
       <div class="material-title"><b>{{index+1}}. {{m.materialName}}</b><span>{{m.materialCode || '无编码'}} · {{m.role==='PRIMARY'?'主料':'辅料'}}</span></div>
       <div class="fields material-fields">
        <label>记录重量的口径<a-select :value="m.weightBasis" :disabled="disabled" :options="basisOptions" placeholder="请选择" @change="setBasis(index,$event)" /></label>
        <label>配方用量 kg<a-input-number v-model:value="m.usageKg" :disabled="disabled" :min="0" /></label>
        <label>参考利用率 %<a-input-number :value="m.weightBasis==='BEFORE_PROCESS'?rowResult(m.id)?.utilizationRate==null?null:rowResult(m.id)!.utilizationRate!*100:m.utilizationRate==null?null:m.utilizationRate*100" :disabled="disabled || m.weightBasis==='BEFORE_PROCESS'" :min="0" :max="100" @update:value="m.utilizationRate=utilizationFromPercent($event)" /></label>
        <label>参考领料 kg<strong>{{session.dirty?'保存后更新':display(rowResult(m.id)?.requisitionKg)}}</strong></label>
       </div>
       <details><summary>来源与确认依据</summary><p class="source-label">来源：{{sourceLabel(m)}}</p><p>原始投入：{{display(m.originalWeightKg)}} kg。{{m.weightBasis==='BEFORE_PROCESS'?'以原始投入作为领料量，不再除以利用率。':'以处理后用量除以利用率，得到参考领料量。'}}</p>
        <label>净用量确认依据<a-input v-model:value="m.confirmationBasis" :disabled="disabled" placeholder="填写处理后称重记录或研发确认依据；混合总重不能直接当单一原料净重" /></label>
        <label>核价备注<a-input v-model:value="m.remark" :disabled="disabled" placeholder="例如配料车间配制" /></label>
       </details>
      </div>
     </section>
     <section><h3>3. 出成与包装</h3><div class="fields">
      <label>主料出成 kg<a-input-number v-model:value="session.draft.mainOutputKg" :disabled="disabled" :min="0" /></label>
      <label>对应主料范围<a-select v-model:value="session.draft.mainMaterialIds" mode="multiple" :disabled="disabled" :options="session.draft.materials.filter(m=>m.role==='PRIMARY').map(m=>({value:m.id,label:`${m.stage} / ${m.materialName}`}))" /></label>
      <label class="wide">主料出成及范围确认依据<a-input v-model:value="session.draft.mainScopeBasis" :disabled="disabled" placeholder="确认分子为主料净产出，分母为以上选定主料的领料量" /></label>
      <label>可分装成品净重 kg<a-input-number v-model:value="session.draft.finishedNetKg" :disabled="disabled" :min="0" /></label>
      <label>单包净重 kg<a-input-number v-model:value="session.draft.unitWeightKg" :disabled="disabled" :min="0" /></label>
      <label>每箱包数<a-input-number v-model:value="session.draft.bagsPerBox" :disabled="disabled" :min="1" :precision="0" /></label>
      <label>实测包数（独立记录）<a-input-number v-model:value="session.draft.actualBags" :disabled="disabled" :min="0" :precision="0" /></label>
     </div>
     <div class="summary"><span>核价主料得率 <b>{{resultValue(session.view.calculation.mainYieldPercent)}}%</b></span><span>参考完整包数 <b>{{resultValue(session.view.calculation.referenceBags)}}</b></span><span>参考箱数 <b>{{resultValue(session.view.calculation.referenceBoxes)}}</b></span><span>不足整包余量 <b>{{resultValue(session.view.calculation.remainingKg)}} kg</b></span></div>
     <p class="muted">袋数向下取整，箱数向上取整。参考量不替代实测。修改后保存更新结果。</p>
     <a-alert v-if="packingBasisChanged" type="warning" message="包装基准已调整：请逐项核对数量，填写当前规格下的确认原因。旧建议不会自动换算。" />
     <div v-for="p in session.draft.packaging" :key="p.id" class="packing">
      <div class="material-title"><span>{{p.quantity===0?'本次不使用（不导出，保留原因）':p.conversionRule || '人工确认数量'}}</span><a-button v-if="editable && p.quantity!==0" danger size="small" @click="removePacking(session.draft,session.view.source,p.id)">{{session.view.source.packaging.some(s=>s.id===p.id)?'本次不使用':'删除新增行'}}</a-button></div>
      <div class="fields">
      <label>包装名称<a-input v-model:value="p.materialName" :disabled="disabled" /></label><label>NS编码<a-input v-model:value="p.materialCode" :disabled="disabled" /></label>
      <label>内部代码<a-input v-model:value="p.internalCode" :disabled="disabled" /></label><label>参考数量<a-input-number v-model:value="p.quantity" :disabled="disabled" :min="0" /></label>
      <label>数量单位<a-input v-model:value="p.quantityUnit" :disabled="disabled" placeholder="个、张、米" /></label><label>包装规格<a-input v-model:value="p.specification" :disabled="disabled" /></label>
      <label>备注<a-input v-model:value="p.remark" :disabled="disabled" /></label><label>新增、调整或不使用原因<a-input v-model:value="p.adjustmentReason" :disabled="disabled" placeholder="说明当前规格下的用量依据；数量为0表示本次不使用" /></label>
     </div></div><a-button v-if="editable" :disabled="session.busy" @click="addPacking">＋ 添加包装</a-button></section>
    </fieldset>
    <a-alert v-if="!session.dirty && session.view.calculation.issues.length" type="warning" show-icon message="以下数据仍需确认"><template #description><ul><li v-for="i in session.view.calculation.issues" :key="i.path+i.message">{{i.message}}</li></ul></template></a-alert>
    <div class="toolbar" v-if="editable"><a-button :disabled="session.busy" @click="resetSource">重新取数</a-button><a-button :loading="session.busy" @click="save">保存草稿</a-button><a-button :disabled="session.dirty || session.busy || session.view.stale" @click="preview">预览原材料清单</a-button><a-button type="primary" :disabled="session.dirty || session.busy || session.view.stale || !!session.view.calculation.issues.length" @click="generate">确认生成</a-button></div>
   </template>
  </a-spin>
 </a-card>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch} from "vue";
import {Modal,message} from "ant-design-vue";
import type {PricingBasisHeader,PricingBasisMaterial} from "@rnd/shared";
import {pricingBasisApi} from "../../services/pricingBasisApi";
import {PricingBasisSession,utilizationFromPercent,removePacking,pricingSourceLabel} from "./pricingBasisSession";
const props=defineProps<{pricingFileId:string;editable:boolean}>();const emit=defineEmits<{generated:[]}>();
const session=reactive(new PricingBasisSession(pricingBasisApi));const error=ref("");const disabled=computed(()=>!props.editable||session.busy);
const headerFields:{key:keyof PricingBasisHeader;label:string}[]=[{key:"companyName",label:"公司"},{key:"productName",label:"产品名称"},{key:"specification",label:"产品规格"},{key:"category",label:"类别"},{key:"ownerName",label:"负责人"},{key:"authorName",label:"编写人"},{key:"documentNumber",label:"文件编号"},{key:"version",label:"版本号"},{key:"effectiveDate",label:"实施日期"}];
const basisOptions=[{value:"AFTER_PROCESS",label:"处理后用量 → 换算领料"},{value:"BEFORE_PROCESS",label:"处理前投入 = 领料基准"}];
watch(()=>[props.pricingFileId,props.editable] as const,async([id,editable])=>{error.value="";try{await session.load(id,editable);}catch(e){error.value=e instanceof Error?e.message:"加载失败";}},{immediate:true});
const display=(v:number|null|undefined)=>v==null?"待填写":Number(v.toFixed(4)).toString();
const rowResult=(id:string)=>session.view?.calculation.materials.find(m=>m.id===id);
const packingBasisChanged=computed(()=>{const d=session.draft,s=session.view?.source;return !!d&&!!s&&(d.finishedNetKg!==s.finishedNetKg||d.unitWeightKg!==s.unitWeightKg||d.bagsPerBox!==s.bagsPerBox);});
function sourceLabel(m:PricingBasisMaterial){const d=session.draft!,s=session.view!.source;const bound=s.sourceRevisionId===d.sourceRevisionId?s.materials.find(x=>x.id===m.id)??m:m;return pricingSourceLabel(d.sourceRevisionId,bound);}
watch(()=>[session.draft?.finishedNetKg,session.draft?.unitWeightKg,session.draft?.bagsPerBox],(now,old)=>{if(old.every(v=>v!==undefined)&&now.some((v,i)=>v!==old[i]))for(const p of session.draft?.packaging??[])p.adjustmentReason=null;});
const resultValue=(v:number|null)=>session.dirty?"待保存":display(v);
function setBasis(index:number,value:"AFTER_PROCESS"|"BEFORE_PROCESS"){const m=session.draft!.materials[index]!;m.weightBasis=value;m.utilizationRate=null;m.confirmationBasis=null;m.processedOutputId=null;}
async function run(fn:()=>Promise<unknown>){error.value="";try{await fn();}catch(e){error.value=e instanceof Error?e.message:"操作失败";}}
async function save(){await run(async()=>{await session.save();message.success("核价草稿已保存");});}
async function preview(){await run(async()=>{const blob=await session.preview();const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download="原材料清单-预览.xlsx";a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});}
function generate(){Modal.confirm({title:"确认生成原材料清单？",content:"将使用当前已保存的核价数据生成新归档，原始试验记录不会改变。",onOk:()=>run(async()=>{await session.generate();message.success("已生成，等待核价审核");emit("generated");})});}
function resetSource(){Modal.confirm({title:"重新取数？",content:"当前核价输入将被来源数据替换，仍需保存并重新确认。",onOk:()=>run(()=>session.resetSource())});}
function addPacking(){session.draft?.packaging.push({id:`manual-${Date.now()}`,materialCode:null,materialName:"",internalCode:null,quantity:null,quantityUnit:null,specification:null,conversionRule:null,remark:null,adjustmentReason:null});}
</script>
<style scoped>
.basis-card{margin-bottom:16px}.intro,.toolbar,.material-title{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.intro{margin-bottom:20px;color:#667085}fieldset{border:0;padding:0;margin:0}section{padding:22px 0;border-bottom:1px solid #eef0f4}h3{font-size:16px;margin:0 0 16px}h4{color:#2468f2;margin:0 0 14px}.fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.fields label,details label{display:grid;gap:6px;font-size:13px;color:#596579}.fields :deep(.ant-input-number){width:100%}.wide{grid-column:1/-1}.ingredient,.packing{padding:18px;background:#fafbfd;border:1px solid #e7ebf1;border-radius:8px;margin:12px 0}.material-title{margin-bottom:14px}.material-title span,.muted{font-size:12px;color:#86909c}.summary{display:flex;flex-wrap:wrap;gap:22px;padding:18px;background:#eef5ff;margin-top:18px}.summary b{display:block;font-size:19px;color:#2468f2}details{margin-top:16px}details p{font-size:12px;color:#667085}details label{margin-top:12px}summary{cursor:pointer;color:#667085}.toolbar{justify-content:flex-end;padding-top:20px;position:sticky;bottom:0;background:white;padding-bottom:10px}.ant-alert{margin-bottom:12px}@media(max-width:760px){.fields{grid-template-columns:1fr}.wide{grid-column:auto}}
</style>
