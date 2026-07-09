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
          <div v-if="!readOnly" class="execution-heading">
            <div><strong>{{ samplingModeLabel }}</strong><span>一次只填写一个工序</span></div>
            <van-button size="small" plain @click="startArrangeMode">调整工序</van-button>
          </div>
          <ProcessStepEditor
            v-model="processSteps"
            :mode="readOnly ? 'readonly' : 'execute'"
            :readonly="readOnly"
            :current-index="currentStepIndex"
            @previous="previousStep"
            @next="nextStep"
          />

          <van-collapse v-model="activeSections" class="supporting-sections">
            <van-collapse-item title="配方" name="materials">
              <div class="formula-summary">
                <span>总投入 {{ totalFormulaWeight.toFixed(3) }}kg</span>
                <span>主原料 {{ primaryMaterialName }}</span>
              </div>
              <div v-for="(material,index) in materials" :key="index" class="material-row">
                <van-field label="类别">
                  <template #input>
                    <van-radio-group v-model="material.materialCategory" direction="horizontal" :disabled="readOnly">
                      <van-radio name="RAW">原料</van-radio>
                      <van-radio name="AUXILIARY">辅料</van-radio>
                      <van-radio name="PACKAGING">包材</van-radio>
                    </van-radio-group>
                  </template>
                </van-field>
                <van-field label="主原料">
                  <template #input>
                    <van-checkbox
                      :model-value="material.primaryMaterial"
                      :disabled="readOnly || material.materialCategory === 'PACKAGING'"
                      @update:model-value="setPrimaryMaterial(index)"
                    >
                      用于计算成品得率
                    </van-checkbox>
                  </template>
                </van-field>
                <van-field v-model="material.materialName" label="物料" placeholder="物料名称" :readonly="readOnly" />
                <van-field v-model="material.materialCode" label="ERP编码" placeholder="可选" :readonly="readOnly" />
                <van-field v-model="material.weightKg" label="重量" type="number" :readonly="readOnly">
                  <template #button>kg</template>
                </van-field>
                <van-field label="配方比例">
                  <template #input>{{ formulaRatioAt(index).toFixed(2) }}%</template>
                </van-field>
                <van-field v-model="material.utilizationRate" label="利用率" type="number" :readonly="readOnly">
                  <template #button>%</template>
                </van-field>
                <van-button v-if="!readOnly && materials.length > 1" size="mini" plain type="danger" @click="removeMaterial(index)">删除</van-button>
              </div>
              <van-button v-if="!readOnly" block plain type="primary" size="small" @click="addMaterial">+ 添加配方行</van-button>
            </van-collapse-item>
            <van-collapse-item title="成品出成" name="yield">
              <div class="yield-card">
                <div><span>主原料投入</span><strong>{{ primaryInputWeight.toFixed(3) }}kg</strong></div>
                <div><span>成品得率</span><strong>{{ finishedYield.toFixed(2) }}%</strong></div>
              </div>
              <van-field v-model="form.finishedOutputWeightKg" label="成品实际产出" type="number" placeholder="请输入" :readonly="readOnly">
                <template #button>kg</template>
              </van-field>
            </van-collapse-item>
            <van-collapse-item title="实验说明与附件" name="notes">
              <van-field v-model="form.summary" rows="2" autosize type="textarea" label="实验摘要" placeholder="选填" :readonly="readOnly" />
              <van-uploader v-if="!readOnly" v-model="fileList" :after-read="afterRead" />
            </van-collapse-item>
          </van-collapse>
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
import { canEditExperiment, canNotifyInternalTest, finishedYieldPercent, formulaRatios } from "@rnd/shared";
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
const activeSections=ref<string[]>([]);
const fileList=ref<Array<{url?:string;file?:File}>>([]);
const headerTitle=ref("实验单录入");

const blankMaterial=(primaryMaterial=false):EditableMaterial=>({
  materialCategory:"RAW",
  primaryMaterial,
  materialCode:"",
  materialName:"",
  weightKg:"",
  utilizationRate:"100",
  remark:"",
});
const materials=ref<EditableMaterial[]>([blankMaterial(true)]);
const processSteps=ref<EditableProcessStep[]>([blankProcessStep()]);
const form=reactive({summary:"",finishedOutputWeightKg:""});

