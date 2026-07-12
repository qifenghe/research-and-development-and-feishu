<template>
  <div class="page-with-footer experiment-page">
    <van-skeleton title :row="8" :loading="loading">
      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="loadDetail">重试</van-button>
      </van-empty>

      <template v-else>
        <PageHeader :title="headerTitle" compact>
          <StatusBadge label="打样中" variant="success" />
          <StatusBadge v-if="draftSaved" label="草稿已保存" variant="primary" style="margin-left:8px" />
        </PageHeader>

        <section v-if="phase === 'mode'" class="mode-section">
          <h1>选择本次打样方式</h1>
          <p>工序固定时直接开始；需要调整工艺时先完成编排。</p>
          <button class="mode-card mode-card--recommended" @click="startQuickMode">
            <span class="mode-card__tag">推荐 · 操作最少</span>
            <strong>快速打样</strong>
            <small>使用系统推荐工序，直接逐步填写投入和产出数据。</small>
          </button>
          <button class="mode-card" @click="startArrangeMode">
            <span class="mode-card__tag">灵活 · 先确认工艺</span>
            <strong>编排后打样</strong>
            <small>先调整工序顺序、名称和控制要点，再开始现场记录。</small>
          </button>
        </section>

        <section v-else-if="phase === 'arrange'" class="content-section">
          <div class="section-title">
            <div><h2>编排本次工序</h2><p>调整只影响当前样品版本。</p></div>
          </div>
          <ProcessStepEditor v-model="processSteps" mode="arrange" />
        </section>

        <section v-else class="content-section">
          <van-notice-bar
            v-if="readOnly"
            color="#246BFE"
            background="#EAF2FF"
            left-icon="info-o"
            text="当前版本已提交，仅供查看。"
          />
          <section class="form-section">
            <h2>基础信息</h2>
            <van-field :model-value="detail?.task.productName" label="产品名称" readonly />
            <van-field :model-value="detail?.version.specification || detail?.project?.specification" label="产品规格" readonly />
            <van-field v-model="form.summary" rows="2" autosize type="textarea" label="实验摘要" placeholder="选填" :readonly="readOnly" />
          </section>
          <section class="form-section">
            <div class="section-title"><div><h2>配方投入</h2><p>一个主料，可添加多个配料；利用率默认 100%。</p></div></div>
            <div class="formula-summary"><span>总投入 {{ totalFormulaWeight.toFixed(3) }}kg</span><span>主料 {{ primaryMaterialName }}</span></div>
            <div v-for="(material,index) in materials" :key="index" class="material-row">
              <van-field label="类型"><template #input><van-tag :type="material.primaryMaterial ? 'primary' : 'success'">{{ material.primaryMaterial ? "主料" : "配料" }}</van-tag><van-button v-if="!readOnly && !material.primaryMaterial" size="mini" plain type="primary" @click="setPrimaryMaterial(index)">设为主料</van-button></template></van-field>
              <van-field v-model="material.materialName" label="物料" placeholder="物料名称" :readonly="readOnly" />
              <van-field v-model="material.materialCode" label="ERP编码" placeholder="可选" :readonly="readOnly" />
              <van-field v-model="material.weightKg" label="重量" type="number" :readonly="readOnly"><template #button>kg</template></van-field>
              <van-field label="配方比例"><template #input>{{ formulaRatioAt(index).toFixed(2) }}%</template></van-field>
              <van-field v-model="material.utilizationRate" label="利用率" type="number" :readonly="readOnly"><template #button>%</template></van-field>
              <van-button v-if="!readOnly && !material.primaryMaterial" size="mini" plain type="danger" @click="removeMaterial(index)">删除配料</van-button>
            </div>
            <van-button v-if="!readOnly" block plain type="primary" size="small" @click="addMaterial">+ 添加配料</van-button>
          </section>
          <section class="form-section">
            <div v-if="!readOnly" class="execution-heading"><div><h2>关键工序</h2><span>{{ samplingModeLabel }} · 一次只填写一个工序</span></div><van-button size="small" plain @click="startArrangeMode">调整工序</van-button></div>
            <h2 v-else>关键工序</h2>
            <ProcessStepEditor v-model="processSteps" :mode="readOnly ? 'readonly' : 'execute'" :readonly="readOnly" :current-index="currentStepIndex" @previous="previousStep" @next="nextStep" />
          </section>
          <section class="form-section">
            <h2>成品产出</h2>
            <van-field v-model="form.finishedOutputWeightKg" label="成品重量" type="number" placeholder="请输入" :readonly="readOnly"><template #button>kg</template></van-field>
            <van-field v-model="form.finishedOutputQuantity" label="成品数量" type="digit" inputmode="numeric" placeholder="请输入正整数" :readonly="readOnly" @blur="validateFinishedOutputQuantity" />
            <van-field label="成品单位"><template #input><van-radio-group v-model="form.finishedOutputUnit" direction="horizontal" :disabled="readOnly"><van-radio name="袋">袋</van-radio><van-radio name="盒">盒</van-radio><van-radio name="份">份</van-radio><van-radio name="个">个</van-radio><van-radio name="盘">盘</van-radio></van-radio-group></template></van-field>
          </section>
          <section class="form-section pricing-preview">
            <h2>核价数据预览</h2>
            <div v-for="(material,index) in materials" :key="`preview-${index}`" class="pricing-preview__material"><span>{{ material.primaryMaterial ? "主料" : "配料" }} {{ material.materialName || "未命名" }}</span><strong>{{ Number(material.weightKg || 0).toFixed(3) }}kg · {{ formulaRatioAt(index).toFixed(2) }}%</strong></div>
            <div class="pricing-preview__grid"><span>总投入<strong>{{ pricingPreview.totalInputWeightKg.toFixed(3) }}kg</strong></span><span>成品重量<strong>{{ Number(form.finishedOutputWeightKg || 0).toFixed(3) }}kg</strong></span><span>成品数量<strong>{{ pricingPreview.referenceQuantity }}{{ form.finishedOutputUnit }}</strong></span><span>平均每{{ form.finishedOutputUnit }}重量<strong>{{ pricingPreview.averageUnitWeightKg.toFixed(3) }}kg</strong></span><span>主料得率<strong>{{ pricingPreview.primaryMaterialYieldPercent.toFixed(2) }}%</strong></span></div>
          </section>
          <section class="form-section"><h2>照片附件</h2><van-uploader v-if="!readOnly" v-model="fileList" :after-read="afterRead" /></section>
        </section>

        <FixedActionBar v-if="phase !== 'mode' && !loading" :with-tabbar="false">
          <van-button v-if="phase === 'arrange'" block plain @click="phase='mode'">返回</van-button>
          <van-button v-if="phase === 'arrange'" block type="primary" @click="confirmArrangement">确认并开始打样</van-button>
          <van-button v-if="phase !== 'arrange' && canSaveDraft" block plain type="primary" :loading="saving" @click="saveDraft">保存草稿</van-button>
          <van-button v-if="phase !== 'arrange' && canNotify" block type="primary" :loading="submitting" @click="notifyTest">通知测试</van-button>
        </FixedActionBar>
      </template>
    </van-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { ExperimentMaterial, MaterialCategory, RndTaskDetailView } from "@rnd/shared";
