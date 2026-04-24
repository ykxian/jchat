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
