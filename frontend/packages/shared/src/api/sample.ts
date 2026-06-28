import type { ApiClient } from "./client";
import type {
  PagedResult,
  SampleRequest,
  StoppedSampleProjectView,
} from "../types";

export interface CreateSampleRequestPayload {
  productName: string;
  productType: string;
  customerName: string;
  specification: string;
  applicationScenario: string;
  flavorRequirement: string;
  creatorName: string;
}

export function createSampleApi(client: ApiClient) {
  return {
    list: (params?: { status?: string; keyword?: string; page?: number; size?: number; sort?: string }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set("status", params.status);
      if (params?.keyword) query.set("keyword", params.keyword);
      if (params?.page !== undefined) query.set("page", String(params.page));
      if (params?.size !== undefined) query.set("size", String(params.size));
      if (params?.sort) query.set("sort", params.sort);
      const suffix = query.toString() ? `?${query}` : "";
      if (params?.page !== undefined || params?.size !== undefined) {
        return client.get<PagedResult<SampleRequest>>(`/sample-requests${suffix}`);
      }
      return client.get<SampleRequest[]>(`/sample-requests${suffix}`);
    },
    create: (payload: CreateSampleRequestPayload) =>
      client.post<SampleRequest>("/sample-requests", payload),
    approve: (id: string, reviewerName: string) =>
      client.post(`/sample-requests/${id}/approve`, { reviewerName }),
    detail: (id: string) => client.get<SampleRequest>(`/sample-requests/${id}`),
    versionTimeline: (projectId: string) =>
      client.get<import("../types").SampleVersionTimelineItem[]>(
        `/sample-projects/${projectId}/version-timeline`,
      ),
    processSteps: (versionId: string) =>
      client.get<import("../types").ExperimentProcessStep[]>(
        `/sample-versions/${versionId}/process-steps`,
      ),
    stoppedProjects: () =>
      client.get<StoppedSampleProjectView[]>("/sample-projects/stopped"),
  };
}

export type SampleApi = ReturnType<typeof createSampleApi>;
