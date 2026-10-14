import "./mobile-dom-bootstrap.mts";
import assert from "node:assert/strict";
import test from "node:test";
import { dirname } from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { createRenderer, nextTick } from "../apps/pc/node_modules/vue/index.mjs";
import { createMemoryHistory, createRouter, RouterView } from "../apps/pc/node_modules/vue-router/dist/vue-router.mjs";
import { createServer, type ViteDevServer } from "../apps/pc/node_modules/vite/dist/node/index.js";
import { createEmptyProcessPlan } from "../packages/shared/src/process-plan.ts";

class MemoryStorage implements Storage {
  readonly #values = new Map<string, string>();
  get length() { return this.#values.size; }
  clear() { this.#values.clear(); }
  getItem(key: string) { return this.#values.get(key) ?? null; }
  key(index: number) { return [...this.#values.keys()][index] ?? null; }
  removeItem(key: string) { this.#values.delete(key); }
  setItem(key: string, value: string) { this.#values.set(key, String(value)); }
}

type HostNode = { parent: HostNode | null; children: HostNode[]; props: Record<string, unknown>; text?: string };
const flush = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick(); };
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: Error) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; }
function trial(id: "trial-a" | "trial-b") {
  return {
    id,
    experimentFormId: "form-1",
    versionNo: 1,
    name: id === "trial-a" ? "方案 A" : "方案 B",
    sourceTrialId: null,
    archived: false,
    purpose: null,
    variables: null,
    conclusion: "PENDING",
    recommendationReason: null,
    qualityScore: null,
    qualityNotes: null,
    difficulty: null,
    plan: createEmptyProcessPlan(),
    plannedData: { materialWeightsKg: {}, stepParameters: {}, majorYieldTargets: {}, batchYieldTarget: null, yieldBasisNote: null },
    inheritedActuals: false,
    majorOrigins: {},
    createdBy: "engineer-1",
    createdAt: "2026-09-15T10:00:00",
    updatedBy: "engineer-1",
    updatedAt: "2026-09-15T10:00:00",
  };
}

let server: ViteDevServer;
test("material forms hide internal formula identity while retaining it in edited payloads", async () => {
  const plan: any = observedPlan();
  plan.majorProcesses[0].steps[0].materials[0].formulaMaterialId = "internal-formula-42";
  const require = createRequire(new URL("../apps/pc/package.json", import.meta.url));
  const { renderToString } = require("vue/server-renderer");
  const { createSSRApp, h } = require("vue");
  for (const [path, props] of [
    ["/src/components/process/MinorStepWorkspace.vue", { modelValue: plan, majorKey: "m" }],
    ["/src/components/process/ProcessPlanSnapshot.vue", { plan }],
  ] as const) {
    const component = (await server.ssrLoadModule(path)).default;
    const app = createSSRApp(component, props);
    app.component("a-input", { props: ["value", "placeholder"], render() { return h("input", { value: this.value, placeholder: this.placeholder }); } });
    app.config.warnHandler = () => {};
    const html = await renderToString(app);
    assert.doesNotMatch(html, /配方物料 ID|物料\/配方 ID|internal-formula-42/);
    assert.match(html, /牛肉/);
  }
  let saved: any;
  const { app, setup } = await mountSetup("/src/components/process/MinorStepWorkspace.vue", {
    modelValue: plan, majorKey: "m", "onUpdate:modelValue": (value: any) => { saved = value; },
  });
  try {
    setup.selectedStep.materials[0].weightKg = 12;
    setup.publishLocal();
    await flush();
    assert.equal(saved.majorProcesses[0].steps[0].materials[0].formulaMaterialId, "internal-formula-42");
    assert.equal(saved.majorProcesses[0].steps[0].materials[0].weightKg, 12);
  } finally { app.unmount(); }
});
test("readonly snapshot exposes every recorded parameter and material addition detail", async () => {
  const plan:any=observedPlan();
  plan.majorProcesses[0].steps[0].parameters=Array.from({length:4},(_,i)=>({key:`p${i}`,name:`参数${i}`,valueType:"NUMBER",unit:"min",actualValue:String(i),showInSummary:false}));
  const {app,setup}=await mountSetup("/src/components/process/ProcessPlanSnapshot.vue",{plan});
  try {
    assert.equal(setup.parameters(plan.majorProcesses[0].steps[0]).length,4);
    assert.equal(setup.parameters(plan.majorProcesses[0].steps[0])[3].actualValue,"3");
  } finally {app.unmount();}
});
test("parameter editor adds edits reorders confirms deletion and refuses readonly mutation", async () => {
  let value: any[] = [];
  const props: any = { modelValue:value, "onUpdate:modelValue": (next:any[]) => { value=next; app._instance.props.modelValue=next; } };
  const {app,setup}=await mountSetup("/src/components/process/StepParameterEditor.vue",props);
  try {
    for (const name of ["转速","压力","运行时间","暂停时间"]) { setup.addParameter(name); await flush(); }
    assert.deepEqual(value.map(p=>p.name),["转速","压力","运行时间","暂停时间"]);
    const key=value[1].key;
    setup.updateParameter(key,{actualValue:"-0.08",measurementBasis:"GAUGE"}); await flush();
    assert.equal(value[1].measurementBasis,"GAUGE");
    setup.moveParameter(key,-1); await flush(); assert.equal(value[0].key,key);
    await setup.removeParameter(key,async()=>false); await flush(); assert.equal(value.length,4);
    await setup.removeParameter(key,async()=>true); await flush(); assert.equal(value.length,3);
    app._instance.props.readonly=true; await flush();
    setup.addParameter("不能添加"); setup.updateParameter(value[0].key,{actualValue:"9"});
    await setup.removeParameter(value[0].key,async()=>true); await flush(); assert.equal(value.length,3); assert.equal(value[0].actualValue,undefined);
    app._instance.props.readonly=false; await flush();
    setup.appendTemplate("滚筒转速"); await flush(); setup.appendTemplate("滚筒转速"); await flush();
    assert.equal(value.length,5); assert.notEqual(value[3].key,value[4].key); assert.equal(value[4].actualValue,undefined);
  } finally { app.unmount(); }
});
test("step template drop appends repeated independent steps and selects each newly added step", async () => {
  let plan: any = observedPlan();
  plan.majorProcesses[0].steps = [];
  const props: any = { modelValue: plan, majorKey: "m", "onUpdate:modelValue": (next: any) => { plan = next; app._instance.props.modelValue = next; } };
  const { app, setup } = await mountSetup("/src/components/process/MinorStepWorkspace.vue", props);
  const event = { dataTransfer: { getData: () => JSON.stringify({ kind: "template", name: "煮制" }) } };
  try {
    for (let i = 0; i < 2; i++) {
      await setup.dropAt(event, i); await flush();
      assert.equal(plan.majorProcesses[0].steps.length, i + 1);
      assert.equal(setup.selectedStepKey, plan.majorProcesses[0].steps[i].key);
    }
    assert.notEqual(plan.majorProcesses[0].steps[0].key, plan.majorProcesses[0].steps[1].key);
    assert.deepEqual(plan.majorProcesses[0].steps.map((s: any) => s.stepName), ["煮制", "煮制"]);
    assert.deepEqual(plan.majorProcesses[0].steps.map((s: any) => s.sequence), [1, 2]);
  } finally { app.unmount(); }
});

test("step drop ignores malformed data and readonly workspace rejects additions", async () => {
  for (const readonly of [false, true]) {
    let emissions = 0;
    const { app, setup } = await mountSetup("/src/components/process/MinorStepWorkspace.vue", {
      modelValue: observedPlan(), majorKey: "m", readonly, "onUpdate:modelValue": () => { emissions++; },
    });
    try {
      await setup.dropAt({ dataTransfer: { getData: () => '{"kind":"template","name":42}' } }, 1);
      await flush();
      assert.equal(emissions, 0);
      if (readonly) { await setup.addStep("煮制"); await flush(); assert.equal(emissions, 0); }
    } finally { app.unmount(); }
  }
});

test('trial publishes current editable source and keeps finished quantities scoped to selected trial',async()=>{
 let selected:any;const {app,setup}=await mountSetup('/src/components/trials/TrialWorkbench.vue',{userId:'u',formalPlan:createEmptyProcessPlan(),'onSource-change':(v:any)=>{selected=v;}});
 try{
  setup.currentTrial={...trial('trial-a'),plan:observedPlan(138.5)};await flush();
  assert.equal(selected?.id,'trial-a');assert.equal(selected.plan.majorProcesses[0].steps[0].materials[0].weightKg,138.5);
  assert.equal(typeof setup.updateFinishedOutput,'function');setup.updateFinishedOutput({quantity:277,unit:'袋',remark:'本方案'});await flush();
  assert.equal(selected.finishedOutput.quantity,277);
  setup.currentTrial={...trial('trial-b'),plan:createEmptyProcessPlan()};await flush();
  assert.equal(selected.id,'trial-b');assert.equal(selected.finishedOutput?.quantity,undefined);
 }finally{app.unmount();}
});
test('new and copied schemes suggest next alphabetic name including archives',async()=>{
 const {app,setup}=await mountSetup('/src/components/trials/TrialWorkbench.vue',{userId:'u',formalPlan:createEmptyProcessPlan()});
 try{setup.trials=[{...trial('trial-a'),name:'方案 Z',archived:true}];setup.currentTrial=trial('trial-b');setup.newTrialOpen=true;await flush();assert.equal(setup.newTrialName,'方案 AA');setup.openCopy();assert.equal(setup.copyName,'方案 AA');}finally{app.unmount();}
});
test('unified entry initializes an empty trial list instead of leaving an unused formal editor',async()=>{
 const api=(await server.ssrLoadModule('/src/services/trialApi.ts')).trialApi;
 const original={list:api.list,initialize:api.initialize,find:api.find};let initialized=0;
 api.list=async()=>[];api.initialize=async(id:string)=>{assert.equal(id,'FORM-UNIFIED');initialized++;return trial('trial-initial');};api.find=async()=>trial('trial-initial');
 const {app,setup}=await mountSetup('/src/components/trials/TrialWorkbench.vue',{formId:'FORM-UNIFIED',userId:'u',formalPlan:createEmptyProcessPlan(),unifiedEntry:true});
 try{await flush();assert.equal(initialized,1);assert.equal(setup.currentTrial.id,'trial-initial');}finally{app.unmount();Object.assign(api,original);}
});
async function mountSetup(path: string, props: any) {
  const component = (await server.ssrLoadModule(path)).default;
  component.render = () => null;
  const renderer = createRenderer<HostNode, HostNode>({
    patchProp(el, key, _old, next) { el.props[key] = next; }, insert(child, parent) { child.parent = parent; parent.children.push(child); },
    remove() {}, createElement() { return { parent: null, children: [], props: {} }; },
    createText(text) { return { parent: null, children: [], props: {}, text }; }, createComment(text) { return { parent: null, children: [], props: {}, text }; },
    setText(node, text) { node.text = text; }, setElementText(node, text) { node.text = text; }, parentNode(node) { return node.parent; }, nextSibling() { return null; }, setScopeId() {},
    insertStaticContent() { const node: HostNode = { parent: null, children: [], props: {} }; return [node, node]; },
  });
  const app = renderer.createApp(component, props); app.provide(Symbol.for("v-scx"), { modules: new Set<string>() });
  if(path.includes('ExperimentFormView')) {
    const pinia=await server.ssrLoadModule('pinia');
    const instance=pinia.createPinia();pinia.setActivePinia(instance);app.use(instance);
    const auth=await server.ssrLoadModule('/src/stores/auth.ts');auth.useAuthStore(instance);
    const router=createRouter({history:createMemoryHistory(),routes:[{path:'/tasks/:id',component:{render:()=>null}}]});
    await router.push('/tasks/TEST-PROMOTION');await router.isReady();app.use(router);
  }
  const instance = app.mount({ parent: null, children: [], props: {} }) as any;
  await flush(); return { app, setup: instance.$.setupState };
}
function observedPlan(weight: number | undefined = 10) {
  return { ...createEmptyProcessPlan(), majorProcesses: [{ id: "m", key: "m", sequence: 1, processName: "热加工", yieldBasis: "PRIMARY_INPUT", inputs: [], outputs: [], steps: [{ id: "s", key: "s", sequence: 1, stepName: "煮制", stepType: "NORMAL", parameter1Name: "温度", parameter1Unit: "℃", parameter1Value: "97", materials: [{ id: "mat", key: "mat", sequence: 1, materialRole: "PRIMARY", sourceType: "EXTERNAL", materialName: "牛肉", weightKg: weight }], outputs: [{ id: "out", key: "out", sequence: 1, primaryOutput: true, continueFlow: false, outputType: "FINISHED", weightKg: weight }], controlPoints: [] }] }] };
}
test('promoted finished records replace old form values before pending autosave and survive reload',async()=>{
 localStorage.clear();
 const api=(await server.ssrLoadModule('/src/services/api.ts')).api;
 let record:any={id:'form-1',finishedOutputQuantity:100,finishedOutputUnit:'袋',materials:[],processSteps:[]};
 api.task.detail=async()=>({task:{status:'SAMPLING',productName:'回归',versionCode:'A0'},currentExperimentForm:{...record}});
 api.task.getProcessPlan=async()=>observedPlan(50);
 const writes:any[]=[];api.task.saveExperimentDraft=async(_id:string,c:any)=>{writes.push(c);record={...record,...c};return record;};
 const {app,setup}=await mountSetup('/src/views/rnd/ExperimentFormView.vue',{});
 try {
  setup.auth.principal={role:'RND_DIRECTOR',name:'总监'};await flush();
  setup.form.finishedOutputQuantity=101;await flush();
  record={...record,finishedOutputQuantity:277,finishedOutputUnit:'袋',finishedOutputWeightKg:138.5};
  await setup.onTrialPromoted({snapshot:observedPlan(138.5)},{quantity:277,unit:'袋',remark:'提升'});
  await new Promise(r=>setTimeout(r,1600));
  assert.equal(setup.form.finishedOutputQuantity,277);assert.equal(record.finishedOutputQuantity,277);
  assert.ok(writes.every(c=>c.finishedOutputQuantity===277));
  await setup.autoSave();assert.equal(writes.at(-1).finishedOutputQuantity,277);
  const reloaded=await api.task.detail();assert.equal(reloaded.currentExperimentForm.finishedOutputQuantity,277);
 }finally{app.unmount();}
});
test('newly created form switches source to trials and failed trial flush marks parent save as error',async()=>{
 localStorage.clear();const api=(await server.ssrLoadModule('/src/services/api.ts')).api;
 api.task.detail=async()=>({task:{status:'SAMPLING',productName:'初始任务',versionCode:'A0'},version:{specification:''}});
 api.sample.processSteps=async()=>[];
 api.task.saveExperimentDraft=async()=>({id:'NEW-FORM',status:'DRAFT',materials:[],processSteps:[]});
 const {app,setup}=await mountSetup('/src/views/rnd/ExperimentFormView.vue',{});
 try{
  setup.auth.principal={role:'RND_DIRECTOR',name:'总监'};await flush();
  assert.equal(setup.workspaceMode,'formal');await setup.saveDraft({silent:true});await flush();assert.equal(setup.workspaceMode,'trials');
  setup.selectedTrial=trial('trial-a');setup.trialWorkbench={flushSave:async()=>false};
  assert.equal(await setup.saveDraft({silent:true}),false);assert.equal(setup.draftSyncState,'error');
 }finally{app.unmount();}
});

test("graph replacement prunes deleted planned keys before saving material step and major deletions", async () => {
  // Regression: displayed kg quantity must match the quantity sent to the server.
  {
    localStorage.clear();
    const value={...trial("trial-a"),plan:observedPlan(138.5),finishedOutput:{quantity:138.5,unit:"kg",remark:"公斤"}};
    const api=(await server.ssrLoadModule("/src/services/trialApi.ts")).trialApi;
    api.list=async()=>[value];api.find=async()=>value;
    let saved:any;api.save=async(_f:string,_t:string,c:any)=>{saved=c;return {...value,...c};};
    const {app,setup}=await mountSetup("/src/components/trials/TrialWorkbench.vue",{formId:"form-1",userId:"engineer-1",formalPlan:createEmptyProcessPlan()});
    try {setup.replacePlan(observedPlan(130));await setup.saveTrial();assert.equal(saved.finishedOutput.quantity,130);}
    finally {app.unmount();}
  }
  for (const deletion of ["material", "step", "major"]) {
    localStorage.clear();
    const value = { ...trial("trial-a"), plan: observedPlan(), plannedData: { materialWeightsKg: { mat: 10 }, stepParameters: { s: { parameter1Value: "95", parameter2Value: null } }, majorYieldTargets: { m: 80 }, batchYieldTarget: 72, yieldBasisNote: "主料" } };
    const api = (await server.ssrLoadModule("/src/services/trialApi.ts")).trialApi;
    api.list = async () => [value]; api.find = async () => value;
    let saved: any;
    api.save = async (_f: string, _t: string, command: any) => { saved = command; return { ...value, ...command }; };
    const { app, setup } = await mountSetup("/src/components/trials/TrialWorkbench.vue", { formId: "form-1", userId: "engineer-1", formalPlan: createEmptyProcessPlan() });
    const next = structuredClone(value.plan);
    if (deletion === "material") next.majorProcesses[0]!.steps[0]!.materials = [];
    else if (deletion === "step") next.majorProcesses[0]!.steps = [];
    else next.majorProcesses = [];
    setup.replacePlan(next); await setup.saveTrial();
    assert.deepEqual(saved.plannedData.materialWeightsKg, {});
    assert.deepEqual(saved.plannedData.stepParameters, {});
    assert.deepEqual(saved.plannedData.parameterValues, deletion === "material" ? {s:{"legacy-1":"95"}} : {});
    assert.deepEqual(saved.plannedData.majorYieldTargets, deletion === "major" ? {} : { m: 80 });
    assert.equal(saved.plannedData.batchYieldTarget, 72); assert.equal(saved.plannedData.yieldBasisNote, "主料");
    if (deletion === "material") assert.match(setup.actualAndDifference("parameter:s:1"), /\+2℃/);
    app.unmount();
  }
});

test("actual summaries preserve missing partial zero and tiny observations", async () => {
  for (const weight of [undefined, 0, 0.0001]) {
    const plan = observedPlan(); plan.majorProcesses[0]!.steps[0]!.materials[0]!.weightKg = weight; plan.majorProcesses[0]!.steps[0]!.outputs[0]!.weightKg = weight;
    const board = await mountSetup("/src/components/process/MajorProcessBoard.vue", { modelValue: plan });
    const summary = board.setup.yieldOf(plan.majorProcesses[0]);
    assert.equal(summary.primaryInputWeightKg, weight ?? null); assert.equal(summary.qualifiedOutputWeightKg, weight ?? null);
    assert.equal(summary.balanceDifferenceKg, weight == null ? null : 0);
    assert.equal(board.setup.kg(weight ?? null), weight == null ? "待补充" : `${weight}kg`);
    const workspace = await mountSetup("/src/components/process/MinorStepWorkspace.vue", { modelValue: plan, majorKey: "m" });
    assert.equal(workspace.setup.recipeTotal, weight ?? null);
    board.app.unmount(); workspace.app.unmount();
  }
  const plan = observedPlan(); plan.majorProcesses[0]!.steps[0]!.materials.push({ ...plan.majorProcesses[0]!.steps[0]!.materials[0]!, id: "aux", key: "aux", materialRole: "AUXILIARY", weightKg: undefined });
  const workspace = await mountSetup("/src/components/process/MinorStepWorkspace.vue", { modelValue: plan, majorKey: "m" });
  assert.equal(workspace.setup.recipeTotal, null); workspace.app.unmount();
});
const require = createRequire(import.meta.url);
test.before(async () => {
  Object.assign(globalThis, { localStorage: new MemoryStorage() });
  Object.assign(globalThis, { getComputedStyle: () => ({ getPropertyValue: () => "" }) });
  Object.assign(globalThis.document, { getElementsByTagName: () => [] });
  const configFile = fileURLToPath(new URL("../apps/pc/vite.config.ts", import.meta.url));
  server = await createServer({ root: dirname(configFile), configFile, server: { middlewareMode: true }, appType: "custom", logLevel: "silent" });
  const ant = require("../apps/pc/node_modules/ant-design-vue");
  ant.message.error = () => undefined;
  ant.message.success = () => undefined;
});
test.after(async () => { await server.close(); });

test("mounted workbench restores server A before a discarded switch whose B load fails", async () => {
  localStorage.clear();
  const apiModule = await server.ssrLoadModule("/src/services/trialApi.ts");
  const componentModule = await server.ssrLoadModule("/src/components/trials/TrialWorkbench.vue");
  const trialApi = apiModule.trialApi as any;
  const trialB = deferred<ReturnType<typeof trial>>();
  const reads: string[] = [];
  trialApi.list = async () => [trial("trial-a"), trial("trial-b")];
  trialApi.find = async (_formId: string, trialId: "trial-a" | "trial-b") => {
    reads.push(trialId);
    return trialId === "trial-a" ? trial("trial-a") : trialB.promise;
  };
  componentModule.default.render = () => null;

  const renderer = createRenderer<HostNode, HostNode>({
    patchProp(el, key, _old, next) { el.props[key] = next; },
    insert(child, parent, anchor) { child.parent = parent; const index = anchor ? parent.children.indexOf(anchor) : -1; if (index < 0) parent.children.push(child); else parent.children.splice(index, 0, child); },
    remove(child) { if (child.parent) { const index = child.parent.children.indexOf(child); if (index >= 0) child.parent.children.splice(index, 1); } },
    createElement() { return { parent: null, children: [], props: {} }; },
    createText(text) { return { parent: null, children: [], props: {}, text }; },
    createComment(text) { return { parent: null, children: [], props: {}, text }; },
    setText(node, text) { node.text = text; },
    setElementText(node, text) { node.text = text; },
    parentNode(node) { return node.parent; },
    nextSibling(node) { return node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null; },
    querySelector() { return null; },
    setScopeId() {},
    insertStaticContent() { const node: HostNode = { parent: null, children: [], props: {} }; return [node, node]; },
  });
  const root: HostNode = { parent: null, children: [], props: {} };
  const app = renderer.createApp(componentModule.default, { formId: "form-1", userId: "engineer-1", formalPlan: createEmptyProcessPlan() });
  app.provide(Symbol.for("v-scx"), { modules: new Set<string>() });
  const instance = app.mount(root) as any;
  const setup = instance.$.setupState;
  await flush();
  assert.equal(setup.currentTrial.id, "trial-a");

  setup.currentTrial.name = "未保存的 A";
  setup.markDirty();
  const switching = setup.switchTrial("trial-b");
  await flush();
  assert.deepEqual(reads, ["trial-a"], "B must not load before the unsaved choice is resolved");
  assert.equal(setup.switchModalOpen, true);

  setup.discardAndResolve();
  await flush();
  assert.deepEqual(reads, ["trial-a", "trial-b"]);
  assert.equal(setup.currentTrial.name, "方案 A", "discard restores the persisted server snapshot before loading B");
  trialB.reject(new Error("B load failed"));
  await switching;
  await flush();
  assert.equal(setup.currentTrial.id, "trial-a");
  assert.equal(setup.currentTrial.name, "方案 A");
  assert.equal(setup.dirty, false);
  app.unmount();
});

test("mounted workbench does not create a new scheme until dirty changes are explicitly resolved", async () => {
  localStorage.clear();
  const apiModule = await server.ssrLoadModule("/src/services/trialApi.ts");
  const componentModule = await server.ssrLoadModule("/src/components/trials/TrialWorkbench.vue");
  const trialApi = apiModule.trialApi as any;
  let creates = 0;
  trialApi.list = async () => [trial("trial-a")];
  trialApi.find = async () => trial("trial-a");
  trialApi.create = async () => { creates++; return trial("trial-b"); };
  componentModule.default.render = () => null;
  const renderer = createRenderer<HostNode, HostNode>({
    patchProp(el, key, _old, next) { el.props[key] = next; }, insert(child, parent) { child.parent = parent; parent.children.push(child); },
    remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); }, createElement() { return { parent: null, children: [], props: {} }; },
    createText(text) { return { parent: null, children: [], props: {}, text }; }, createComment(text) { return { parent: null, children: [], props: {}, text }; },
    setText(node, text) { node.text = text; }, setElementText(node, text) { node.text = text; }, parentNode(node) { return node.parent; }, nextSibling() { return null; }, querySelector() { return null; }, setScopeId() {},
    insertStaticContent() { const node: HostNode = { parent: null, children: [], props: {} }; return [node, node]; },
  });
  const app = renderer.createApp(componentModule.default, { formId: "form-1", userId: "engineer-1", formalPlan: createEmptyProcessPlan() });
  app.provide(Symbol.for("v-scx"), { modules: new Set<string>() });
  const instance = app.mount({ parent: null, children: [], props: {} }) as any;
  const setup = instance.$.setupState;
  await flush();
  setup.currentTrial.name = "未保存的 A";
  setup.markDirty();
  setup.newTrialName = "方案 B";
  const creating = setup.createTrial();
  await flush();
  assert.equal(creates, 0);
  assert.equal(setup.switchModalOpen, true);
  setup.discardAndResolve();
  await creating;
  await flush();
  assert.equal(creates, 1);
  assert.equal(setup.currentTrial.id, "trial-b");
  app.unmount();
});

test("same-component route parameter update is cancelled while dirty until the user chooses", async () => {
  localStorage.clear();
  const apiModule = await server.ssrLoadModule("/src/services/trialApi.ts");
  const componentModule = await server.ssrLoadModule("/src/components/trials/TrialWorkbench.vue");
  const trialApi = apiModule.trialApi as any;
  trialApi.list = async (formId: string) => [{ ...trial("trial-a"), experimentFormId: formId }];
  trialApi.find = async (formId: string) => ({ ...trial("trial-a"), experimentFormId: formId });
  componentModule.default.render = () => null;
  const renderer = createRenderer<HostNode, HostNode>({
    patchProp(el, key, _old, next) { el.props[key] = next; }, insert(child, parent) { child.parent = parent; parent.children.push(child); },
    remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); }, createElement() { return { parent: null, children: [], props: {} }; },
    createText(text) { return { parent: null, children: [], props: {}, text }; }, createComment(text) { return { parent: null, children: [], props: {}, text }; },
    setText(node, text) { node.text = text; }, setElementText(node, text) { node.text = text; }, parentNode(node) { return node.parent; }, nextSibling() { return null; }, querySelector() { return null; }, setScopeId() {},
    insertStaticContent() { const node: HostNode = { parent: null, children: [], props: {} }; return [node, node]; },
  });
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/forms/:formId", component: componentModule.default, props: route => ({ formId: String(route.params.formId), userId: "engineer-1", formalPlan: createEmptyProcessPlan() }) }] });
  await router.push("/forms/form-1");
  await router.isReady();
  let setup: any;
  const app = renderer.createApp(RouterView);
  app.mixin({ mounted() { if ((this as any).$?.type === componentModule.default) setup = (this as any).$?.setupState; } });
  app.use(router);
  app.provide(Symbol.for("v-scx"), { modules: new Set<string>() });
  app.mount({ parent: null, children: [], props: {} });
  await flush();
  setup.currentTrial.name = "未保存的 A";
  setup.markDirty();
  const navigation = router.push("/forms/form-2");
  await new Promise(resolve => setTimeout(resolve, 0));
  await flush();
  assert.equal(router.currentRoute.value.params.formId, "form-1");
  assert.equal(setup.switchModalOpen, true);
  setup.resolveUnsaved(false);
  await navigation;
  assert.equal(router.currentRoute.value.params.formId, "form-1");
  app.unmount();
});