import { calculatePricingPreview, canEditExperiment, canNotifyInternalTest, formulaRatios, normalizePositiveIntegerQuantity } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import ProcessStepEditor from "../components/ProcessStepEditor.vue";
import { blankProcessStep, fromProcessSteps, toProcessSteps, type EditableProcessStep } from "../components/ProcessStepEditor.helpers";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

type Phase = "mode" | "arrange" | "execute" | "readonly";
type EditableMaterial = {
  materialCategory: MaterialCategory;
  primaryMaterial: boolean;
  materialCode: string;
  materialName: string;
  weightKg: string;
  utilizationRate: string;
  remark: string;
};
type FinishedOutputUnit = "袋" | "盒" | "份" | "个" | "盘";

const route=useRoute();
const router=useRouter();
const auth=useAuthStore();
const detail=ref<RndTaskDetailView|null>(null);
const loading=ref(true);
const loadError=ref("");
const saving=ref(false);
const submitting=ref(false);
const draftSaved=ref(false);
const phase=ref<Phase>("mode");
const samplingMode=ref<"quick"|"arrange">("quick");
const currentStepIndex=ref(0);
const fileList=ref<Array<{url?:string;file?:File}>>([]);
const headerTitle=ref("实验单录入");

const blankMaterial=(primaryMaterial=false):EditableMaterial=>({
  materialCategory:primaryMaterial?"RAW":"AUXILIARY",
  primaryMaterial,
  materialCode:"",
  materialName:"",
  weightKg:"",
  utilizationRate:"100",
  remark:"",
});
const materials=ref<EditableMaterial[]>([blankMaterial(true)]);
const processSteps=ref<EditableProcessStep[]>([blankProcessStep()]);
const form=reactive<{summary:string;finishedOutputWeightKg:string;finishedOutputQuantity:string;finishedOutputUnit:FinishedOutputUnit}>({summary:"",finishedOutputWeightKg:"",finishedOutputQuantity:"",finishedOutputUnit:"袋"});

