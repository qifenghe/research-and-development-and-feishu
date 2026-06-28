import type { ApiClient } from "./client";
import type {
  ArchiveFileView,
  PagedResult,
  PricingFileDetailView,
  PricingFileRecord,
  ShipmentDetailView,
  ShipmentRecord,
} from "../types";

export function createShipmentApi(client: ApiClient) {
  return {
    list: (params?: { status?: string; keyword?: string }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set("status", params.status);
      if (params?.keyword) query.set("keyword", params.keyword);
      const suffix = query.toString() ? `?${query}` : "";
      return client.get<ShipmentRecord[]>(`/shipments${suffix}`);
    },
    createShipment: (versionId: string, payload: Record<string, unknown>) =>
      client.post<ShipmentRecord>(`/sample-versions/${versionId}/shipments`, payload),
    shipmentDetail: (id: string, role?: string) => {
      const suffix = role ? `?role=${encodeURIComponent(role)}` : "";
      return client.get<ShipmentDetailView>(`/shipments/${id}/detail${suffix}`);
    },
    submitFeedback: (shipmentId: string, payload: Record<string, unknown>) =>
      client.post(`/shipments/${shipmentId}/feedback`, payload),
    pricingFiles: (params?: {
      status?: string;
      keyword?: string;
      page?: number;
      size?: number;
      sort?: string;
    }) => {
      const query = new URLSearchParams();
      if (params?.status) query.set("status", params.status);
      if (params?.keyword) query.set("keyword", params.keyword);
      if (params?.page !== undefined) query.set("page", String(params.page));
      if (params?.size !== undefined) query.set("size", String(params.size));
      if (params?.sort) query.set("sort", params.sort);
      const suffix = query.toString() ? `?${query}` : "";
      if (params?.page !== undefined || params?.size !== undefined) {
        return client.get<PagedResult<PricingFileRecord>>(`/pricing-files${suffix}`);
      }
      return client.get<PricingFileRecord[]>(`/pricing-files${suffix}`);
    },
    createPricingFile: (versionId: string) =>
      client.post<PricingFileRecord>(`/sample-versions/${versionId}/pricing-files`),
    pricingDetail: (id: string, role?: string) => {
      const suffix = role ? `?role=${encodeURIComponent(role)}` : "";
      return client.get<PricingFileDetailView>(`/pricing-files/${id}/detail${suffix}`);
    },
    notifyFinance: (id: string, recipientName: string) =>
      client.post(`/pricing-files/${id}/notify-finance`, { recipientName }),
    downloadPricingFile: (id: string) => client.get<Blob>(`/pricing-files/${id}/download`),
    archiveFiles: (versionId: string) =>
      client.get<ArchiveFileView[]>(`/sample-versions/${versionId}/archive-files`),
    downloadArchiveFile: (id: string) => client.get<Blob>(`/archive-files/${id}/download`),
  };
}

export type ShipmentApi = ReturnType<typeof createShipmentApi>;