test("mounted formal workspace keeps promoted server hydration when an older save acknowledges late", async () => {
  const apiModule = await server.ssrLoadModule("/src/services/api.ts");
  const componentModule = await server.ssrLoadModule("/src/components/process/ProcessPlanWorkspace.vue");
  const save = deferred<any>();
  apiModule.api.task.getProcessRevisions = async () => [];
  apiModule.api.task.saveProcessPlan = async () => save.promise;
  componentModule.default.render = () => null;
  const renderer = createRenderer<HostNode, HostNode>({
    patchProp(el, key, _old, next) { el.props[key] = next; }, insert(child, parent) { child.parent = parent; parent.children.push(child); },
    remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1); }, createElement() { return { parent: null, children: [], props: {} }; },
    createText(text) { return { parent: null, children: [], props: {}, text }; }, createComment(text) { return { parent: null, children: [], props: {}, text }; },
    setText(node, text) { node.text = text; }, setElementText(node, text) { node.text = text; }, parentNode(node) { return node.parent; }, nextSibling() { return null; }, querySelector() { return null; }, setScopeId() {},
    insertStaticContent() { const node: HostNode = { parent: null, children: [], props: {} }; return [node, node]; },
  });
  const base = { ...createEmptyProcessPlan(), versionNo: 1, status: "DRAFT" as const };
  const app = renderer.createApp(componentModule.default, { formId: "form-1", modelValue: base });
  app.provide(Symbol.for("v-scx"), { modules: new Set<string>() });
  const instance = app.mount({ parent: null, children: [], props: {} }) as any;
  const setup = instance.$.setupState;
  await flush();
  const pendingDraft = { ...base, majorProcesses: [{ key: "old", sequence: 1, processName: "旧草稿", yieldBasis: "NONE", inputs: [], outputs: [], steps: [] }] };
  setup.restoreLocalDirty(pendingDraft);
  const oldFlush = setup.saveNow(true);
  await flush();
  const promoted = { ...createEmptyProcessPlan(), versionNo: 3, status: "SUBMITTED" as const, majorProcesses: [{ key: "promoted", sequence: 1, processName: "正式提交图", yieldBasis: "NONE", inputs: [], outputs: [], steps: [] }] };
  setup.hydrateServer(promoted);
  save.resolve({ ...pendingDraft, versionNo: 2, status: "DRAFT" });
  await oldFlush;
  await flush();
  assert.equal(setup.plan.status, "SUBMITTED");
  assert.equal(setup.plan.versionNo, 3);
  assert.equal(setup.plan.majorProcesses[0].processName, "正式提交图");
  assert.equal(setup.saveState, "idle");
  app.unmount();
});