const canSaveDraft=computed(()=>detail.value?canEditExperiment(detail.value,auth.displayName,auth.role):false);
const canNotify=computed(()=>detail.value?canNotifyInternalTest(detail.value,auth.displayName,auth.role):false);
const readOnly=computed(()=>!canSaveDraft.value);
const samplingModeLabel=computed(()=>samplingMode.value==="quick"?"快速打样":"编排后打样");
const formulaWeights=computed(()=>materials.value.map((item)=>Number(item.weightKg||0)));
const formulaRatiosValue=computed(()=>formulaRatios(formulaWeights.value));
const totalFormulaWeight=computed(()=>formulaWeights.value.reduce((sum,weight)=>sum+weight,0));
const primaryMaterial=computed(()=>materials.value.find((item)=>item.primaryMaterial));
const primaryMaterialName=computed(()=>primaryMaterial.value?.materialName?.trim()||"未选择");
const primaryInputWeight=computed(()=>Number(primaryMaterial.value?.weightKg||0));
const ingredientWeight=computed(()=>materials.value.filter((item)=>!item.primaryMaterial).reduce((sum,item)=>sum+Number(item.weightKg||0),0));
const pricingPreview=computed(()=>calculatePricingPreview({primaryMaterialWeightKg:primaryInputWeight.value,ingredientWeightKg:ingredientWeight.value,finishedOutputWeightKg:Number(form.finishedOutputWeightKg||0),finishedOutputQuantity:Number(form.finishedOutputQuantity||0)}));

async function ensureAuthReady(){if(!auth.principal)await auth.fetchMe().catch(()=>undefined)}

function applyDetail(data:RndTaskDetailView){
  detail.value=data;
  headerTitle.value=`${data.task.productName} ${data.task.versionCode}`;
  const existing=data.currentExperimentForm;
  if(existing){
    form.summary=existing.summary||"";
    form.finishedOutputWeightKg=existing.finishedOutputWeightKg!=null?String(existing.finishedOutputWeightKg):"";
    form.finishedOutputQuantity=existing.finishedOutputQuantity!=null?String(existing.finishedOutputQuantity):"";
    form.finishedOutputUnit=(existing.finishedOutputUnit as FinishedOutputUnit)||"袋";
    draftSaved.value=true;
    phase.value=readOnly.value?"readonly":"execute";
  }else if(readOnly.value){phase.value="readonly"}
  if(existing?.materials?.length){
    const savedMaterials=existing.materials.filter(item=>(item.materialCategory??categoryFromStage(item.stage))!=="PACKAGING").map(item=>({
    materialCategory:item.materialCategory??categoryFromStage(item.stage),
    primaryMaterial:item.primaryMaterial??false,
    materialCode:item.materialCode||"",
    materialName:item.materialName,
    weightKg:String(item.weightKg??""),
    utilizationRate:item.utilizationRate!=null?String(item.utilizationRate*100):"100",
    remark:item.remark||"",
    }));
    const primaryIndex=savedMaterials.findIndex(item=>item.primaryMaterial);
    materials.value=savedMaterials.length?savedMaterials.map((item,index)=>({...item,materialCategory:index===(primaryIndex>=0?primaryIndex:0)?"RAW":"AUXILIARY",primaryMaterial:index===(primaryIndex>=0?primaryIndex:0)})):[blankMaterial(true)];
  }
  if(existing?.processSteps?.length)processSteps.value=fromProcessSteps(existing.processSteps);
}

