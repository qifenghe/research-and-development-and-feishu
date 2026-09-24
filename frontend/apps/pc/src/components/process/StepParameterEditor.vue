<template>
  <div class="parameter-editor">
    <div v-if="!readonly" class="parameter-toolbar">
      <a-button size="small" @click="addParameter()">＋ 新增参数</a-button>
      <a-select v-model:value="operation" size="small" placeholder="选择操作字段" :options="parameterOperations.map(value=>({value,label:value}))" />
      <a-select :value="null" size="small" placeholder="选择常用字段追加" :options="available.map(p=>({value:p.name,label:p.name}))" @change="appendTemplate" />
    </div>
    <p class="note">实际记录与试验计划分开；常用字段不预填工艺标准。勾选的前三项显示在摘要中。</p>
    <div v-for="(parameter,index) in modelValue" :key="parameter.key" class="parameter-card">
      <div class="parameter-line">
        <a-input :value="parameter.name" :disabled="readonly" placeholder="参数名称" aria-label="参数名称" @update:value="updateParameter(parameter.key,{name:$event})" />
        <a-input :value="parameter.actualValue" :disabled="readonly" placeholder="实际记录值" aria-label="实际记录值" @update:value="updateParameter(parameter.key,{actualValue:$event})" />
        <a-auto-complete :value="parameter.unit" :disabled="readonly || parameter.valueType==='TEXT'" :options="units" placeholder="单位/自填" @update:value="updateParameter(parameter.key,{unit:String($event)})" />
        <a-button type="link" size="small" @click="opened=opened===parameter.key?'':parameter.key">{{ opened===parameter.key?'收起':'详情' }}</a-button>
      </div>
      <div v-if="opened===parameter.key" class="parameter-detail">
        <label>数据类型<a-select :value="parameter.valueType" :disabled="readonly" :options="[{value:'NUMBER',label:'数值'},{value:'TEXT',label:'文字'}]" @update:value="updateParameter(parameter.key,{valueType:$event,lowerLimit:undefined,upperLimit:undefined})" /></label>
        <label v-if="parameter.valueType==='NUMBER'">可选下限<a-input :value="parameter.lowerLimit" :disabled="readonly" @update:value="updateParameter(parameter.key,{lowerLimit:$event})" /></label>
        <label v-if="parameter.valueType==='NUMBER'">可选上限<a-input :value="parameter.upperLimit" :disabled="readonly" @update:value="updateParameter(parameter.key,{upperLimit:$event})" /></label>
        <label>压力测量口径<a-select :value="parameter.measurementBasis" :disabled="readonly" allow-clear placeholder="非压力项可留空" :options="bases" @update:value="updateParameter(parameter.key,{measurementBasis:$event})" /></label>
        <label class="full">备注/设备百分比定义<a-input :value="parameter.remark" :disabled="readonly" placeholder="说明设备、显示百分比的含义或测量依据" @update:value="updateParameter(parameter.key,{remark:$event})" /></label>
        <p v-if="/压力|真空/.test(parameter.name) && !parameter.measurementBasis" class="warning full">测量口径待确认：仅保存记录，不自动比较或换算。</p>
        <div class="full actions">
          <a-checkbox :checked="parameter.showInSummary" :disabled="readonly" @update:checked="updateParameter(parameter.key,{showInSummary:$event})">显示在关键摘要</a-checkbox>
          <template v-if="!readonly"><a-button size="small" :disabled="index===0" @click="moveParameter(parameter.key,-1)">上移</a-button><a-button size="small" :disabled="index===modelValue.length-1" @click="moveParameter(parameter.key,1)">下移</a-button><a-button size="small" danger @click="removeParameter(parameter.key)">删除参数</a-button></template>
        </div>
      </div>
    </div>
    <p v-if="!modelValue.length" class="note">暂无参数，可新增自定义项或选择常用字段。</p>
  </div>
</template>
<script setup lang="ts">
import {computed,ref} from "vue";
import {Modal} from "ant-design-vue";
import type {StepParameterDraft} from "@rnd/shared";
import {parameterTemplates,parameterOperations,parameterKey} from "./stepParameterTemplates";
const props=defineProps<{modelValue:StepParameterDraft[];readonly?:boolean;operation?:string}>();
const emit=defineEmits<{"update:modelValue":[value:StepParameterDraft[]]}>();
const operation=ref(props.operation || "滚揉"), opened=ref("");
const available=computed(()=>parameterTemplates(operation.value));
const units=["℃","min","s","mm","rpm","MPa","kPa","bar","%","kg","g","次","m/s","无量纲"].map(value=>({value}));
const bases=[{value:"GAUGE",label:"表压"},{value:"ABSOLUTE",label:"绝对压力"},{value:"DEVICE_PERCENT",label:"设备显示百分比"}];
function publish(value:StepParameterDraft[]) { if(!props.readonly) emit("update:modelValue",value); }
function addParameter(name="") { if(props.readonly)return;const p:StepParameterDraft={key:parameterKey(),name,valueType:"NUMBER",showInSummary:false};publish([...props.modelValue,p]);opened.value=p.key; }
function appendTemplate(name:string) { if(props.readonly)return;const p=parameterTemplates(operation.value).find(p=>p.name===name);if(p){publish([...props.modelValue,p]);opened.value=p.key;} }
function updateParameter(key:string,patch:Partial<StepParameterDraft>) { publish(props.modelValue.map(p=>p.key===key?{...p,...patch,key}:p)); }
function moveParameter(key:string,offset:number) { if(props.readonly)return;const values=[...props.modelValue],index=values.findIndex(p=>p.key===key);if(index<0||index+offset<0||index+offset>=values.length)return;const [p]=values.splice(index,1);values.splice(index+offset,0,p!);publish(values); }
async function removeParameter(key:string,confirm?:()=>Promise<boolean>) {
  if(props.readonly)return;
  const accepted=await (confirm?confirm():new Promise<boolean>(resolve=>Modal.confirm({title:"删除此参数？",content:"实际记录及关联计划值会从当前草稿移除，已提交历史不变。",onOk:()=>resolve(true),onCancel:()=>resolve(false)})));
  if(accepted&&!props.readonly)publish(props.modelValue.filter(p=>p.key!==key));
}
</script>
<style scoped>
.parameter-toolbar,.actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center}.parameter-toolbar :deep(.ant-select){min-width:140px}.note{font-size:12px;color:#86909c;margin:10px 0}.parameter-card{border:1px solid #edf0f5;border-radius:8px;margin-top:8px;padding:10px}.parameter-line{display:grid;grid-template-columns:minmax(90px,1.2fr) minmax(90px,1fr) 90px auto;gap:8px;align-items:center}.parameter-detail{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:12px;padding-top:12px;border-top:1px solid #f0f0f0}.parameter-detail label{display:grid;gap:5px;font-size:12px;color:#667085}.full{grid-column:1/-1}.warning{color:#ad6800;font-size:12px;margin:0}.parameter-line>*{min-width:0}@media(max-width:900px){.parameter-line{grid-template-columns:1fr 1fr}.parameter-detail{grid-template-columns:1fr 1fr}}
</style>
