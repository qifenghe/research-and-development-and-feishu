<template>
  <div class="page-with-footer experiment-page">
    <van-skeleton title :row="8" :loading="loading">
      <van-empty v-if="loadError" image="error" :description="loadError">
        <van-button round type="primary" @click="loadDetail">重试</van-button>
      </van-empty>

      <template v-else>
        <PageHeader :title="headerTitle" compact>
          <StatusBadge label="打样中" variant="success" />
          <StatusBadge v-if="draftStatusLabel" :label="draftStatusLabel" variant="primary" style="margin-left:8px" />
        </PageHeader>

        <section v-if="phase === 'arrange'" class="content-section">
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
            <div class="section-title"><div><h2>配方投入</h2><p>利用率默认 100%，主料可多选，也可按全部物料计算。</p></div></div>
            <van-field label="得率计算"><template #input><van-radio-group v-model="yieldCalculationMode"><van-radio name="SELECTED_PRIMARY_MATERIALS">按所选主料</van-radio><van-radio name="TOTAL_PICKING_WEIGHT" style="margin-top:8px">按全部非包材物料</van-radio></van-radio-group></template></van-field>
            <div class="formula-summary"><span>总投入 {{ totalFormulaWeight.toFixed(3) }}kg</span><span>得率基准 {{ yieldBasisWeight.toFixed(3) }}kg</span></div>
            <div v-for="(material,index) in materials" :key="index" class="material-row">
              <van-field label="类别"><template #input><van-radio-group v-model="material.materialCategory" direction="horizontal" :disabled="readOnly"><van-radio name="RAW">原料</van-radio><van-radio name="AUXILIARY">辅料</van-radio><van-radio name="PACKAGING">包材</van-radio></van-radio-group></template></van-field>
              <van-field v-if="yieldCalculationMode === 'SELECTED_PRIMARY_MATERIALS' && material.materialCategory === 'RAW'" label="计入主料"><template #input><van-switch v-model="material.primaryMaterial" :disabled="readOnly" size="20" /></template></van-field>
              <van-field v-model="material.materialName" label="物料" placeholder="物料名称" :readonly="readOnly" />
              <van-field v-model="material.materialCode" label="ERP编码" placeholder="可选" :readonly="readOnly" />
              <van-field v-model="material.weightKg" label="重量" type="number" :readonly="readOnly"><template #button>kg</template></van-field>
              <van-field label="配方比例"><template #input>{{ formulaRatioAt(index).toFixed(2) }}%</template></van-field>
              <van-field v-model="material.utilizationRate" label="利用率" type="number" :readonly="readOnly"><template #button>%</template></van-field>
              <van-button v-if="!readOnly && materials.length > 1" size="mini" plain type="danger" @click="removeMaterial(index)">删除物料</van-button>
            </div>
            <van-button v-if="!readOnly" block plain type="primary" size="small" @click="addMaterial">+ 添加物料</van-button>
          </section>
          <section class="form-section">
            <div v-if="!readOnly" class="execution-heading"><div><h2>关键工序</h2><span>一次只填写一个工序</span></div><van-button size="small" plain @click="startArrangeMode">调整工序</van-button></div>
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
            <div v-for="(material,index) in materials" :key="`preview-${index}`" class="pricing-preview__material"><span>{{ categoryLabel(material.materialCategory) }}{{ material.primaryMaterial ? " · 主料" : "" }} {{ material.materialName || "未命名" }}</span><strong>{{ Number(material.weightKg || 0).toFixed(3) }}kg · {{ formulaRatioAt(index).toFixed(2) }}%</strong></div>
            <div class="pricing-preview__grid"><span>总投入<strong>{{ pricingPreview.totalInputWeightKg.toFixed(3) }}kg</strong></span><span>得率基准<strong>{{ pricingPreview.yieldBasisWeightKg.toFixed(3) }}kg</strong></span><span>成品重量<strong>{{ Number(form.finishedOutputWeightKg || 0).toFixed(3) }}kg</strong></span><span>成品数量<strong>{{ pricingPreview.referenceQuantity }}{{ form.finishedOutputUnit }}</strong></span><span>平均每{{ form.finishedOutputUnit }}重量<strong>{{ pricingPreview.averageUnitWeightKg.toFixed(3) }}kg</strong></span><span>研发参考得率<strong>{{ pricingPreview.yieldPercent.toFixed(2) }}%</strong></span></div>
          </section>
          <section class="form-section"><h2>照片附件</h2><van-uploader v-if="!readOnly" v-model="fileList" :after-read="afterRead" /></section>
        </section>

        <FixedActionBar v-if="!loading" :with-tabbar="false">
          <van-button v-if="phase === 'arrange'" block type="primary" @click="confirmArrangement">确认并开始打样</van-button>
          <van-button v-if="phase !== 'arrange' && canSaveDraft" block plain type="primary" :loading="saving" @click="saveDraft">保存草稿</van-button>
          <van-button v-if="phase !== 'arrange' && canNotify" block type="primary" :loading="submitting" @click="notifyTest">通知测试</van-button>
        </FixedActionBar>
      </template>
    </van-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showFailToast, showSuccessToast } from "vant";
