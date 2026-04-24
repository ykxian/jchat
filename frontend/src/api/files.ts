import { apiClient } from "./client";
import type { CursorPage, FileRecord } from "./types";

export const filesApi = {
  get(id: string) {
    return apiClient.get<FileRecord>(`/files/${id}`);
  },
  list(conversationId?: string | null, limit = 50) {
    return apiClient.get<CursorPage<FileRecord>>("/files", {
      query: {
        conversationId,
        limit
      }
    });
  },
  remove(id: string) {
    return apiClient.delete<void>(`/files/${id}`);
  },
  upload(file: File, conversationId?: string | null) {
    const body = new FormData();
    body.append("file", file);
    if (conversationId) {
      body.append("conversationId", conversationId);
    }
    return apiClient.post<FileRecord>("/files", body);
  }
};
