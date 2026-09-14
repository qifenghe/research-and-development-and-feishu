import type { ProcessPlanDraft } from "@rnd/shared";

export interface TrialDraftEnvelope<T> {
  savedAt: string;
  value: T;
}

export interface TrialRequestToken {
  generation: number;
  formId: string;
  identity: string;
}

export class TrialRequestFence {
  private generation = 0;
  private formId = "";
  private identity = "";

  begin(formId: string, identity: string): TrialRequestToken {
    this.generation += 1;
    this.formId = formId;
    this.identity = identity;
    return { generation: this.generation, formId, identity };
  }

  invalidate() {
    this.generation += 1;
    this.formId = "";
    this.identity = "";
  }

  isCurrent(token: TrialRequestToken) {
    return token.generation === this.generation
      && token.formId === this.formId
      && token.identity === this.identity;
  }
}

export function trialDraftKey(userId: string, formId: string, trialId: string) {
  return `rnd:trial-draft:${encodeURIComponent(userId)}:${encodeURIComponent(formId)}:${encodeURIComponent(trialId)}`;
}

export function writeTrialDraft<T>(storage: Storage, key: string, value: T, savedAt = new Date().toISOString()) {
  storage.setItem(key, JSON.stringify({ savedAt, value } satisfies TrialDraftEnvelope<T>));
}

export function readTrialDraft<T>(storage: Storage, key: string): TrialDraftEnvelope<T> | null {
  const raw = storage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as TrialDraftEnvelope<T>;
    return typeof parsed.savedAt === "string" && parsed.value != null ? parsed : null;
  } catch {
    return null;
  }
}

export function clearTrialDraft(storage: Storage, key: string) {
  storage.removeItem(key);
}

export function normalizeTrialLocalDateTime(value?: string | null): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed) return undefined;
  if (/[zZ]$|[+-]\d{2}:\d{2}$/.test(trimmed)) {
    throw new Error("测量时间必须使用本地日期时间，不能包含时区或 Z");
  }
  const normalized = trimmed.replace(" ", "T");
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(normalized)) {
    throw new Error("测量时间必须使用本地日期时间");
  }
  return normalized;
}

function plainClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

/**
 * Gives every locally-created node an id before transport. The backend remaps
 * those ids and returns the authoritative graph, which callers must adopt.
 */
export function prepareTrialPlanForSave(value: ProcessPlanDraft): ProcessPlanDraft {
  const plan = plainClone(value);
  for (const major of plan.majorProcesses) {
    major.id ||= major.key;
    for (const input of major.inputs) input.id ||= input.key;
    for (const output of major.outputs) output.id ||= output.key;
    for (const step of major.steps) {
      step.id ||= step.key;
      for (const material of step.materials) material.id ||= material.key;
      for (const output of step.outputs || []) output.id ||= output.key;
      for (const point of step.controlPoints || []) {
        point.id ||= point.key;
        for (const measurement of point.measurements) {
          measurement.id ||= measurement.key;
          measurement.measuredAt = normalizeTrialLocalDateTime(measurement.measuredAt);
        }
      }
    }
  }
  return plan;
}