const canSaveDraft=computed(()=>detail.value?canEditExperiment(detail.value,auth.displayName,auth.role):false);
const canNotify=computed(()=>detail.value?canNotifyInternalTest(detail.value,auth.displayName,auth.role):false);
const readOnly=computed(()=>!canSaveDraft.value);
const samplingModeLabel=computed(()=>samplingMode.value==="quick"?"快速打样":"编排后打样");
const formulaWeights=computed(()=>materials.value.map((item)=>Number(item.weightKg||0)));
const formulaRatiosValue=computed(()=>formulaRatios(formulaWeights.value));
const totalFormulaWeight=computed(()=>formulaWeights.value.reduce((sum,weight)=>sum+weight,0));
const primaryMaterial=computed(()=>materials.value.find((item)=>item.primaryMaterial&&item.materialCategory!=="PACKAGING"));
const primaryMaterialName=computed(()=>primaryMaterial.value?.materialName?.trim()||"未选择");
const primaryInputWeight=computed(()=>Number(primaryMaterial.value?.weightKg||0));
const finishedYield=computed(()=>finishedYieldPercent(Number(form.finishedOutputWeightKg||0),primaryInputWeight.value));

async function ensureAuthReady(){if(!auth.principal)await auth.fetchMe().catch(()=>undefined)}

function applyDetail(data:RndTaskDetailView){
  detail.value=data;
  headerTitle.value=`${data.task.productName} ${data.task.versionCode}`;
  const existing=data.currentExperimentForm;
  if(existing){
    form.summary=existing.summary||"";
    form.finishedOutputWeightKg=existing.finishedOutputWeightKg!=null?String(existing.finishedOutputWeightKg):"";
    draftSaved.value=true;
    phase.value=readOnly.value?"readonly":"execute";
  }else if(readOnly.value){phase.value="readonly"}
  if(existing?.materials?.length){materials.value=existing.materials.map(item=>({
    materialCategory:item.materialCategory??categoryFromStage(item.stage),
    primaryMaterial:item.primaryMaterial??false,
    materialCode:item.materialCode||"",
    materialName:item.materialName,
    weightKg:String(item.weightKg??""),
    utilizationRate:item.utilizationRate!=null?String(item.utilizationRate*100):"100",
    remark:item.remark||"",
  }))}
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
function removeMaterial(index:number){materials.value.splice(index,1)}
function setPrimaryMaterial(index:number){
  if(readOnly.value)return;
  const target=materials.value[index];
  if(!target||target.materialCategory==="PACKAGING")return;
  materials.value=materials.value.map((item,itemIndex)=>({...item,primaryMaterial:itemIndex===index}));
}

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

function buildMaterials():ExperimentMaterial[]{return materials.value.filter(item=>item.materialName.trim()).map((item,index)=>({stage:categoryLabel(item.materialCategory),sequence:index+1,materialCode:item.materialCode.trim()||undefined,materialName:item.materialName.trim(),weightKg:Number(item.weightKg||0),utilizationRate:Number(item.utilizationRate||100)/100,materialCategory:item.materialCategory,primaryMaterial:item.materialCategory!=="PACKAGING"&&item.primaryMaterial,inputUnit:"kg",remark:item.remark.trim()||undefined}))}

async function saveDraft(){
  saving.value=true;
  try{detail.value={...detail.value!,currentExperimentForm:await api.task.saveExperimentDraft(String(route.params.id),{operatorName:auth.displayName,summary:form.summary,materials:buildMaterials(),processSteps:toProcessSteps(processSteps.value),finishedOutputWeightKg:Number(form.finishedOutputWeightKg||0)||undefined})};draftSaved.value=true;showSuccessToast("草稿已保存")}
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
  if(!buildMaterials().some(item=>item.primaryMaterial)){showFailToast("请先选择一个主原料");return}
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
.experiment-page{background:#f6f8fb}.mode-section,.content-section{padding:18px 14px}.mode-section h1{margin:8px 0;font-size:24px}.mode-section>p{margin:0 0 20px;color:#64748b}.mode-card{display:block;width:100%;margin-bottom:12px;padding:20px;text-align:left;background:#fff;border:1px solid #e2e8f0;border-radius:8px}.mode-card--recommended{border:2px solid #246bfe;background:#f7faff}.mode-card__tag{display:block;margin-bottom:12px;color:#246bfe;font-size:12px;font-weight:700}.mode-card strong{display:block;margin-bottom:7px;font-size:20px}.mode-card small{display:block;color:#64748b;line-height:1.6}.section-title{display:flex;justify-content:space-between;margin-bottom:14px}.section-title h2{margin:0 0 4px}.section-title p{margin:0;color:#64748b}.execution-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.execution-heading strong,.execution-heading span{display:block}.execution-heading span{margin-top:3px;color:#64748b;font-size:12px}.supporting-sections{margin-top:14px}.material-row{margin-bottom:10px;padding:8px;background:#fff;border:1px solid #e7ebf2;border-radius:8px}.material-row .van-button{margin:6px 16px}
</style>
