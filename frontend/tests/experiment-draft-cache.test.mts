import assert from "node:assert/strict";
import test from "node:test";

import {
  clearExperimentDraft,
  experimentDraftKey,
  readExperimentDraft,
  writeExperimentDraft,
} from "../packages/shared/src/experiment/draft-cache.ts";

class MemoryStorage {
  private values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

test("isolates experiment drafts by user and task", () => {
  assert.notEqual(experimentDraftKey("U1", "TASK-1"), experimentDraftKey("U2", "TASK-1"));
  assert.notEqual(experimentDraftKey("U1", "TASK-1"), experimentDraftKey("U1", "TASK-2"));
});

test("writes reads and clears a versioned experiment draft", () => {
  const storage = new MemoryStorage();
  const key = experimentDraftKey("U1", "TASK-1");
  writeExperimentDraft(storage, key, { summary: "微辣" }, "2026-07-13T10:00:00.000Z");
  assert.deepEqual(readExperimentDraft<{ summary: string }>(storage, key), {
    version: 1,
    savedAt: "2026-07-13T10:00:00.000Z",
    value: { summary: "微辣" },
  });
  clearExperimentDraft(storage, key);
  assert.equal(readExperimentDraft(storage, key), null);
});

test("ignores malformed cached drafts", () => {
  const storage = new MemoryStorage();
  storage.setItem("broken", "not-json");
  assert.equal(readExperimentDraft(storage, "broken"), null);
});
