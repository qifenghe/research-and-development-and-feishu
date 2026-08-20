import assert from "node:assert/strict";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";
import { createRenderer, nextTick } from "../apps/mobile/node_modules/vue/index.mjs";
import { createPinia } from "../apps/mobile/node_modules/pinia/dist/pinia.mjs";
import { createMemoryHistory, createRouter } from "../apps/mobile/node_modules/vue-router/dist/vue-router.mjs";
import { createServer, type ViteDevServer } from "../apps/mobile/node_modules/vite/dist/node/index.js";

type MountedActions = {
  afterRead: (item: { file?: File }) => Promise<void>;
  notifyTest: () => Promise<void>;
  hydrated: boolean;
};

class MemoryStorage implements Storage {
  #values = new Map<string,string>();
  get length(){return this.#values.size}
  clear(){this.#values.clear()}
  getItem(key:string){return this.#values.get(key)??null}
  key(index:number){return [...this.#values.keys()][index]??null}
  removeItem(key:string){this.#values.delete(key)}
  setItem(key:string,value:string){this.#values.set(key,String(value))}
}

function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(done=>{resolve=done});return {promise,resolve}}
const flush=async()=>{await Promise.resolve();await nextTick();await Promise.resolve();await nextTick()};

function form(task:"task-a"|"task-b"){
  const suffix=task.at(-1)!;
  return {id:`form-${suffix}`,taskId:task,projectId:`project-${suffix}`,versionId:`version-${suffix}`,sampleNo:`S-${suffix}`,productName:`产品 ${suffix}`,versionCode:`V-${suffix}`,status:"DRAFT",operatorName:"研发工程师",summary:`summary-${suffix}`,materials:[{stage:"原料",sequence:1,materialName:"主料",weightKg:10,utilizationRate:1,materialCategory:"RAW",primaryMaterial:true,inputUnit:"kg"}],processSteps:[{sequence:1,processName:"熟制",beforeWeightKg:10,afterWeightKg:9,remainingWeightKg:0,remainingDisposition:"DISCARD"}],finishedOutputWeightKg:9,finishedOutputQuantity:9,finishedOutputUnit:"袋",yieldCalculationMode:"SELECTED_PRIMARY_MATERIALS",savedAt:"2026-08-20T12:00:00",submittedAt:null};
}
function detail(task:"task-a"|"task-b",withForm:boolean){
  const suffix=task.at(-1)!;
  return {task:{id:task,projectId:`project-${suffix}`,versionId:`version-${suffix}`,sampleNo:`S-${suffix}`,productName:`产品 ${suffix}`,versionCode:`V-${suffix}`,status:"SAMPLING",assigneeName:"研发工程师",createdAt:"2026-08-20T10:00:00"},version:{id:`version-${suffix}`,projectId:`project-${suffix}`,sampleNo:`S-${suffix}`,productName:`产品 ${suffix}`,productType:"预制菜",specification:"1kg",versionNo:`V-${suffix}`,versionNumber:1,versionCode:`V-${suffix}`,materials:[],createdAt:"2026-08-20T10:00:00"},project:null,currentExperimentForm:withForm?form(task):null,currentTestAssignment:null,fieldGroups:[],availableActions:[]};
}
const emptyPlan=(formId:string)=>({id:`plan-${formId}`,experimentFormId:formId,versionNo:1,status:"DRAFT",majorProcesses:[],balanceToleranceKg:0.01,legacy:true});

let server:ViteDevServer;
let documentAppends=0;
test.before(async()=>{
  Object.assign(globalThis,{
    localStorage:new MemoryStorage(),
    window:{addEventListener(){},removeEventListener(){},clearTimeout,setTimeout},
    document:{body:{appendChild(){documentAppends++},removeChild(){}},createElement(){return {style:{},remove(){},click(){}}}},
  });
  Object.assign(globalThis.URL,{createObjectURL(){return "blob:test"},revokeObjectURL(){}});
  const configFile=fileURLToPath(new URL("../apps/mobile/vite.config.ts",import.meta.url));
  server=await createServer({root:dirname(configFile),configFile,server:{middlewareMode:true},ssr:{noExternal:["vant"]},appType:"custom",logLevel:"silent"});
});
test.after(async()=>{await server.close()});

async function mountView(aHasForm:boolean){
  localStorage.clear();
  documentAppends=0;
  const apiModule=await server.ssrLoadModule("/src/services/api.ts");
  const authModule=await server.ssrLoadModule("/src/stores/auth.ts");
  const viewModule=await server.ssrLoadModule("/src/views/ExperimentFormView.vue");
  viewModule.default.render=()=>null;
  const api=apiModule.api as any;
  api.task.detail=async(taskId:string)=>detail(taskId as "task-a"|"task-b",taskId==="task-b"||aHasForm);
  api.sample.processSteps=async()=>[];
  api.task.getProcessPlan=async(formId:string)=>emptyPlan(formId);
  const router=createRouter({history:createMemoryHistory("/m/"),routes:[{path:"/experiments/:id",component:{render(){return null}}},{path:"/todo",component:{render(){return null}}}]});
  await router.push("/experiments/task-a");await router.isReady();
  const pinia=createPinia();
  const auth=authModule.useAuthStore(pinia);auth.token="test";auth.principal={userId:"engineer-1",name:"研发工程师",role:"RND_ENGINEER",expiresAt:"2099-01-01T00:00:00Z"};
  type HostNode={parent:HostNode|null;children:HostNode[];props:Record<string,unknown>;text?:string};
  const renderer=createRenderer<HostNode,HostNode>({patchProp(el,key,_old,next){el.props[key]=next},insert(child,parent,anchor){child.parent=parent;const index=anchor?parent.children.indexOf(anchor):-1;if(index<0)parent.children.push(child);else parent.children.splice(index,0,child)},remove(child){if(child.parent){const index=child.parent.children.indexOf(child);if(index>=0)child.parent.children.splice(index,1)}},createElement(){return {parent:null,children:[],props:{}}},createText(text){return {parent:null,children:[],props:{},text}},createComment(text){return {parent:null,children:[],props:{},text}},setText(node,text){node.text=text},setElementText(node,text){node.text=text},parentNode(node){return node.parent},nextSibling(node){if(!node.parent)return null;return node.parent.children[node.parent.children.indexOf(node)+1]??null},querySelector(){return null},setScopeId(){},insertStaticContent(){const node:HostNode={parent:null,children:[],props:{}};return [node,node]}});
  const root:HostNode={parent:null,children:[],props:{}};
  const app=renderer.createApp(viewModule.default);app.use(pinia).use(router).provide(Symbol.for("v-scx"),{modules:new Set<string>()});
  const instance=app.mount(root) as any;
  const setup=instance.$.setupState;
  const view:MountedActions={afterRead:setup.afterRead,notifyTest:setup.notifyTest,get hydrated(){return setup.hydrated}};
  await flush();
  return {api,router,view,unmount:()=>app.unmount()};
}

test("mounted A upload stops after deferred draft save when the memory router reuses the view for B",async()=>{
  const mounted=await mountView(false);const save=deferred<any>();const uploads:string[]=[];
  mounted.api.task.saveExperimentDraft=async(taskId:string)=>{assert.equal(taskId,"task-a");return save.promise};
  mounted.api.task.uploadAttachment=async(formId:string)=>{uploads.push(formId)};
  const action=mounted.view.afterRead({file:new File(["a"],"a.jpg",{type:"image/jpeg"})});await flush();
  localStorage.setItem("rnd:experiment-draft:v1:engineer-1:task-b","B-SENTINEL");
  await mounted.router.push("/experiments/task-b");await flush();save.resolve(form("task-a"));await action;await flush();
  assert.deepEqual(uploads,[]);assert.equal(mounted.router.currentRoute.value.params.id,"task-b");assert.equal(localStorage.getItem("rnd:experiment-draft:v1:engineer-1:task-b"),"B-SENTINEL");
  assert.equal(mounted.view.hydrated,true);
  assert.equal(documentAppends,0,"a stale upload completion must not publish a toast");
  mounted.unmount();
});

test("mounted late A submit cannot clear B cache, toast or navigate after memory-router reuse",async()=>{
  const mounted=await mountView(true);const submit=deferred<unknown>();const submissions:string[]=[];
  mounted.api.task.submitExperimentForTest=async(formId:string)=>{submissions.push(formId);return submit.promise};
  const action=mounted.view.notifyTest();await flush();
  localStorage.setItem("rnd:experiment-draft:v1:engineer-1:task-b","B-SENTINEL");
  await mounted.router.push("/experiments/task-b");await flush();submit.resolve({});await action;await flush();
  assert.deepEqual(submissions,["form-a"]);assert.equal(mounted.router.currentRoute.value.params.id,"task-b");assert.equal(localStorage.getItem("rnd:experiment-draft:v1:engineer-1:task-b"),"B-SENTINEL");
  assert.equal(mounted.view.hydrated,true);
  assert.equal(documentAppends,0,"a stale submit completion must not publish a toast");
  mounted.unmount();
});
