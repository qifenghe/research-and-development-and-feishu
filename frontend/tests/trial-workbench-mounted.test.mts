import "./mobile-dom-bootstrap.mts";
import assert from "node:assert/strict";
import test from "node:test";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRenderer, nextTick } from "../apps/pc/node_modules/vue/index.mjs";
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
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
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
test.before(async () => {
  Object.assign(globalThis, { localStorage: new MemoryStorage() });
  Object.assign(globalThis.document, { getElementsByTagName: () => [] });
  const configFile = fileURLToPath(new URL("../apps/pc/vite.config.ts", import.meta.url));
  server = await createServer({ root: dirname(configFile), configFile, server: { middlewareMode: true }, appType: "custom", logLevel: "silent" });
});
test.after(async () => { await server.close(); });

test("mounted workbench waits for an explicit discard before switching an unsaved trial", async () => {
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

  setup.markDirty();
  const switching = setup.switchTrial("trial-b");
  await flush();
  assert.deepEqual(reads, ["trial-a"], "B must not load before the unsaved choice is resolved");
  assert.equal(setup.switchModalOpen, true);

  setup.discardAndResolve();
  await flush();
  assert.deepEqual(reads, ["trial-a", "trial-b"]);
  assert.equal(setup.currentTrial.id, "trial-a", "the delayed B acknowledgement has not arrived yet");
  trialB.resolve(trial("trial-b"));
  await switching;
  await flush();
  assert.equal(setup.currentTrial.id, "trial-b");
  assert.equal(setup.dirty, false);
  app.unmount();
});
