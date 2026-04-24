import { authStore } from "../stores/authStore";
import { ApiError, refreshAccessToken } from "./client";
import type { ChatCompletionPayload, SseEnvelope } from "./types";

const API_BASE = "/api/v1";

export interface StreamHandlers {
  onError?: (event: SseEnvelope) => void;
  onMessage?: (event: SseEnvelope) => void;
}

function buildUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE}${normalizedPath}`;
}

async function ensureAccessToken() {
  const currentToken = authStore.getState().accessToken;

  if (currentToken) {
    return currentToken;
  }

  const session = await refreshAccessToken();
  return session.accessToken;
}

async function parseApiError(response: Response) {
  try {
    const payload = (await response.json()) as {
      code?: string;
      details?: unknown;
      message?: string;
      requestId?: string;
    };
    return new ApiError(response.status, payload);
  } catch {
    return new ApiError(response.status, {});
  }
}

function parseEventChunk(chunk: string) {
  const lines = chunk.split(/\r?\n/);
  let eventName = "message";
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
      continue;
    }

    if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  }

  if (!dataLines.length) {
    return null;
  }

  return {
    eventName,
    payload: JSON.parse(dataLines.join("\n")) as SseEnvelope
  };
}

async function readSseStream(
  stream: ReadableStream<Uint8Array>,
  handlers: StreamHandlers,
  signal?: AbortSignal
) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      if (signal?.aborted) {
        throw new DOMException("The operation was aborted.", "AbortError");
      }

      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split("\n\n");
      buffer = parts.pop() ?? "";

      for (const part of parts) {
        const parsed = parseEventChunk(part.trim());

        if (!parsed) {
          continue;
        }

        if (parsed.eventName === "error") {
          handlers.onError?.(parsed.payload);
          continue;
        }

        handlers.onMessage?.(parsed.payload);
      }
    }

    const trailing = buffer.trim();

    if (trailing) {
      const parsed = parseEventChunk(trailing);

      if (parsed) {
        if (parsed.eventName === "error") {
          handlers.onError?.(parsed.payload);
        } else {
          handlers.onMessage?.(parsed.payload);
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}

export async function streamChatCompletion(
  payload: ChatCompletionPayload,
  handlers: StreamHandlers,
  signal?: AbortSignal
) {
  async function sendRequest(accessToken: string) {
    return fetch(buildUrl("/chat/completions"), {
      body: JSON.stringify(payload),
      credentials: "include",
      headers: {
        Accept: "text/event-stream",
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json"
      },
      method: "POST",
      signal
    });
  }

  let response = await sendRequest(await ensureAccessToken());

  if (response.status === 401) {
    const error = await parseApiError(response.clone());

    if (error.code === "AUTH_EXPIRED") {
      const session = await refreshAccessToken();
      response = await sendRequest(session.accessToken);
    } else {
      throw error;
    }
  }

  if (!response.ok) {
    throw await parseApiError(response);
  }

  if (!response.body) {
    throw new Error("Streaming response body is missing");
  }

  await readSseStream(response.body, handlers, signal);
}
