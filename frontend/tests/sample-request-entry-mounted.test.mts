import './mobile-dom-bootstrap.mts';
import test from 'node:test';
import assert from 'node:assert/strict';
import {createRenderer} from '../apps/pc/node_modules/vue/index.mjs';
import {createPinia} from '../apps/pc/node_modules/pinia/dist/pinia.mjs';
import {createServer} from '../apps/pc/node_modules/vite/dist/node/index.js';
import {fileURLToPath} from 'node:url';
Object.assign(document,{getElementsByTagName:()=>[],querySelectorAll:()=>[]});
Object.defineProperty(globalThis,'localStorage',{value:{getItem:()=>null,setItem(){},removeItem(){}},configurable:true});
for(const target of ['pc','mobile'])test(`${target} real form sends template answers and keeps them after failed save`,async()=>{
 const root=fileURLToPath(new URL(`../apps/${target}`,import.meta.url));
 const server=await createServer({root,configFile:root+'/vite.config.ts',server:{middlewareMode:true},ssr:{noExternal:['vant']},appType:'custom',logLevel:'silent'});
 let app:any;
 try{
 const {api}=await server.ssrLoadModule('/src/services/api.ts');
 const {useAuthStore}=await server.ssrLoadModule('/src/stores/auth.ts');
 const component=(await server.ssrLoadModule(target==='pc'?'/src/views/demand/RequestNewView.vue':'/src/views/RequestNewView.vue')).default;component.render=()=>null;
 const renderer=createRenderer<any,any>({patchProp(){},insert(){},remove(){},createElement(){return {}},createText(){return {}},createComment(){return {}},setText(){},setElementText(){},parentNode(){return null},nextSibling(){return null},querySelector(){return null},setScopeId(){},insertStaticContent(){return [{},{}]}});
 const pinia=createPinia();const auth=useAuthStore(pinia);auth.principal={userId:'u',name:'测试内勤',role:'RND_ASSISTANT',expiresAt:'2099-01-01T00:00:00Z'};
 app=renderer.createApp(component);app.use(pinia);app.provide(Symbol.for('v-scx'),{modules:new Set()});const s=(app.mount({}) as any).$.setupState;
 assert.equal(s.form.referenceSampleProvided,null);
 Object.assign(s.form,{productName:'牛腩',productType:'预制菜',customerName:'客户',specification:'500g/袋',applicationScenario:'餐饮',flavorRequirement:'香辣',referenceSampleProvided:false,cutShape:'20×30×40 mm',mainMaterialRequirement:'牛腩',textureRequirement:'软烂',packagingRequirement:'真空袋',storageRequirement:'冷冻',customerTargetPrice:'18元/袋，未税',monthlyDemand:'3吨/月',specialRequirements:'无',referenceMarketPrice:'',additionalNotes:'',creatorName:target==='pc'?'实际销售':'测试内勤'});
 let payload:any;api.sample.create=async(p:any)=>{payload=p;throw Error('暂时无法保存');};
 await s.submit();assert.equal(payload.referenceSampleProvided,false);assert.equal(payload.monthlyDemand,'3吨/月');assert.equal(payload.creatorName,target==='pc'?'实际销售':'测试内勤');assert.equal(s.form.cutShape,'20×30×40 mm');assert.match(s.submitError,/暂时无法保存/);
 payload=undefined;s.form.cutShape=' ';if(target==='mobile')s.expandedGroups=[];await s.submit();assert.equal(payload,undefined);
 if(target==='mobile')assert.ok(s.expandedGroups.includes('产品要求'),'invalid field must be visible even if its group was collapsed');
 }finally{app?.unmount();await server.close();}
});
