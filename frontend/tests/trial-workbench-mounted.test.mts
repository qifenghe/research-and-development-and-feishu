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
