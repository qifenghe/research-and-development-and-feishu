const TOKEN_KEY = "rnd_access_token";
const USER_KEY = "rnd_user";
const memoryStorage = new Map<string, string>();

export function getAccessToken(): string | null {
  return safeGetItem(TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  safeSetItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  safeRemoveItem(TOKEN_KEY);
  safeRemoveItem(USER_KEY);
}

export function getStoredUser<T>(): T | null {
  const raw = safeGetItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function setStoredUser<T>(user: T): void {
  safeSetItem(USER_KEY, JSON.stringify(user));
}

function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key) ?? memoryStorage.get(key) ?? null;
  } catch {
    return memoryStorage.get(key) ?? null;
  }
}

function safeSetItem(key: string, value: string): void {
  memoryStorage.set(key, value);
  try {
    localStorage.setItem(key, value);
  } catch {
    // Some embedded WebViews can block localStorage. Keep the in-memory copy for the current session.
  }
}

function safeRemoveItem(key: string): void {
  memoryStorage.delete(key);
  try {
    localStorage.removeItem(key);
  } catch {
    // Ignore blocked storage cleanup in embedded WebViews.
  }
}
