import { apiClient } from "./client";
import type { ProviderListResponse } from "./types";

export const providersApi = {
  list() {
    return apiClient.get<ProviderListResponse>("/providers");
  }
};
