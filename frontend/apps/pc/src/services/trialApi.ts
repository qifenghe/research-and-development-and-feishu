import type { ProcessPlanDraft, ProcessRevision, ProcessSubmissionPreview } from "@rnd/shared";
import { client } from "./api";

export type TrialConclusion = "PENDING" | "ADJUST" | "REJECT" | "RECOMMEND";
export type TrialDifficulty = "EASY" | "MEDIUM" | "HARD";

export interface TrialStepParameters {
  parameter1Value: string | null;
  parameter2Value: string | null;
}

export interface TrialPlannedData {
  materialWeightsKg: Record<string, number>;
  stepParameters: Record<string, TrialStepParameters>;
  majorYieldTargets: Record<string, number>;
  batchYieldTarget: number | null;
  yieldBasisNote: string | null;
}

export interface TrialScheme {
  id: string;
  experimentFormId: string;
  versionNo: number;
  name: string;
  sourceTrialId?: string | null;
  archived: boolean;
  purpose: string | null;
  variables: string | null;
  conclusion: TrialConclusion;
  recommendationReason: string | null;
  qualityScore: number | null;
  qualityNotes: string | null;
  difficulty: TrialDifficulty | null;
  plan: ProcessPlanDraft;
  plannedData: TrialPlannedData;
  inheritedActuals: boolean;
  majorOrigins: Record<string, string>;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface TrialPromotionPreview {
  trialVersionNo: number;
  expectedProcessVersionNo: number;
  checks: ProcessSubmissionPreview;
  differingDraft: boolean;
  previewToken: string;
  previewHash: string;
}

export interface TrialPromotionSource {
  promotionId: string;
  trialId: string;
  trialName: string;
  trialVersionNo: number;
  trialSnapshotHash: string;
  promotedBy: string;
  promotedAt: string;
}

export interface SaveTrialRequest {
  versionNo: number;
  name: string;
  purpose: string | null;
  variables: string | null;
  conclusion: TrialConclusion;
  recommendationReason: string | null;
  qualityScore: number | null;
  qualityNotes: string | null;
  difficulty: TrialDifficulty | null;
  plan: ProcessPlanDraft;
  plannedData: TrialPlannedData;
}

export const trialApi = {
  list: (formId: string) => client.get<TrialScheme[]>(`/experiment-forms/${formId}/trials`),
  find: (formId: string, trialId: string) => client.get<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}`),
  create: (formId: string, request: { name: string; purpose: string | null; variables: string | null; plan: ProcessPlanDraft; plannedData: TrialPlannedData }) =>
    client.post<TrialScheme>(`/experiment-forms/${formId}/trials`, request),
  save: (formId: string, trialId: string, request: SaveTrialRequest) =>
    client.put<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}`, request),
  copy: (formId: string, trialId: string, request: { versionNo: number; name: string; includeActuals: boolean }) =>
    client.post<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}/copy`, request),
  archive: (formId: string, trialId: string, request: { versionNo: number; archived: boolean }) =>
    client.post<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}/archive`, request),
  confirmControlPoint: (formId: string, trialId: string, pointId: string, trialVersionNo: number) =>
    client.post<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}/control-points/${pointId}/confirm`, { trialVersionNo }),
  confirmControlDeviation: (formId: string, trialId: string, pointId: string, trialVersionNo: number, resolutionNote: string) =>
    client.post<TrialScheme>(`/experiment-forms/${formId}/trials/${trialId}/control-points/${pointId}/confirm-deviation`, { trialVersionNo, resolutionNote }),
  previewPromotion: (formId: string, trialId: string, trialVersionNo: number, expectedProcessVersionNo: number) =>
    client.post<TrialPromotionPreview>(`/experiment-forms/${formId}/trials/${trialId}/submission-preview`, { trialVersionNo, expectedProcessVersionNo }),
  promote: (formId: string, trialId: string, request: { trialVersionNo: number; expectedProcessVersionNo: number; previewToken: string; confirmed: true; changeReason: string | null; idempotencyKey: string }) =>
    client.post<ProcessRevision>(`/experiment-forms/${formId}/trials/${trialId}/submit`, request),
  revisionSource: (formId: string, revisionId: string) =>
    client.get<TrialPromotionSource | null>(`/experiment-forms/${formId}/process-plan/revisions/${revisionId}/source`),
  displacedDraft: (formId: string, revisionId: string) =>
    client.get<ProcessPlanDraft | null>(`/experiment-forms/${formId}/process-plan/revisions/${revisionId}/displaced-draft`),
};
