import { cloneVueValue } from "./cloneVueValue.ts";

export type ProcessPlanSaveState = "idle" | "dirty" | "saving" | "saved" | "error";

export class ProcessPlanSaveCoordinator<T> {
  value!: T;
  state: ProcessPlanSaveState = "idle";
  private generation = 0;
  private persistedGeneration = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private inFlight: Promise<void> | undefined;
  private disposed = false;
  private formId: string | undefined;
  private epoch = 0;
  private readonly options: {
    debounceMs: number;
    isDraft: (value: T) => boolean;
    mergeAck: (local: T, acknowledgement: T) => T;
    formId?: string;
    save: (formId: string, value: T) => Promise<T>;
    onChange?: (value: T, state: ProcessPlanSaveState) => void;
    onError?: (error: unknown) => void;
  };

  constructor(options: {
    debounceMs: number;
    isDraft: (value: T) => boolean;
    mergeAck: (local: T, acknowledgement: T) => T;
    formId?: string;
    save: (formId: string, value: T) => Promise<T>;
    onChange?: (value: T, state: ProcessPlanSaveState) => void;
    onError?: (error: unknown) => void;
  }) {
    this.options = options;
    this.formId = options.formId;
  }

  hydrate(value: T) {
    this.hydrateServer(value);
  }

  hydrateServer(value: T) {
    this.epoch++;
    this.cancelTimer();
    this.inFlight = undefined;
    this.value = cloneVueValue(value);
    this.persistedGeneration = this.generation;
    this.state = "idle";
    this.publish();
  }

  adoptServerDraft(value: T) {
    this.hydrateServer(value);
  }

  restoreLocalDirty(value: T) {
    this.cancelTimer();
    this.value = cloneVueValue(value);
    this.generation++;
    this.state = this.options.isDraft(this.value) ? "dirty" : "idle";
    this.publish();
  }

  rebind(formId: string | undefined, serverValue: T) {
    this.formId = formId;
    this.hydrateServer(serverValue);
  }

  edit(edit: (current: T) => T) {
    if (this.disposed || !this.options.isDraft(this.value)) return;
    this.value = edit(cloneVueValue(this.value));
    this.generation++;
    this.state = "dirty";
    this.publish();
    this.cancelTimer();
    if (this.formId) this.timer = setTimeout(() => void this.flush().catch(() => undefined), this.options.debounceMs);
  }

  async flush(): Promise<void> {
    this.cancelTimer();
    if (this.disposed || !this.formId || !this.options.isDraft(this.value) || this.generation === this.persistedGeneration) {
      if (!this.formId && this.generation !== this.persistedGeneration) throw new Error("PROCESS_PLAN_FORM_ID_REQUIRED");
      return;
    }
    if (this.inFlight) return this.inFlight;
    const task = this.saveUntilCurrent();
    this.inFlight = task;
    try {
      await task;
    } finally {
      if (this.inFlight === task) this.inFlight = undefined;
    }
  }

  dispose() {
    this.disposed = true;
    this.generation++;
    this.cancelTimer();
  }

  private async saveUntilCurrent() {
    while (!this.disposed && this.options.isDraft(this.value) && this.generation !== this.persistedGeneration) {
      const saveGeneration = this.generation;
      const snapshot = cloneVueValue(this.value);
      const saveFormId = this.formId;
      const saveEpoch = this.epoch;
      this.state = "saving";
      this.publish();
      try {
        const acknowledgement = await this.options.save(saveFormId!, snapshot);
        if (this.disposed || saveEpoch !== this.epoch || saveFormId !== this.formId) return;
        this.persistedGeneration = saveGeneration;
        this.value = this.options.mergeAck(this.value, acknowledgement);
        this.state = this.generation === saveGeneration ? "saved" : "dirty";
        this.publish();
      } catch (error) {
        if (this.disposed || saveEpoch !== this.epoch || saveFormId !== this.formId) return;
        if (!this.disposed) {
          this.state = "error";
          this.publish();
          this.options.onError?.(error);
        }
        throw error;
      }
    }
  }

  private cancelTimer() {
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
  }

  private publish() {
    this.options.onChange?.(cloneVueValue(this.value), this.state);
  }
}
