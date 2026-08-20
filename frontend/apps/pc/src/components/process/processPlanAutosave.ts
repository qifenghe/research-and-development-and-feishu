export type ProcessPlanSaveState = "idle" | "dirty" | "saving" | "saved" | "error";

export class ProcessPlanSaveCoordinator<T> {
  value!: T;
  state: ProcessPlanSaveState = "idle";
  private generation = 0;
  private persistedGeneration = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private inFlight: Promise<void> | undefined;
  private disposed = false;
  private readonly options: {
    debounceMs: number;
    isDraft: (value: T) => boolean;
    mergeAck: (local: T, acknowledgement: T) => T;
    save: (value: T) => Promise<T>;
    onChange?: (value: T, state: ProcessPlanSaveState) => void;
    onError?: (error: unknown) => void;
  };

  constructor(options: {
    debounceMs: number;
    isDraft: (value: T) => boolean;
    mergeAck: (local: T, acknowledgement: T) => T;
    save: (value: T) => Promise<T>;
    onChange?: (value: T, state: ProcessPlanSaveState) => void;
    onError?: (error: unknown) => void;
  }) {
    this.options = options;
  }

  hydrate(value: T) {
    this.cancelTimer();
    this.value = structuredClone(value);
    this.persistedGeneration = this.generation;
    this.state = "idle";
    this.publish();
  }

  edit(edit: (current: T) => T) {
    if (this.disposed || !this.options.isDraft(this.value)) return;
    this.value = edit(structuredClone(this.value));
    this.generation++;
    this.state = "dirty";
    this.publish();
    this.cancelTimer();
    this.timer = setTimeout(() => void this.flush(), this.options.debounceMs);
  }

  async flush(): Promise<void> {
    this.cancelTimer();
    if (this.disposed || !this.options.isDraft(this.value) || this.generation === this.persistedGeneration) return;
    if (this.inFlight) return this.inFlight;
    this.inFlight = this.saveUntilCurrent();
    try {
      await this.inFlight;
    } finally {
      this.inFlight = undefined;
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
      const snapshot = structuredClone(this.value);
      this.state = "saving";
      this.publish();
      try {
        const acknowledgement = await this.options.save(snapshot);
        if (this.disposed) return;
        this.persistedGeneration = saveGeneration;
        this.value = this.options.mergeAck(this.value, acknowledgement);
        this.state = this.generation === saveGeneration ? "saved" : "dirty";
        this.publish();
      } catch (error) {
        if (!this.disposed) {
          this.state = "error";
          this.publish();
          this.options.onError?.(error);
        }
        return;
      }
    }
  }

  private cancelTimer() {
    if (this.timer) clearTimeout(this.timer);
    this.timer = undefined;
  }

  private publish() {
    this.options.onChange?.(structuredClone(this.value), this.state);
  }
}
