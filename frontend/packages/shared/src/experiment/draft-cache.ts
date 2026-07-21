export interface DraftStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface CachedExperimentDraft<T> {
  version: 1;
  savedAt: string;
  value: T;
}

export function experimentDraftKey(userId: string, taskId: string) {
  return `rnd:experiment-draft:v1:${encodeURIComponent(userId)}:${encodeURIComponent(taskId)}`;
}

export function writeExperimentDraft<T>(
  storage: DraftStorage,
  key: string,
  value: T,
  savedAt = new Date().toISOString(),
) {
  const draft: CachedExperimentDraft<T> = { version: 1, savedAt, value };
  storage.setItem(key, JSON.stringify(draft));
  return draft;
}

export function readExperimentDraft<T>(storage: DraftStorage, key: string): CachedExperimentDraft<T> | null {
  const raw = storage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<CachedExperimentDraft<T>>;
    if (parsed.version !== 1 || typeof parsed.savedAt !== "string" || !("value" in parsed)) return null;
    return parsed as CachedExperimentDraft<T>;
  } catch {
    return null;
  }
}

export function clearExperimentDraft(storage: DraftStorage, key: string) {
  storage.removeItem(key);
}

export function isCachedDraftNewer(localSavedAt: string, serverSavedAt?: string | null) {
  if (!serverSavedAt) return true;
  return Date.parse(localSavedAt) > Date.parse(serverSavedAt);
}
