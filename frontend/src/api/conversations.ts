import { apiClient } from "./client";
import type {
  Conversation,
  CreateConversationPayload,
  CursorPage,
  Message,
  UpdateConversationPayload
} from "./types";

export const conversationsApi = {
  create(payload: CreateConversationPayload) {
    return apiClient.post<Conversation>("/conversations", payload);
  },
  get(id: string) {
    return apiClient.get<Conversation>(`/conversations/${id}`);
  },
  list(limit = 50) {
    return apiClient.get<CursorPage<Conversation>>("/conversations", {
      query: { limit }
    });
  },
  listMessages(id: string, limit = 200) {
    return apiClient.get<CursorPage<Message>>(`/conversations/${id}/messages`, {
      query: { limit }
    });
  },
  update(id: string, payload: UpdateConversationPayload) {
    return apiClient.patch<Conversation>(`/conversations/${id}`, payload);
  }
};
