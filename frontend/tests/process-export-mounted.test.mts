import "./mobile-dom-bootstrap.mts";
import assert from "node:assert/strict";
import test from "node:test";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRenderer, nextTick, h, ref } from "../apps/pc/node_modules/vue/index.mjs";
import { createServer, type ViteDevServer } from "../apps/pc/node_modules/vite/dist/node/index.js";
type Node = { parent: Node | null; children: Node[]; props: Record<string, unknown>; text?: string };
const make = (): Node => ({parent:null,children:[],props:{}});
const renderer = createRenderer<Node, Node>({
  patchProp(el,key,_old,value){el.props[key]=value;}, insert(child,parent){child.parent=parent;parent.children.push(child);}, remove(){},
  createElement:make,createText(text){return {...make(),text};},createComment:make,setText(n,text){n.text=text;},setElementText(n,text){n.text=text;},
  parentNode(n){return n.parent;},nextSibling(){return null;},querySelector(){return null;},setScopeId(){},insertStaticContent(){return [make(),make()];}
});
let server: ViteDevServer;
const flush = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick(); };
test.before(async()=>{ Object.assign(globalThis.document, { getElementsByTagName: () => [] }); const configFile=fileURLToPath(new URL("../apps/pc/vite.config.ts",import.meta.url)); server=await createServer({root:dirname(configFile),configFile,server:{middlewareMode:true},appType:"custom",logLevel:"silent"}); });
test.after(async()=>{await server.close();});
test('failed save opens local HTML preview without claiming server checks',async()=>{
 const {exportApi}=await server.ssrLoadModule('/src/services/processExportApi.ts');let checks=0;exportApi.check=async()=>{checks++;return {ready:true,issues:[]};};
 const {default:Component}=await server.ssrLoadModule('/src/components/process/ProcessExportPreview.vue');Component.render=()=>null;
 const app=renderer.createApp(Component,{formId:'F',trialId:'A',versionNo:1,sourceLabel:'A',beforeCheck:async()=>false,plan:{versionNo:1,status:'DRAFT',majorProcesses:[]}});
 app.provide(Symbol.for('v-scx'),{modules:new Set()});const child:any=app.mount(make());await child.$.setupState.openPreview();await flush();
 assert.equal(checks,0);assert.equal(child.$.setupState.open,true);assert.equal(child.$.setupState.localOnly,true);assert.deepEqual(Object.keys(child.$.setupState.checks),[]);app.unmount();
});
test('packaging selection carries catalog quantity unit without altering food weight',async()=>{
 const {default:Component}=await server.ssrLoadModule('/src/components/process/MinorStepWorkspace.vue');Component.render=()=>null;
 const material={key:'bag',sequence:1,materialRole:'AUXILIARY',sourceType:'EXTERNAL',materialName:'',materialState:'SOLID',weightKg:5};
 const step={key:'s',sequence:1,stepName:'包装',stepType:'NORMAL',materials:[material],outputs:[],controlPoints:[]};
 const plan={versionNo:0,status:'DRAFT',majorProcesses:[{key:'m',sequence:1,processName:'包装',yieldBasis:'NONE',inputs:[],outputs:[],steps:[step]}]};
 let emitted:any;const app=renderer.createApp(Component,{modelValue:plan,majorKey:'m','onUpdate:modelValue':(v:any)=>{emitted=v;}});
 app.provide(Symbol.for('v-scx'),{modules:new Set()});const child:any=app.mount(make());await flush();
 const setup=child.$.setupState,row=setup.selectedStep.materials[0];
 row.materialRole='PACKAGING';setup.ensurePrimaryMaterial(row);
 setup.selectMaterial(row,{id:'B1',code:'BAG',name:'袋',category:'PACKAGING',unit:'个',specification:'500g',status:'ACTIVE',version:0});
 row.quantity=277;setup.publishLocal();await flush();
 const actual=emitted.majorProcesses[0].steps[0].materials[0];
 assert.equal(actual.quantityUnit,'个');assert.equal(actual.quantity,277);assert.equal(actual.weightKg,undefined);
 assert.equal(actual.sourceType,'EXTERNAL');assert.ok(setup.materialRoles.some((x:any)=>x.value==='PACKAGING'));
 app.unmount();
});
test('packaging picker queries packaging rather than ingredient-only catalog',async()=>{
 const {materialCatalogApi}=await server.ssrLoadModule('/src/services/materialCatalogApi.ts');
 const original=materialCatalogApi.list,categories=materialCatalogApi.classifications;let query:any;
 materialCatalogApi.list=async(p:any)=>{query=p;return {items:[{id:'b',code:'B',name:'袋',category:'PACKAGING',unit:'个',status:'ACTIVE'}],total:1};};
 materialCatalogApi.classifications=async()=>[];
 try{const {default:Component}=await server.ssrLoadModule('/src/components/materials/MaterialPicker.vue');Component.render=()=>null;
 const app=renderer.createApp(Component,{purpose:'packaging'});app.provide(Symbol.for('v-scx'),{modules:new Set()});const child:any=app.mount(make());await child.$.setupState.show();await flush();
 assert.equal(query.category,'PACKAGING');assert.notEqual(query.ingredientsOnly,'true');assert.equal(child.$.setupState.items[0].id,'b');app.unmount();
 }finally{materialCatalogApi.list=original;materialCatalogApi.classifications=categories;}
});
test('reusing a temporary packaging identity carries its role and unit without copying count',async()=>{
 const {default:Component}=await server.ssrLoadModule('/src/components/process/MinorStepWorkspace.vue');Component.render=()=>null;
 const source={key:'bag',sequence:1,sourceType:'EXTERNAL',temporaryMaterialId:'TMP-BAG',materialName:'袋',materialRole:'PACKAGING',quantity:277,quantityUnit:'只'};
 const target={key:'new',sequence:2,sourceType:'EXTERNAL',materialName:'',materialRole:'AUXILIARY',weightKg:5};
 const plan={versionNo:0,status:'DRAFT',majorProcesses:[{key:'m',sequence:1,processName:'包装',yieldBasis:'NONE',inputs:[],outputs:[],steps:[{key:'s',sequence:1,stepName:'包装',stepType:'NORMAL',materials:[source,target],outputs:[],controlPoints:[]}]}]};
 let emitted:any;const app=renderer.createApp(Component,{modelValue:plan,majorKey:'m','onUpdate:modelValue':(p:any)=>emitted=p});app.provide(Symbol.for('v-scx'),{modules:new Set()});const child:any=app.mount(make());await flush();
 child.$.setupState.reuseTemporary(child.$.setupState.selectedStep.materials[1],'TMP-BAG');await flush();
 const row=emitted.majorProcesses[0].steps[0].materials[1];assert.equal(row.materialRole,'PACKAGING');assert.equal(row.quantityUnit,'只');assert.equal(row.weightKg,undefined);assert.equal(row.quantity,undefined);app.unmount();
});