import type { ExperimentMaterial, MaterialCategory, RndTaskDetailView, YieldCalculationMode } from "@rnd/shared";
import { calculatePricingPreview, canEditExperiment, canNotifyInternalTest, clearExperimentDraft, experimentDraftKey, formulaRatios, isCachedDraftNewer, normalizePositiveIntegerQuantity, readExperimentDraft, writeExperimentDraft, yieldBasisWeightKg } from "@rnd/shared";
import PageHeader from "../components/PageHeader.vue";
import StatusBadge from "../components/StatusBadge.vue";
import FixedActionBar from "../components/FixedActionBar.vue";
import ProcessStepEditor from "../components/ProcessStepEditor.vue";
import { blankProcessStep, fromProcessSteps, toProcessSteps, type EditableProcessStep } from "../components/ProcessStepEditor.helpers";
import { useAuthStore } from "../stores/auth";
import { api } from "../services/api";

type Phase = "arrange" | "execute" | "readonly";
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
const draftSyncState=ref<"idle"|"local"|"syncing"|"saved"|"error">("idle");
const phase=ref<Phase>("arrange");
const yieldCalculationMode=ref<YieldCalculationMode>("SELECTED_PRIMARY_MATERIALS");
const hydrated=ref(false);
let autoSaveTimer:number|undefined;
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
const materials=ref<EditableMaterial[]>([blankMaterial(false)]);
const processSteps=ref<EditableProcessStep[]>([blankProcessStep()]);
const form=reactive<{summary:string;finishedOutputWeightKg:string;finishedOutputQuantity:string;finishedOutputUnit:FinishedOutputUnit}>({summary:"",finishedOutputWeightKg:"",finishedOutputQuantity:"",finishedOutputUnit:"袋"});

const canSaveDraft=computed(()=>detail.value?canEditExperiment(detail.value,auth.displayName,auth.role):false);
const canNotify=computed(()=>detail.value?canNotifyInternalTest(detail.value,auth.displayName,auth.role):false);
const readOnly=computed(()=>!canSaveDraft.value);
const draftStatusLabel=computed(()=>({idle:"",local:"已本地保存",syncing:"正在自动保存",saved:"已自动保存",error:"网络异常，已本地保存"}[draftSyncState.value]));
const formulaWeights=computed(()=>materials.value.map((item)=>Number(item.weightKg||0)));
const formulaRatiosValue=computed(()=>formulaRatios(formulaWeights.value));
const totalFormulaWeight=computed(()=>formulaWeights.value.reduce((sum,weight)=>sum+weight,0));
const yieldBasisWeight=computed(()=>yieldBasisWeightKg(materials.value.map(item=>({weightKg:Number(item.weightKg||0),utilizationRatePercent:Number(item.utilizationRate||100),materialCategory:item.materialCategory,primaryMaterial:item.primaryMaterial})),yieldCalculationMode.value));
const pricingPreview=computed(()=>calculatePricingPreview({totalInputWeightKg:totalFormulaWeight.value,yieldBasisWeightKg:yieldBasisWeight.value,finishedOutputWeightKg:Number(form.finishedOutputWeightKg||0),finishedOutputQuantity:Number(form.finishedOutputQuantity||0)}));
const localDraftKey=computed(()=>experimentDraftKey(auth.principal?.userId||auth.user?.id||auth.displayName,String(route.params.id)));

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
    yieldCalculationMode.value=existing.yieldCalculationMode||"SELECTED_PRIMARY_MATERIALS";
    draftSaved.value=true;
    phase.value=readOnly.value?"readonly":"execute";
  }else if(readOnly.value){phase.value="readonly"}
  else if(/酱汁|复合调味/.test(data.project?.productType||"")){yieldCalculationMode.value="TOTAL_PICKING_WEIGHT"}
  if(existing?.materials?.length){
    const savedMaterials=existing.materials.map(item=>({
    materialCategory:item.materialCategory??categoryFromStage(item.stage),
    primaryMaterial:item.primaryMaterial??false,
    materialCode:item.materialCode||"",
    materialName:item.materialName,
    weightKg:String(item.weightKg??""),
    utilizationRate:item.utilizationRate!=null?String(item.utilizationRate*100):"100",
    remark:item.remark||"",
    }));
    materials.value=savedMaterials.length?savedMaterials:[blankMaterial(false)];
  }
  if(existing?.processSteps?.length)processSteps.value=fromProcessSteps(existing.processSteps);
}

