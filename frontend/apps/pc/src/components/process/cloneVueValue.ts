import { toRaw } from "vue";

export function cloneVueValue<T>(value: T): T {
  return structuredClone(unwrapVueValue(value, new WeakMap<object, unknown>())) as T;
}

function unwrapVueValue(value: unknown, seen: WeakMap<object, unknown>): unknown {
  if (value === null || typeof value !== "object") return value;
  const raw = toRaw(value);
  const cached = seen.get(raw);
  if (cached) return cached;
  if (raw instanceof Date) return new Date(raw.getTime());
  if (raw instanceof RegExp) return new RegExp(raw.source, raw.flags);
  if (raw instanceof Map) {
    const copy = new Map<unknown, unknown>();
    seen.set(raw, copy);
    raw.forEach((item, key) => copy.set(unwrapVueValue(key, seen), unwrapVueValue(item, seen)));
    return copy;
  }
  if (raw instanceof Set) {
    const copy = new Set<unknown>();
    seen.set(raw, copy);
    raw.forEach(item => copy.add(unwrapVueValue(item, seen)));
    return copy;
  }
  if (Array.isArray(raw)) {
    const copy: unknown[] = [];
    seen.set(raw, copy);
    raw.forEach(item => copy.push(unwrapVueValue(item, seen)));
    return copy;
  }
  const copy: Record<string, unknown> = {};
  seen.set(raw, copy);
  for (const [key, item] of Object.entries(raw)) copy[key] = unwrapVueValue(item, seen);
  return copy;
}