test('minor workspace inherits unchanged input and clears external identity when selecting prior output',async()=>{
  const {default:Component}=await server.ssrLoadModule('/src/components/process/MinorStepWorkspace.vue'); Component.render=()=>null;
  const {aggregateProcessRecipe}=await server.ssrLoadModule('/../../packages/shared/src/process-plan.ts');
  const input={key:'input',sequence:1,sourceType:'STEP_OUTPUT',sourceStepOutputId:'upstream',materialName:'牛肉',materialState:'SOLID',materialRole:'PRIMARY',weightKg:10,materialCode:'BEEF',formulaMaterialId:'old',catalogMaterialId:'MC1'};
  const step={key:'step',sequence:1,stepName:'领料',stepType:'NORMAL',materials:[input],outputs:[],controlPoints:[]};
  const plan={versionNo:0,status:'DRAFT',majorProcesses:[
    {key:'first',sequence:1,processName:'准备',steps:[{key:'producer',sequence:1,stepName:'准备',materials:[{key:'raw',sequence:1,sourceType:'EXTERNAL',materialRole:'PRIMARY',materialName:'牛肉',weightKg:10}],outputs:[{id:'upstream',key:'upstream',sequence:1,outputName:'牛肉',materialState:'SOLID',primaryOutput:true,continueFlow:true,weightKg:10}],controlPoints:[]}]},
    {key:'second',sequence:2,processName:'领料',steps:[step]}]};
  let emitted:any;
  const app=renderer.createApp(Component,{modelValue:plan,majorKey:'second','onUpdate:modelValue':(value:any)=>{emitted=value;}});
  app.provide(Symbol.for('v-scx'),{modules:new Set()}); const child:any=app.mount(make()); await flush();
  const setup=child.$.setupState;
  setup.applyOutputSource(setup.selectedStep.materials[0]); await flush();
  assert.equal(emitted.majorProcesses[1].steps[0].materials[0].materialCode,undefined);
  assert.equal(emitted.majorProcesses[1].steps[0].materials[0].catalogMaterialId,undefined);
  setup.inheritInput(setup.selectedStep.materials[0]); await flush();
  const output=emitted.majorProcesses[1].steps[0].outputs[0];
  assert.equal(output.outputName,'牛肉'); assert.equal(output.primaryOutput,true); assert.equal(output.weightKg,undefined);
  assert.equal(aggregateProcessRecipe(emitted)[0].weightKg,10);
  assert.equal(plan.majorProcesses[1].steps[0].outputs.length,0);
  app.unmount();
});