async function loadProcessTemplate(versionId:string){
  try{const steps=await api.sample.processSteps(versionId);if(steps.length)processSteps.value=fromProcessSteps(steps)}catch{ /* 不阻断打样 */ }
}

async function loadDetail(){
  loading.value=true;loadError.value="";
  try{await ensureAuthReady();const data=await api.task.detail(String(route.params.id),auth.role,auth.displayName);applyDetail(data);if(!data.currentExperimentForm?.processSteps?.length&&!readOnly.value)await loadProcessTemplate(data.task.versionId)}
  catch(error){loadError.value=error instanceof Error?error.message:"无法打开实验单"}
  finally{loading.value=false}
}

onMounted(loadDetail);

function startQuickMode(){samplingMode.value="quick";phase.value="execute";currentStepIndex.value=0}
function startArrangeMode(){samplingMode.value="arrange";phase.value="arrange"}
function confirmArrangement(){
  if(!processSteps.value.some(step=>step.processName.trim())){showFailToast("请至少填写一个工序");return}
  processSteps.value=processSteps.value.filter(step=>step.processName.trim());
  currentStepIndex.value=0;phase.value="execute";
}
function previousStep(){currentStepIndex.value=Math.max(0,currentStepIndex.value-1)}
function nextStep(){currentStepIndex.value=Math.min(processSteps.value.length-1,currentStepIndex.value+1)}
function addMaterial(){materials.value.push(blankMaterial())}
function removeMaterial(index:number){if(!materials.value[index]||materials.value[index].primaryMaterial)return;materials.value.splice(index,1)}
function setPrimaryMaterial(index:number){
  if(readOnly.value)return;
  const target=materials.value[index];
  if(!target)return;
  materials.value=materials.value.map((item,itemIndex)=>({...item,materialCategory:itemIndex===index?"RAW":"AUXILIARY",primaryMaterial:itemIndex===index}));
}

function categoryFromStage(stage?:string):MaterialCategory{
  if(stage==="辅料"||stage==="AUXILIARY")return "AUXILIARY";
  if(stage==="包材"||stage==="PACKAGING")return "PACKAGING";
  return "RAW";
}

function categoryLabel(category:MaterialCategory){
  if(category==="AUXILIARY")return "辅料";
  return "原料";
}

function formulaRatioAt(index:number){
  return formulaRatiosValue.value[index]??0;
}

function buildMaterials():ExperimentMaterial[]{return materials.value.filter(item=>item.materialName.trim()).map((item,index)=>({stage:categoryLabel(item.materialCategory),sequence:index+1,materialCode:item.materialCode.trim()||undefined,materialName:item.materialName.trim(),weightKg:Number(item.weightKg||0),utilizationRate:Number(item.utilizationRate||100)/100,materialCategory:item.primaryMaterial?"RAW":"AUXILIARY",primaryMaterial:item.primaryMaterial,inputUnit:"kg",remark:item.remark.trim()||undefined}))}

function validateFinishedOutputQuantity(){
  const quantity=normalizePositiveIntegerQuantity(form.finishedOutputQuantity);
  if(form.finishedOutputQuantity!==""&&quantity===undefined){showFailToast("成品数量必须为正整数");return undefined}
  return quantity;
}

async function saveDraft(){
  const finishedOutputQuantity=validateFinishedOutputQuantity();
  if(form.finishedOutputQuantity!==""&&finishedOutputQuantity===undefined)return;
  saving.value=true;
  try{detail.value={...detail.value!,currentExperimentForm:await api.task.saveExperimentDraft(String(route.params.id),{operatorName:auth.displayName,summary:form.summary,materials:buildMaterials(),processSteps:toProcessSteps(processSteps.value),finishedOutputWeightKg:Number(form.finishedOutputWeightKg||0)||undefined,finishedOutputQuantity,finishedOutputUnit:form.finishedOutputUnit})};draftSaved.value=true;showSuccessToast("草稿已保存")}
  catch(error){showFailToast(error instanceof Error?error.message:"保存失败")}
  finally{saving.value=false}
}

