function round(value: number, decimals: number) {
  if (!Number.isFinite(value)) return 0;
  const factor = 10 ** decimals;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

function safeWeight(value: number | null | undefined) {
  return Number.isFinite(value ?? Number.NaN) ? Number(value) : 0;
}

export type PricingPreviewInput = {
  primaryMaterialWeightKg: number | null | undefined;
  ingredientWeightKg: number | null | undefined;
  finishedOutputWeightKg: number | null | undefined;
  finishedOutputQuantity: number | null | undefined;
};

export type PricingPreview = {
  totalInputWeightKg: number;
  primaryMaterialYieldPercent: number;
  averageUnitWeightKg: number;
  referenceQuantity: number;
};

export function formulaRatios(weights: Array<number | null | undefined>) {
  const safeWeights = weights.map(safeWeight);
  const totalWeight = safeWeights.reduce((sum, weight) => sum + weight, 0);
  if (totalWeight <= 0) {
    return safeWeights.map(() => 0);
  }
  return safeWeights.map((weight) => round((weight / totalWeight) * 100, 6));
}

export function processLoss(input: number | null | undefined, output: number | null | undefined, residual = 0) {
  const inputWeight = safeWeight(input);
  const outputWeight = safeWeight(output);
  const residualWeight = safeWeight(residual);
  const lossWeightKg = inputWeight - outputWeight - residualWeight;
  if (lossWeightKg < 0) {
    throw new Error("出成与余料不能超过投入重量");
  }
  return {
    lossWeightKg: round(lossWeightKg, 4),
    lossRate: inputWeight > 0 ? round((lossWeightKg / inputWeight) * 100, 6) : 0,
  };
}

export function finishedYieldPercent(output: number | null | undefined, primaryInput: number | null | undefined) {
  const outputWeight = safeWeight(output);
  const primaryWeight = safeWeight(primaryInput);
  if (primaryWeight <= 0) {
    return 0;
  }
  return round((outputWeight / primaryWeight) * 100, 6);
}

export function averageUnitWeightKg(
  finishedOutputWeightKg: number | null | undefined,
  finishedOutputQuantity: number | null | undefined,
) {
  const outputWeight = safeWeight(finishedOutputWeightKg);
  const outputQuantity = safeWeight(finishedOutputQuantity);
  if (outputQuantity <= 0) {
    return 0;
  }
  return round(outputWeight / outputQuantity, 4);
}

export function referenceQuantity(finishedOutputQuantity: number | null | undefined) {
  return round(Math.max(0, safeWeight(finishedOutputQuantity)), 4);
}

export function normalizePositiveIntegerQuantity(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") return undefined;
  const quantity = typeof value === "string" ? Number(value) : value;
  return Number.isInteger(quantity) && quantity > 0 ? quantity : undefined;
}

export function calculatePricingPreview(input: PricingPreviewInput): PricingPreview {
  const primaryMaterialWeightKg = Math.max(0, safeWeight(input.primaryMaterialWeightKg));
  const ingredientWeightKg = Math.max(0, safeWeight(input.ingredientWeightKg));
  const finishedOutputWeightKg = Math.max(0, safeWeight(input.finishedOutputWeightKg));
  const finishedOutputQuantity = Math.max(0, safeWeight(input.finishedOutputQuantity));

  return {
    totalInputWeightKg: round(primaryMaterialWeightKg + ingredientWeightKg, 4),
    primaryMaterialYieldPercent: finishedYieldPercent(finishedOutputWeightKg, primaryMaterialWeightKg),
    averageUnitWeightKg: averageUnitWeightKg(finishedOutputWeightKg, finishedOutputQuantity),
    referenceQuantity: referenceQuantity(finishedOutputQuantity),
  };
}
