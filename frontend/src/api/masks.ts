import { apiClient } from "./client";
import type { CreateMaskPayload, CursorPage, Mask, UpdateMaskPayload } from "./types";

export const masksApi = {
  create(payload: CreateMaskPayload) {
    return apiClient.post<Mask>("/masks", payload);
  },
  delete(id: string) {
    return apiClient.delete<void>(`/masks/${id}`);
  },
  get(id: string) {
    return apiClient.get<Mask>(`/masks/${id}`);
  },
  list(options?: { limit?: number; mine?: boolean; q?: string | null }) {
    return apiClient.get<CursorPage<Mask>>("/masks", {
      query: {
        limit: options?.limit ?? 50,
        mine: options?.mine ?? false,
        q: options?.q ?? undefined
      }
    });
  },
  update(id: string, payload: UpdateMaskPayload) {
    return apiClient.patch<Mask>(`/masks/${id}`, payload);
  }
};
