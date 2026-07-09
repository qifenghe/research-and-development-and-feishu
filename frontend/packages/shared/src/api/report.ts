import type { ApiClient } from "./client";

function query(params: Record<string, string | undefined>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, value);
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function createReportApi(client: ApiClient) {
  return {
    exportExperimentForm: (id: string) =>
      client.get<Blob>(`/reports/experiment-forms/${id}/export`),
    exportTestRecord: (id: string) =>
      client.get<Blob>(`/reports/test-records/${id}/export`),
    exportPricingFile: (id: string) =>
      client.get<Blob>(`/reports/pricing-files/${id}/export`),
    exportRndTasks: (params: { keyword?: string; status?: string; startDate?: string; endDate?: string } = {}) =>
      client.get<Blob>(`/reports/rnd-tasks/export${query(params)}`),
    exportShipments: (params: { keyword?: string; status?: string; startDate?: string; endDate?: string } = {}) =>
      client.get<Blob>(`/reports/shipments/export${query(params)}`),
  };
}

export type ReportApi = ReturnType<typeof createReportApi>;
