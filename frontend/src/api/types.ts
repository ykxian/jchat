export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Conversation {
  id: string;
  title: string | null;
  provider: string;
  model: string;
  systemPrompt: string | null;
  reasoningEffort: "low" | "medium" | "high" | null;
  pinned: boolean;
  archived: boolean;
  lastMessageAt: string | null;
  messageCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export type MessageRole = "user" | "assistant" | "system" | "tool";

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  toolCalls: unknown;
  toolCallId: string | null;
  parentId: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  fileIds: string[];
  createdAt: string | null;
}

export interface CreateConversationPayload {
  title?: string | null;
  provider: string;
  model: string;
  systemPrompt?: string | null;
  reasoningEffort?: "low" | "medium" | "high" | null;
}

export interface UpdateConversationPayload {
  title?: string | null;
  pinned?: boolean | null;
  archived?: boolean | null;
  systemPrompt?: string | null;
  provider?: string | null;
  model?: string | null;
  reasoningEffort?: "low" | "medium" | "high" | null;
}

export interface ChatCompletionPayload {
  conversationId: string;
  provider?: string | null;
  model?: string | null;
  messages: Array<{
    role: "user";
    content: string;
  }>;
  temperature?: number;
  topP?: number;
  maxTokens?: number | null;
  reasoningEffort?: "low" | "medium" | "high" | null;
  apiKeyId?: string | null;
}

export interface SseEnvelope {
  type: "start" | "delta" | "usage" | "done" | "error";
  messageId?: string | null;
  requestId?: string | null;
  content?: string | null;
  prompt?: number | null;
  completion?: number | null;
  finishReason?: string | null;
  code?: string | null;
  message?: string | null;
}

export interface ModelSpec {
  id: string;
  displayName: string;
  contextWindow: number;
  supportsTools: boolean;
}

export interface ProviderKeySummary {
  id: string;
  label: string;
}

export interface ProviderInfo {
  name: string;
  displayName: string;
  available: boolean;
  models: ModelSpec[];
  hasServerKey: boolean;
  userKeys: ProviderKeySummary[];
}

export interface ProviderListResponse {
  items: ProviderInfo[];
}

export interface ApiKeyRecord {
  id: string;
  provider: string;
  label: string;
  baseUrl: string | null;
  last4: string;
  createdAt: string | null;
}

export interface ApiKeyListResponse {
  items: ApiKeyRecord[];
}

export interface CreateApiKeyPayload {
  provider: string;
  label: string;
  baseUrl?: string | null;
  key: string;
}
