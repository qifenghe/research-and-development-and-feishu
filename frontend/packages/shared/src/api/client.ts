import type { ApiResponse } from "../types";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface ApiClientOptions {
  baseURL?: string;
  getToken: () => string | null;
  onUnauthorized?: () => void;
  onForbidden?: () => void;
}

export function createApiClient(options: ApiClientOptions) {
  const baseURL = options.baseURL ?? "/api/v1";

  async function request<T>(
    path: string,
    init: RequestInit = {},
  ): Promise<T> {
    const headers = new Headers(init.headers);
    if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) {
      headers.set("Content-Type", "application/json");
    }

    const token = options.getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(`${baseURL}${path}`, {
      ...init,
      headers,
    });

    if (response.status === 401) {
      options.onUnauthorized?.();
      throw new ApiError("未登录或会话已过期", 401);
    }

    const contentType = response.headers.get("Content-Type") ?? "";
    if (
      contentType.includes("application/octet-stream") ||
      contentType.includes("application/vnd")
    ) {
      if (!response.ok) {
        throw new ApiError("下载失败", response.status);
      }
      return (await response.blob()) as T;
    }

    const payload = (await response.json()) as ApiResponse<T>;
    if (response.status === 403) {
      options.onForbidden?.();
      throw new ApiError(payload.message || "无权限访问", 403, payload.code);
    }
    if (!response.ok || payload.code !== "0") {
      throw new ApiError(payload.message || "请求失败", response.status, payload.code);
    }

    return payload.data;
  }

  return {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body?: unknown) =>
      request<T>(path, {
        method: "POST",
        body: body === undefined ? undefined : JSON.stringify(body),
      }),
    put: <T>(path: string, body?: unknown) =>
      request<T>(path, {
        method: "PUT",
        body: body === undefined ? undefined : JSON.stringify(body),
      }),
    upload: <T>(path: string, formData: FormData) =>
      request<T>(path, { method: "POST", body: formData }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
