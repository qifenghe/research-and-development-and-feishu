import { client } from "./api";
export type ExportType = "FORMULA_XLSX" | "SOP_DOCX" | "PRICING_XLSX";
export interface ExportIssue { code: string; path: string; message: string; majorSequence?: number | null; stepSequence?: number | null }
export interface ExportCheck { ready: boolean; issues: ExportIssue[] }
export interface ExportScope { formId: string; trialId?: string; versionNo: number }
function path(scope: ExportScope, action: string, type: ExportType) {
  return `/experiment-forms/${encodeURIComponent(scope.formId)}/${scope.trialId ? `trials/${encodeURIComponent(scope.trialId)}` : "process-plan"}/export-${action}?versionNo=${scope.versionNo}&artifactType=${type}`;
}
export const exportApi = {
  check: (scope: ExportScope, type: ExportType) => client.get<ExportCheck>(path(scope, "check", type)),
  preview: (scope: ExportScope, type: ExportType) => client.get<Blob>(path(scope, "preview", type)),
  revisionCheck: (formId: string, revisionId: string, type: ExportType) => client.get<ExportCheck>(`/experiment-forms/${encodeURIComponent(formId)}/process-plan/revisions/${encodeURIComponent(revisionId)}/export-check?artifactType=${type}`),
};
