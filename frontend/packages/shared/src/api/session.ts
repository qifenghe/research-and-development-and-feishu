import type { ApiClient } from "./client";
import type {
  DashboardOverview,
  FeishuLoginResult,
  SessionPrincipal,
} from "../types";

export function createSessionApi(client: ApiClient) {
  return {
    me: () => client.get<SessionPrincipal>("/session/me"),
    feishuCallback: (code: string) =>
      client.post<FeishuLoginResult>("/feishu/oauth/callback", { code }),
  };
}

export function createDashboardApi(client: ApiClient) {
  return {
    overview: () => client.get<DashboardOverview>("/dashboard/overview"),
  };
}

export type SessionApi = ReturnType<typeof createSessionApi>;
export type DashboardApi = ReturnType<typeof createDashboardApi>;
