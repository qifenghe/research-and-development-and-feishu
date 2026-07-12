import type { ApiClient } from "./client";
import type {
  AuthLoginResult,
  DashboardOverview,
  FeishuLoginResult,
  SessionPrincipal,
} from "../types";

export function createSessionApi(client: ApiClient) {
  return {
    login: (username: string, password: string) =>
      client.post<AuthLoginResult>("/auth/login", { username, password }),
    logout: () => client.post<void>("/auth/logout"),
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