test("saved preview fences delayed checks to exact trial version and refuses unsaved data",async()=>{
  const {exportApi}=await server.ssrLoadModule("/src/services/processExportApi.ts");
  const {default:Component}=await server.ssrLoadModule("/src/components/process/ProcessExportPreview.vue"); Component.render=()=>null;
  let resolve!: (value:any)=>void; const pending=new Promise(done=>{resolve=done;}); let calls=0;
  exportApi.check=()=>{calls++;return pending;};
  const scope=ref({formId:"F",trialId:"A",versionNo:1,sourceLabel:"方案 A V1",disabled:false});
  let child:any; const app=renderer.createApp({setup:()=>()=>h(Component,{...scope.value,ref:(v:any)=>{child=v;}})});
  app.provide(Symbol.for("v-scx"),{modules:new Set()}); app.mount(make()); await flush();
  const setup=child.$.setupState; const request=setup.openPreview(); await flush(); assert.equal(calls,3);
  scope.value={...scope.value,trialId:"B",sourceLabel:"方案 B V2",versionNo:2}; await flush();
  resolve({ready:false,issues:[{code:"MISSING",path:"x",message:"A旧数据"}]}); await request; await flush();
  assert.deepEqual(Object.keys(setup.checks),[]); assert.equal(setup.open,false);
  scope.value={...scope.value,disabled:true}; await flush(); await setup.openPreview(); assert.equal(calls,3);
  app.unmount();
});

test("formal generation is blocked by type checks without hiding an older ready archive",async()=>{
  const {exportApi}=await server.ssrLoadModule("/src/services/processExportApi.ts");
  const {api}=await server.ssrLoadModule("/src/services/api.ts");
  const {default:Component}=await server.ssrLoadModule("/src/components/process/RndOutputCenter.vue"); Component.render=()=>null;
  exportApi.revisionCheck=async()=>({ready:false,issues:[{code:"MISSING",path:"x",message:"待补充"}]});
  api.task.getProcessArtifacts=async()=>[{id:"failed",artifactType:"FORMULA_XLSX",status:"FAILED"},{id:"archived",artifactType:"FORMULA_XLSX",status:"READY"}];
  let calls=0; api.task.generateProcessArtifact=async()=>{calls++;};
  const app=renderer.createApp(Component,{formId:"F",selectedRevisionId:"R",revisions:[{id:"R",revisionNo:1}]});
  app.provide(Symbol.for("v-scx"),{modules:new Set()}); const child:any=app.mount(make()); await flush();
  const setup=child.$.setupState; await setup.loadArtifacts();
  assert.equal(setup.readyArtifactFor("FORMULA_XLSX").id,"archived");
  await setup.generate("FORMULA_XLSX"); assert.equal(calls,0); app.unmount();
});
