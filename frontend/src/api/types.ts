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
  maskId: string | null;
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
  toolCalls: Array<{
    id: string;
    name: string;
    arguments: unknown;
  }> | null;
  toolCallId: string | null;
  parentId: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  fileIds: string[];
  createdAt: string | null;
}

export interface CreateConversationPayload {
  title?: string | null;
  provider?: string | null;
  model?: string | null;
  systemPrompt?: string | null;
  maskId?: string | null;
  reasoningEffort?: "low" | "medium" | "high" | null;
}

export interface UpdateConversationPayload {
  title?: string | null;
  pinned?: boolean | null;
  archived?: boolean | null;
  systemPrompt?: string | null;
  maskId?: string | null;
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
  maskId?: string | null;
  fileIds?: string[];
  tools?: string[];
  reasoningEffort?: "low" | "medium" | "high" | null;
  apiKeyId?: string | null;
}

export interface FileRecord {
  id: string;
  conversationId: string | null;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  status: "processing" | "ready" | "failed";
  errorMessage: string | null;
  createdAt: string | null;
}

export interface SseEnvelope {
  type: "start" | "delta" | "usage" | "done" | "error" | "tool_call" | "tool_result";
  messageId?: string | null;
  requestId?: string | null;
  content?: string | null;
  prompt?: number | null;
  completion?: number | null;
  finishReason?: string | null;
  code?: string | null;
  message?: string | null;
  toolCallId?: string | null;
  toolName?: string | null;
  toolArguments?: unknown;
  toolResult?: string | null;
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

export interface Mask {
  id: string;
  ownerId: string | null;
  name: string;
  avatar: string | null;
  systemPrompt: string;
  defaultProvider: string | null;
  defaultModel: string | null;
  temperature: number | null;
  topP: number | null;
  maxTokens: number | null;
  tags: string[];
  isPublic: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CreateMaskPayload {
  name: string;
  avatar?: string | null;
  systemPrompt: string;
  defaultProvider?: string | null;
  defaultModel?: string | null;
  temperature?: number | null;
  topP?: number | null;
  maxTokens?: number | null;
  tags?: string[];
  isPublic?: boolean;
}

export interface UpdateMaskPayload {
  name?: string | null;
  avatar?: string | null;
  systemPrompt?: string | null;
  defaultProvider?: string | null;
  defaultModel?: string | null;
  temperature?: number | null;
  topP?: number | null;
  maxTokens?: number | null;
  tags?: string[] | null;
  isPublic?: boolean | null;
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
