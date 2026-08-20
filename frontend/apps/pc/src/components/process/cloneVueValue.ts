import { toRaw } from "vue";

export function cloneVueValue<T>(value: T): T {
  return structuredClone(toRaw(value));
}
