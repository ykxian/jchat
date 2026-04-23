import { authStore } from "../stores/authStore";
import type { AuthSession } from "./auth";

const API_BASE = "/api/v1";

type QueryValue = string | number | boolean | null | undefined;
type QueryParams = Record<string, QueryValue | QueryValue[]>;

export type ApiErrorPayload = {
  code?: string;
  message?: string;
  details?: unknown;
  requestId?: string;
};

export type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | object | null;
  query?: QueryParams;
  token?: string | null;
  skipAuthRefresh?: boolean;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: unknown;
  readonly requestId?: string;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message ?? "Request failed");
    this.name = "ApiError";
    this.status = status;
    this.code = payload.code ?? `HTTP_${status}`;
    this.details = payload.details;
    this.requestId = payload.requestId;
  }
}

function buildUrl(path: string, query?: QueryParams) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const url = new URL(`${API_BASE}${normalizedPath}`, window.location.origin);

  if (!query) {
    return `${url.pathname}${url.search}`;
  }

  for (const [key, rawValue] of Object.entries(query)) {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];

    for (const value of values) {
      if (value === null || value === undefined) {
        continue;
      }

      url.searchParams.append(key, String(value));
    }
  }

  return `${url.pathname}${url.search}`;
}

function isJsonBody(body: ApiRequestOptions["body"]): body is object {
  if (body === null || body === undefined) {
    return false;
  }

  if (typeof body !== "object") {
    return false;
  }

  return !(body instanceof FormData) && !(body instanceof URLSearchParams) && !(body instanceof Blob);
}

async function parseResponseBody(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const contentType = response.headers.get("content-type") ?? "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

async function parseErrorPayload(response: Response) {
  return (await parseResponseBody(response).catch(() => undefined)) as ApiErrorPayload | undefined;
}

let refreshPromise: Promise<AuthSession> | null = null;

export async function refreshAccessToken() {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const response = await fetch(buildUrl("/auth/refresh"), {
      credentials: "include",
      headers: {
        Accept: "application/json"
      },
      method: "POST"
    });

    if (!response.ok) {
      const payload = await parseErrorPayload(response.clone());
      authStore.clearAuth();
      throw new ApiError(response.status, payload ?? { code: "AUTH_REFRESH_INVALID" });
    }

    const session = (await parseResponseBody(response)) as AuthSession;
    authStore.setAuth(session.user, session.accessToken);
    return session;
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}

async function performRequest(
  requestPath: string,
  init: RequestInit,
  token: string | null,
  allowRefresh: boolean
) {
  const headers = new Headers(init.headers);

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(requestPath, {
    ...init,
    credentials: "include",
    headers
  });

  if (!allowRefresh || !token || response.status !== 401) {
    return response;
  }

  const payload = await parseErrorPayload(response.clone());

  if (payload?.code !== "AUTH_EXPIRED") {
    return response;
  }

  const session = await refreshAccessToken();
  return performRequest(requestPath, init, session.accessToken, false);
}

export async function apiRequest<T = unknown>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const {
    body,
    headers,
    query,
    skipAuthRefresh = false,
    token,
    ...init
  } = options;
  const requestHeaders = new Headers(headers);

  if (!requestHeaders.has("Accept")) {
    requestHeaders.set("Accept", "application/json");
  }

  let requestBody: BodyInit | null | undefined;

  if (isJsonBody(body)) {
    requestHeaders.set("Content-Type", "application/json");
    requestBody = JSON.stringify(body);
  } else {
    requestBody = body as BodyInit | null | undefined;
  }

  const response = await performRequest(
    buildUrl(path, query),
    {
      ...init,
      body: requestBody,
      headers: requestHeaders
    },
    token ?? authStore.getState().accessToken,
    !skipAuthRefresh
  );

  if (!response.ok) {
    const payload = await parseErrorPayload(response.clone());
    throw new ApiError(response.status, payload ?? {});
  }

  return (await parseResponseBody(response)) as T;
}

export const apiClient = {
  delete: <T = void>(path: string, options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, method: "DELETE" }),
  get: <T = unknown>(path: string, options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, method: "GET" }),
  patch: <T = unknown>(path: string, body?: ApiRequestOptions["body"], options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, body, method: "PATCH" }),
  post: <T = unknown>(path: string, body?: ApiRequestOptions["body"], options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, body, method: "POST" }),
  put: <T = unknown>(path: string, body?: ApiRequestOptions["body"], options?: ApiRequestOptions) =>
    apiRequest<T>(path, { ...options, body, method: "PUT" })
};
