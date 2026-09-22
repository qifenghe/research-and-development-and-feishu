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