async function afterRead(item:{file?:File}|Array<{file?:File}>){
  const entry=Array.isArray(item)?item[0]:item;
  if(!detail.value?.currentExperimentForm?.id)await saveDraft();
  const experimentId=detail.value?.currentExperimentForm?.id;
  if(!experimentId||!entry?.file)return;
  try{await api.task.uploadAttachment(experimentId,entry.file,{uploadedBy:auth.displayName,category:"PHOTO"});showSuccessToast("照片已上传")}
  catch(error){showFailToast(error instanceof Error?error.message:"上传失败")}
}

async function notifyTest(){
  if(primaryInputWeight.value<=0){showFailToast("通知测试前请填写主料重量");return}
  if(!processSteps.value.some(step=>step.processName.trim()&&Number(step.beforeWeightKg)>0)){showFailToast("通知测试前请至少完成一道有效工序");return}
  if(Number(form.finishedOutputWeightKg)<=0){showFailToast("通知测试前请填写成品重量");return}
  if(form.finishedOutputQuantity===""){showFailToast("通知测试前请填写成品数量");return}
  if(validateFinishedOutputQuantity()===undefined)return;
  if(!detail.value?.currentExperimentForm?.id&&canSaveDraft.value)await saveDraft();
  const experimentId=detail.value?.currentExperimentForm?.id;
  if(!experimentId){showFailToast("请先保存草稿");return}
  submitting.value=true;
  try{await api.task.submitExperimentForTest(experimentId,auth.displayName);showSuccessToast("已通知内部测试");router.push("/todo")}
  catch(error){showFailToast(error instanceof Error?error.message:"提交失败")}
  finally{submitting.value=false}
}
</script>

<style scoped>
.experiment-page{background:#f6f8fb}.mode-section,.content-section{padding:18px 14px}.mode-section h1{margin:8px 0;font-size:24px}.mode-section>p{margin:0 0 20px;color:#64748b}.mode-card{display:block;width:100%;margin-bottom:12px;padding:20px;text-align:left;background:#fff;border:1px solid #e2e8f0;border-radius:8px}.mode-card--recommended{border:2px solid #246bfe;background:#f7faff}.mode-card__tag{display:block;margin-bottom:12px;color:#246bfe;font-size:12px;font-weight:700}.mode-card strong{display:block;margin-bottom:7px;font-size:20px}.mode-card small{display:block;color:#64748b;line-height:1.6}.form-section{margin-top:14px;padding:14px 0;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.form-section>h2,.form-section>.section-title,.form-section>.execution-heading{padding:0 14px}.form-section h2{margin:0 0 10px;font-size:17px}.section-title{display:flex;justify-content:space-between;margin-bottom:14px}.section-title h2{margin:0 0 4px}.section-title p{margin:0;color:#64748b;font-size:12px}.execution-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.execution-heading h2{margin-bottom:3px}.execution-heading span{display:block;color:#64748b;font-size:12px}.formula-summary{display:flex;justify-content:space-between;padding:0 16px 10px;color:#64748b;font-size:12px}.material-row{margin:0 8px 10px;padding:8px;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.material-row .van-button{margin:6px 16px}.pricing-preview__material{display:flex;justify-content:space-between;gap:12px;padding:9px 16px;border-bottom:1px solid #edf0f5;color:#475569;font-size:13px}.pricing-preview__material strong{color:#172033;font-weight:600;text-align:right}.pricing-preview__grid{display:grid;grid-template-columns:1fr 1fr;gap:1px;background:#e7ebf2}.pricing-preview__grid span{display:flex;flex-direction:column;gap:5px;padding:12px 16px;background:#fff;color:#64748b;font-size:12px}.pricing-preview__grid strong{color:#172033;font-size:14px}
</style>
