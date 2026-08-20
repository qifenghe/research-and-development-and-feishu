export class RequestGeneration {
  private value = 0;

  next() {
    this.value += 1;
    return this.value;
  }

  invalidate() {
    this.value += 1;
  }

  capture() {
    return this.value;
  }

  isCurrent(generation: number) {
    return generation === this.value;
  }
}
