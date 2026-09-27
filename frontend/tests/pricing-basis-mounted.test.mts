import "./mobile-dom-bootstrap.mts";
import assert from "node:assert/strict";
import test from "node:test";
import {dirname} from "node:path";
import {fileURLToPath} from "node:url";
import {createRenderer,nextTick} from "../apps/pc/node_modules/vue/index.mjs";
import {createServer} from "../apps/pc/node_modules/vite/dist/node/index.js";
const make=()=>({parent:null as any,children:[] as any[],props:{} as any,text:""});
const renderer=createRenderer<any,any>({patchProp(n,k,_o,v){n.props[k]=v;},insert(n,p){n.parent=p;p.children.push(n);},remove(){},createElement:make,createText(t){return {...make(),text:t};},createComment:make,setText(n,t){n.text=t;},setElementText(n,t){n.text=t;},parentNode(n){return n.parent;},nextSibling(){return null;},querySelector(){return null;},setScopeId(){},insertStaticContent(){return [make(),make()];}});
test("mounted editor restores material rows and switching weight basis clears stale confirmation",async()=>{
 Object.assign(globalThis.document,{getElementsByTagName:()=>[]});
 const configFile=fileURLToPath(new URL("../apps/pc/vite.config.ts",import.meta.url));
 const server=await createServer({root:dirname(configFile),configFile,server:{middlewareMode:true},appType:"custom",logLevel:"silent"});
 try {
  const {pricingBasisApi}=await server.ssrLoadModule("/src/services/pricingBasisApi.ts");
  const {default:Component}=await server.ssrLoadModule("/src/components/pricing/PricingBasisEditor.vue");Component.render=()=>null;
  const draft={schemaVersion:1,sourceRevisionId:"R",header:{productName:"验收"},materials:[{id:"salt-a",majorId:"a",materialName:"盐",weightBasis:"AFTER_PROCESS",usageKg:1,utilizationRate:.95,confirmationBasis:"旧依据",processedOutputId:"old"},{id:"salt-b",majorId:"b",materialName:"盐",usageKg:2}],packaging:[],actualBags:177};
  pricingBasisApi.load=async()=>({draft:structuredClone(draft),source:structuredClone(draft),version:2,calculation:{materials:[],issues:[],referenceBags:178},confirmedBy:null,stale:false});
  const app=renderer.createApp(Component,{pricingFileId:"P",editable:true});app.provide(Symbol.for("v-scx"),{modules:new Set()});
  const child:any=app.mount(make());await Promise.resolve();await nextTick();await Promise.resolve();await nextTick();
  const setup=child.$.setupState;
  assert.equal(setup.session.draft.materials.length,2);assert.equal(setup.session.draft.actualBags,177);assert.equal(setup.session.view.calculation.referenceBags,178);
  setup.setBasis(0,"BEFORE_PROCESS");assert.equal(setup.session.draft.materials[0].utilizationRate,null);assert.equal(setup.session.draft.materials[0].confirmationBasis,null);assert.equal(setup.session.draft.materials[0].processedOutputId,null);assert.equal(setup.session.dirty,true);
  pricingBasisApi.save=async()=>{throw new Error("核价草稿版本冲突");};await setup.save();assert.match(setup.error,/版本冲突/);assert.equal(setup.session.draft.materials[0].weightBasis,"BEFORE_PROCESS");app.unmount();
 } finally {await server.close();}
});
