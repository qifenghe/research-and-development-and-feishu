import './mobile-dom-bootstrap.mts';
import test from 'node:test';
import assert from 'node:assert/strict';
import {createRenderer,nextTick} from '../apps/pc/node_modules/vue/index.mjs';
import {createPinia} from '../apps/pc/node_modules/pinia/dist/pinia.mjs';
import {createMemoryHistory,createRouter} from '../apps/pc/node_modules/vue-router/dist/vue-router.mjs';
import {createServer} from '../apps/pc/node_modules/vite/dist/node/index.js';
import {fileURLToPath} from 'node:url';
import {canAccessRoute} from '../packages/shared/src/permissions/index.ts';
const values=new Map<string,string>();
Object.assign(document,{getElementsByTagName:()=>[]});
Object.assign(globalThis,{localStorage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>values.set(k,v),removeItem:(k:string)=>values.delete(k),clear:()=>values.clear()}});
let server:any,api:any,authModule:any,context:any;
const flush=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}};
const fixture=()=>({task:{id:'T1',productName:'牛腩',sampleNo:'S1',status:'SAMPLING',assigneeName:'张研发',versionCode:'A0'},version:{id:'V1',projectId:'P1',specification:'1kg'},project:null,fieldGroups:[],availableActions:[],currentExperimentForm:null,currentTestAssignment:null});
test.before(async()=>{
 const root=fileURLToPath(new URL('../apps/pc/',import.meta.url));
 server=await createServer({root,server:{middlewareMode:true},appType:'custom',logLevel:'silent'});
 api=(await server.ssrLoadModule('/src/services/api')).api;
 authModule=await server.ssrLoadModule('/src/stores/auth');
 context=(await server.ssrLoadModule('/src/composables/adminContext')).useAdminContext();
});
test.after(async()=>{await server.close()});
async function mount(file:string,role:string,configure=()=>{},props={}){
 const pinia=createPinia();const auth=authModule.useAuthStore(pinia);auth.token='session-A';auth.principal={userId:'u1',role,name:role==='RND_DIRECTOR'?'赵总监':'张研发'};
 api.task.detail=async()=>fixture();api.task.testRecords=async()=>[];api.task.assignees=async()=>[];api.shipment.list=async()=>[];
 api.task.list=async()=>[];api.shipment.pricingFiles=async()=>[];
 api.dashboard.overview=async()=>({recentTasks:[{taskId:'T1',assigneeName:'张研发',status:'SAMPLING'}],pendingPricingFiles:[]});configure();
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/rnd/tasks/:id',component:{render:()=>null}},{path:'/rnd/history/:id',component:{render:()=>null}},{path:'/rnd/tasks/:id/experiment',component:{render:()=>null}},{path:'/dashboard',component:{render:()=>null}}]});await router.push('/rnd/tasks/T1');
 const component=(await server.ssrLoadModule(file)).default;component.render=()=>null;
 const renderer=createRenderer<any,any>({patchProp(){},insert(){},remove(){},createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode:()=>null,nextSibling:()=>null,insertStaticContent:()=>[{},{}]});
 const app=renderer.createApp(component,props);app.use(pinia).use(router).provide(Symbol.for('v-scx'),{modules:new Set()});app.config.errorHandler=()=>{};
 const instance:any=app.mount({});await flush();return {state:instance.$.setupState,auth,router,unmount:()=>app.unmount()};
}
test('assistant can follow a task but cannot enter the editable experiment',()=>{
 assert.equal(canAccessRoute('RND_ASSISTANT','/rnd/tasks/T1','pc'),true);
 assert.equal(canAccessRoute('RND_ASSISTANT','/rnd/tasks/T1/experiment','pc'),false);
});
test('403 does not invoke the session-expiry handler, 401 still does',async()=>{
 const mod=await server.ssrLoadModule('/src/services/api');let expired=0;mod.setUnauthorizedHandler(()=>expired++);const original=globalThis.fetch;
 try{globalThis.fetch=async()=>new Response(JSON.stringify({code:'SESSION_ROLE_FORBIDDEN',message:'拒绝'}),{status:403,headers:{'Content-Type':'application/json'}});await assert.rejects(mod.client.get('/denied'));assert.equal(expired,0);
 globalThis.fetch=async()=>new Response('{}',{status:401});await assert.rejects(mod.client.get('/expired'));assert.equal(expired,1);}finally{globalThis.fetch=original;mod.setUnauthorizedHandler(()=>{});}
});
for(const role of ['RND_ENGINEER','TESTER'])test(`${role} task page does not request director or shipment APIs`,async()=>{
 let restricted=0;const m=await mount('/src/views/rnd/TaskDetailView.vue',role,()=>{api.task.assignees=async()=>{restricted++;return []};api.shipment.list=async()=>{restricted++;return []}});
 try{assert.equal(m.state.detail.task.id,'T1');assert.equal(restricted,0);}finally{m.unmount()}
});
test('director is not shown the unrelated-owner prohibition',async()=>{
 const m=await mount('/src/views/rnd/TaskDetailView.vue','RND_DIRECTOR');try{assert.equal(m.state.permissionHint,'');}finally{m.unmount()}
});
for(const role of ['TESTER','FINANCE'])test(`${role} dashboard excludes sampling tasks`,async()=>{
 const m=await mount('/src/views/DashboardView.vue',role);try{assert.deepEqual(m.state.visibleTasks,[]);if(role==='FINANCE')assert.ok(m.state.primaryActions.some((x:any)=>x.path==='/finance'));}finally{m.unmount()}
});
test('signout clears project context and a delayed old task cannot republish it',async()=>{
 let resolve!:(v:any)=>void;const pending=new Promise(r=>resolve=r);
 const m=await mount('/src/views/rnd/TaskDetailView.vue','RND_ENGINEER',()=>{api.task.detail=()=>pending});
 context.setPanel({title:'旧项目',rows:[]});m.auth.signOut();assert.equal(context.panel.value,null);
 resolve(fixture());await flush();assert.equal(context.panel.value,null);assert.equal(m.state.detail,null);m.unmount();
});
test('tester dashboard keeps only assignments belonging to the current account',async()=>{
 const m=await mount('/src/views/DashboardView.vue','TESTER',()=>{
  api.task.list=async(params:any)=>{assert.equal(params.status,'PENDING_TEST');return [{id:'mine',status:'PENDING_TEST'},{id:'other',status:'PENDING_TEST'}]};
  api.task.detail=async(id:string)=>({...fixture(),currentTestAssignment:{testerUserId:id==='mine'?'u1':'u2',testerName:'同名测试'}});
 });try{assert.deepEqual(m.state.visibleTasks.map((x:any)=>x.taskId),['mine']);}finally{m.unmount()}
});
test('finance dashboard uses notified pricing inbox rather than global pending pricing',async()=>{
 const m=await mount('/src/views/DashboardView.vue','FINANCE',()=>{
  api.shipment.pricingFiles=async(params:any)=>{assert.equal(params.status,'FINANCE_NOTIFIED');return [{id:'PRICE-ready',status:'FINANCE_NOTIFIED'}]};
 });try{assert.deepEqual(m.state.visiblePricingFiles.map((x:any)=>x.pricingFileId),['PRICE-ready']);}finally{m.unmount()}
});
test('engineer dashboard queries their full active list before taking recent tasks',async()=>{
 const m=await mount('/src/views/DashboardView.vue','RND_ENGINEER',()=>{
  api.dashboard.overview=async()=>({recentTasks:[{taskId:'other',assigneeName:'他人',status:'SAMPLING'}],pendingPricingFiles:[]});
  api.task.list=async()=>[{id:'older-mine',assigneeName:'张研发',status:'SAMPLING'},{id:'done',assigneeName:'张研发',status:'COMPLETED'}];
 });try{assert.deepEqual(m.state.visibleTasks.map((x:any)=>x.taskId),['older-mine']);}finally{m.unmount()}
});
test('administrator retains quick actions',async()=>{
 const m=await mount('/src/views/DashboardView.vue','SYSTEM_ADMIN');try{assert.ok(m.state.primaryActions.length>0);}finally{m.unmount()}
});
test('assistant experiment action goes to readable history rather than forbidden editor',async()=>{
 const m=await mount('/src/views/rnd/TaskDetailView.vue','RND_ASSISTANT');try{const navigated=new Promise<void>(resolve=>{m.router.afterEach(()=>resolve())});m.state.openExperimentForm();await navigated;assert.equal(m.router.currentRoute.value.path,'/rnd/history/V1');}finally{m.unmount()}
});
test('assistant cannot activate pending-test write action',async()=>{
 const detail={...fixture(),task:{...fixture().task,status:'PENDING_TEST'}};
 const m=await mount('/src/components/TaskWorkflowPanel.vue','RND_ASSISTANT',()=>{},{detail,role:'RND_ASSISTANT',operatorName:'内勤',shipment:null});
 try{assert.equal(m.state.actionBlocks.find((b:any)=>b.key==='internal-test').buttons[0].disabled,true);}finally{m.unmount()}
});