async function loadProcessTemplate(versionId:string){
  try{const steps=await api.sample.processSteps(versionId);if(steps.length)processSteps.value=fromProcessSteps(steps)}catch{ /* 不阻断打样 */ }
}

async function loadDetail(){
  loading.value=true;loadError.value="";
  try{await ensureAuthReady();const data=await api.task.detail(String(route.params.id),auth.role,auth.displayName);applyDetail(data);if(!data.currentExperimentForm?.processSteps?.length&&!readOnly.value)await loadProcessTemplate(data.task.versionId);restoreLocalDraft(data.currentExperimentForm?.savedAt)}
  catch(error){loadError.value=error instanceof Error?error.message:"无法打开实验单"}
  finally{loading.value=false}
}

onMounted(()=>{window.addEventListener("pagehide",persistLocalDraft);void loadDetail().finally(()=>{hydrated.value=true})});
onBeforeUnmount(()=>{persistLocalDraft();window.removeEventListener("pagehide",persistLocalDraft);if(autoSaveTimer)window.clearTimeout(autoSaveTimer)});

function startArrangeMode(){phase.value="arrange"}
function confirmArrangement(){
  if(!processSteps.value.some(step=>step.processName.trim())){showFailToast("请至少填写一个工序");return}
  processSteps.value=processSteps.value.filter(step=>step.processName.trim());
  currentStepIndex.value=0;phase.value="execute";
}
function previousStep(){currentStepIndex.value=Math.max(0,currentStepIndex.value-1)}
function nextStep(){currentStepIndex.value=Math.min(processSteps.value.length-1,currentStepIndex.value+1)}
function addMaterial(){materials.value.push(blankMaterial())}
function removeMaterial(index:number){if(materials.value.length<=1)return;materials.value.splice(index,1)}

function categoryFromStage(stage?:string):MaterialCategory{
  if(stage==="辅料"||stage==="AUXILIARY")return "AUXILIARY";
  if(stage==="包材"||stage==="PACKAGING")return "PACKAGING";
  return "RAW";
}

function categoryLabel(category:MaterialCategory){
  if(category==="AUXILIARY")return "辅料";
  if(category==="PACKAGING")return "包材";
  return "原料";
}

function formulaRatioAt(index:number){
  return formulaRatiosValue.value[index]??0;
}

function buildMaterials():ExperimentMaterial[]{return materials.value.filter(item=>item.materialName.trim()).map((item,index)=>({stage:categoryLabel(item.materialCategory),sequence:index+1,materialCode:item.materialCode.trim()||undefined,materialName:item.materialName.trim(),weightKg:Number(item.weightKg||0),utilizationRate:Number(item.utilizationRate||100)/100,materialCategory:item.materialCategory,primaryMaterial:item.materialCategory==="RAW"&&item.primaryMaterial,inputUnit:"kg",remark:item.remark.trim()||undefined}))}

function validateFinishedOutputQuantity(){
  const quantity=normalizePositiveIntegerQuantity(form.finishedOutputQuantity);
  if(form.finishedOutputQuantity!==""&&quantity===undefined){showFailToast("成品数量必须为正整数");return undefined}
  return quantity;
}

function draftSnapshot(){return {phase:phase.value,yieldCalculationMode:yieldCalculationMode.value,currentStepIndex:currentStepIndex.value,form:{...form},materials:materials.value.map(item=>({...item})),processSteps:processSteps.value.map(item=>({...item}))}}
function persistLocalDraft(){
  if(!hydrated.value||readOnly.value)return;
  writeExperimentDraft(localStorage,localDraftKey.value,draftSnapshot());
  if(!["syncing","error"].includes(draftSyncState.value))draftSyncState.value="local";
}
function restoreLocalDraft(serverSavedAt?:string){
  if(readOnly.value){clearExperimentDraft(localStorage,localDraftKey.value);return}
  const cached=readExperimentDraft<ReturnType<typeof draftSnapshot>>(localStorage,localDraftKey.value);
  if(!cached||!isCachedDraftNewer(cached.savedAt,serverSavedAt))return;
  phase.value=cached.value.phase;
  yieldCalculationMode.value=cached.value.yieldCalculationMode;
  currentStepIndex.value=cached.value.currentStepIndex;
  Object.assign(form,cached.value.form);
  materials.value=cached.value.materials;
  processSteps.value=cached.value.processSteps;
  draftSyncState.value="local";
}
function scheduleAutoSave(){
  if(!hydrated.value||readOnly.value)return;
  persistLocalDraft();
  if(autoSaveTimer)window.clearTimeout(autoSaveTimer);
  autoSaveTimer=window.setTimeout(()=>void autoSave(),1500);
}
async function autoSave(){
  if(!canSaveDraft.value)return;
  await saveDraft({silent:true});
}

