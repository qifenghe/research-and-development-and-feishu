import type { ApiClient } from "./client";
import type {
  ExperimentForm,
  MaterialCategory,
  PagedResult,
  RemainingDisposition,
  RndTask,
  RndTaskDetailView,
  RndTaskStatus,
  TestRecord,
  UserAccount,
  YieldCalculationMode,
} from "../types";

export interface SaveExperimentDraftPayload {
  operatorName: string;
  summary?: string;
  materials?: Array<{
    stage: string;
    sequence: number;
    materialCode?: string;
    materialName: string;
    weightKg: number;
    utilizationRate?: number;
    materialCategory?: MaterialCategory;
    primaryMaterial?: boolean;
    formulaRatio?: number;
    inputUnit?: string;
    remark?: string;
  }>;
  processSteps?: Array<{
    sequence: number;
    processName: string;
    beforeWeightKg?: number;
    afterWeightKg?: number;
    lossRate?: number;
    remainingWeightKg?: number;
    remainingDisposition?: RemainingDisposition;
    lossWeightKg?: number;
    remark?: string;
  }>;
  finishedOutputWeightKg?: number;
  finishedOutputQuantity?: number;
  finishedOutputUnit?: "袋" | "盒" | "份" | "个" | "盘";
  finishedYieldPercent?: number;
  yieldCalculationMode?: YieldCalculationMode;
}

export function createTaskApi(client: ApiClient) {
  return {
    pool: () => client.get<RndTask[]>("/rnd-tasks/pool"),
    assignees: () => client.get<UserAccount[]>("/rnd-assignees"),
    list: (params?: {
      status?: RndTaskStatus | string;
      keyword?: string;
      assigneeName?: string;
      page?: number;
      size?: number;
      sort?: string;
    }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set("status", params.status);
      if (params?.keyword) query.set("keyword", params.keyword);
      if (params?.assigneeName) query.set("assigneeName", params.assigneeName);
      if (params?.page !== undefined) query.set("page", String(params.page));
      if (params?.size !== undefined) query.set("size", String(params.size));
      if (params?.sort) query.set("sort", params.sort);
      const suffix = query.toString() ? `?${query}` : "";
      if (params?.page !== undefined || params?.size !== undefined) {
        return client.get<PagedResult<RndTask>>(`/rnd-tasks${suffix}`);
      }
      return client.get<RndTask[]>(`/rnd-tasks${suffix}`);
    },
    detail: (id: string, role?: string, operatorName?: string) => {
      const query = new URLSearchParams();
      if (role) query.set("role", role);
      if (operatorName) query.set("operatorName", operatorName);
      const suffix = query.toString() ? `?${query}` : "";
      return client.get<RndTaskDetailView>(`/rnd-tasks/${id}/detail${suffix}`);
    },
    testRecords: (id: string) => client.get<TestRecord[]>(`/rnd-tasks/${id}/test-records`),
    assign: (id: string, assigneeName: string, dueDate: string, productOwnerName?: string) =>
      client.post<RndTask>(`/rnd-tasks/${id}/assign`, {
        assigneeName,
        dueDate,
        ...(productOwnerName ? { productOwnerName } : {}),
      }),
    accept: (id: string, acceptedBy: string) =>
      client.post<RndTask>(`/rnd-tasks/${id}/accept`, { acceptedBy }),
    saveExperimentDraft: (taskId: string, payload: SaveExperimentDraftPayload) =>
      client.post<ExperimentForm>(`/rnd-tasks/${taskId}/experiment-form/draft`, payload),
    submitExperimentForTest: (experimentFormId: string, testerName: string) =>
      client.post(`/experiment-forms/${experimentFormId}/submit-test`, { testerName }),
    uploadAttachment: (
      experimentFormId: string,
      file: File,
      meta?: { fileName?: string; category?: string; uploadedBy?: string; remark?: string },
    ) => {
      const formData = new FormData();
      formData.append("file", file);
      if (meta?.fileName) formData.append("fileName", meta.fileName);
      if (meta?.category) formData.append("category", meta.category);
      if (meta?.uploadedBy) formData.append("uploadedBy", meta.uploadedBy);
      if (meta?.remark) formData.append("remark", meta.remark);
      return client.upload(`/experiment-forms/${experimentFormId}/attachments`, formData);
    },
    passInternalTest: (testAssignmentId: string, testerName: string, comment?: string) =>
      client.post(`/test-assignments/${testAssignmentId}/pass`, { testerName, comment }),
    failInternalTest: (testAssignmentId: string, testerName: string, comment?: string) =>
      client.post(`/test-assignments/${testAssignmentId}/fail-resample`, { testerName, comment }),
  };
}

export type TaskApi = ReturnType<typeof createTaskApi>;
