import { apiClient } from "./client";
import type { ApiKeyListResponse, ApiKeyRecord, CreateApiKeyPayload } from "./types";

export const apiKeysApi = {
  create(payload: CreateApiKeyPayload) {
    return apiClient.post<ApiKeyRecord>("/api-keys", payload);
  },
  delete(id: string) {
    return apiClient.delete<void>(`/api-keys/${id}`);
  },
  list() {
    return apiClient.get<ApiKeyListResponse>("/api-keys");
  }
};