watch([form,materials,processSteps,yieldCalculationMode,phase],scheduleAutoSave,{deep:true});

async function saveDraft(options:{silent?:boolean}={}){
  const finishedOutputQuantity=validateFinishedOutputQuantity();
  if(form.finishedOutputQuantity!==""&&finishedOutputQuantity===undefined)return;
  saving.value=true;
  draftSyncState.value="syncing";
  try{const saved=await api.task.saveExperimentDraft(String(route.params.id),{operatorName:auth.displayName,summary:form.summary,materials:buildMaterials(),processSteps:toProcessSteps(processSteps.value),finishedOutputWeightKg:Number(form.finishedOutputWeightKg||0)||undefined,finishedOutputQuantity,finishedOutputUnit:form.finishedOutputUnit,yieldCalculationMode:yieldCalculationMode.value});detail.value={...detail.value!,currentExperimentForm:saved};draftSaved.value=true;draftSyncState.value="saved";writeExperimentDraft(localStorage,localDraftKey.value,draftSnapshot(),saved.savedAt);if(!options.silent)showSuccessToast("草稿已保存");return true}
  catch(error){draftSyncState.value="error";persistLocalDraft();if(!options.silent)showFailToast(error instanceof Error?error.message:"保存失败");return false}
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
  if(yieldBasisWeight.value<=0){showFailToast(yieldCalculationMode.value==="SELECTED_PRIMARY_MATERIALS"?"请至少选择一项有重量的主料":"请填写非包材物料重量");return}
  if(!processSteps.value.some(step=>step.processName.trim()&&Number(step.beforeWeightKg)>0)){showFailToast("通知测试前请至少完成一道有效工序");return}
  if(Number(form.finishedOutputWeightKg)<=0){showFailToast("通知测试前请填写成品重量");return}
  if(form.finishedOutputQuantity===""){showFailToast("通知测试前请填写成品数量");return}
  if(validateFinishedOutputQuantity()===undefined)return;
  if(!detail.value?.currentExperimentForm?.id&&canSaveDraft.value)await saveDraft();
  const experimentId=detail.value?.currentExperimentForm?.id;
  if(!experimentId){showFailToast("请先保存草稿");return}
  submitting.value=true;
  try{await api.task.submitExperimentForTest(experimentId,auth.displayName);hydrated.value=false;clearExperimentDraft(localStorage,localDraftKey.value);showSuccessToast("已通知内部测试");router.push("/todo")}
  catch(error){showFailToast(error instanceof Error?error.message:"提交失败")}
  finally{submitting.value=false}
}
</script>

<style scoped>
.experiment-page{background:#f6f8fb}.mode-section,.content-section{padding:18px 14px}.mode-section h1{margin:8px 0;font-size:24px}.mode-section>p{margin:0 0 20px;color:#64748b}.mode-card{display:block;width:100%;margin-bottom:12px;padding:20px;text-align:left;background:#fff;border:1px solid #e2e8f0;border-radius:8px}.mode-card--recommended{border:2px solid #246bfe;background:#f7faff}.mode-card__tag{display:block;margin-bottom:12px;color:#246bfe;font-size:12px;font-weight:700}.mode-card strong{display:block;margin-bottom:7px;font-size:20px}.mode-card small{display:block;color:#64748b;line-height:1.6}.form-section{margin-top:14px;padding:14px 0;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.form-section>h2,.form-section>.section-title,.form-section>.execution-heading{padding:0 14px}.form-section h2{margin:0 0 10px;font-size:17px}.section-title{display:flex;justify-content:space-between;margin-bottom:14px}.section-title h2{margin:0 0 4px}.section-title p{margin:0;color:#64748b;font-size:12px}.execution-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.execution-heading h2{margin-bottom:3px}.execution-heading span{display:block;color:#64748b;font-size:12px}.formula-summary{display:flex;justify-content:space-between;padding:0 16px 10px;color:#64748b;font-size:12px}.material-row{margin:0 8px 10px;padding:8px;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.material-row .van-button{margin:6px 16px}.pricing-preview__material{display:flex;justify-content:space-between;gap:12px;padding:9px 16px;border-bottom:1px solid #edf0f5;color:#475569;font-size:13px}.pricing-preview__material strong{color:#172033;font-weight:600;text-align:right}.pricing-preview__grid{display:grid;grid-template-columns:1fr 1fr;gap:1px;background:#e7ebf2}.pricing-preview__grid span{display:flex;flex-direction:column;gap:5px;padding:12px 16px;background:#fff;color:#64748b;font-size:12px}.pricing-preview__grid strong{color:#172033;font-size:14px}
</style>
