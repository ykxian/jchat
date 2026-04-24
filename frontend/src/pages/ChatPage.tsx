import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { apiKeysApi } from "../api/apiKeys";
import { streamChatCompletion } from "../api/chat";
import { conversationsApi } from "../api/conversations";
import { masksApi } from "../api/masks";
import { providersApi } from "../api/providers";
import type { ApiKeyRecord, Conversation, Mask, Message, ProviderInfo } from "../api/types";
import { Composer } from "../components/chat/Composer";
import { MessageList } from "../components/chat/MessageList";
import { StreamingMessage } from "../components/chat/StreamingMessage";
import { Sidebar } from "../components/conversation/Sidebar";
import { chatCache } from "../db/dexie";
import { useAuthStore } from "../stores/authStore";
import { conversationStore, useConversationStore } from "../stores/conversationStore";
import { streamStore, useStreamStore } from "../stores/streamStore";

const DEFAULT_PROVIDER = "openai";
const DEFAULT_MODEL = "gpt-4o-mini";
const REASONING_EFFORT_OPTIONS = [
  { label: "Default", value: "" },
  { label: "Low", value: "low" },
  { label: "Medium", value: "medium" },
  { label: "High", value: "high" }
] as const;

function buildDraftMessage(content: string): Message {
  return {
    completionTokens: null,
    content,
    createdAt: new Date().toISOString(),
    fileIds: [],
    id: `draft-user-${crypto.randomUUID()}`,
    parentId: null,
    promptTokens: null,
    role: "user",
    toolCallId: null,
    toolCalls: null
  };
}

function buildAssistantDraft(): Message {
  return {
    completionTokens: null,
    content: "",
    createdAt: new Date().toISOString(),
    fileIds: [],
    id: `draft-assistant-${crypto.randomUUID()}`,
    parentId: null,
    promptTokens: null,
    role: "assistant",
    toolCallId: null,
    toolCalls: null
  };
}

function getConversationHeading(conversation: Conversation | null) {
  return conversation?.title?.trim() || "New conversation";
}

function getDefaultSelection(providers: ProviderInfo[]) {
  const available = providers.find((provider) => provider.available) ?? providers[0];
  const model = available?.models[0];

  return {
    model: model?.id ?? DEFAULT_MODEL,
    provider: available?.name ?? DEFAULT_PROVIDER
  };
}

export function ChatPage() {
  const navigate = useNavigate();
  const { conversationId } = useParams();
  const userId = useAuthStore((state) => state.user?.id ?? null);
  const [isOnline, setIsOnline] = useState(() =>
    typeof navigator === "undefined" ? true : navigator.onLine
  );
  const [pageError, setPageError] = useState<string | null>(null);
  const [apiKeys, setApiKeys] = useState<ApiKeyRecord[]>([]);
  const [masks, setMasks] = useState<Mask[]>([]);
  const [providers, setProviders] = useState<ProviderInfo[]>([]);
  const [selectedApiKeyId, setSelectedApiKeyId] = useState<string>("");
  const [selectedMaskId, setSelectedMaskId] = useState("");
  const [modelDraft, setModelDraft] = useState("");
  const conversations = useConversationStore((state) => state.items);
  const currentId = useConversationStore((state) => state.currentId);
  const messagesByConversation = useConversationStore((state) => state.messagesByConversation);
  const isLoadingList = useConversationStore((state) => state.isLoadingList);
  const isLoadingMessages = useConversationStore((state) => state.isLoadingMessages);
  const isCreatingConversation = useConversationStore((state) => state.isCreatingConversation);
  const stream = useStreamStore((state) => state.current);

  const currentConversation =
    conversations.find((conversation) => conversation.id === currentId) ?? null;
  const messages = currentId ? messagesByConversation[currentId] ?? [] : [];
  const isStreamingCurrent = stream?.conversationId === currentId && stream.isStreaming;
  const currentProviderInfo =
    providers.find((provider) => provider.name === currentConversation?.provider) ?? null;
  const selectedApiKey =
    apiKeys.find((apiKey) => apiKey.id === selectedApiKeyId && apiKey.provider === currentConversation?.provider) ??
    null;
  const currentMask = masks.find((mask) => mask.id === (currentConversation?.maskId ?? "")) ?? null;

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    function handleOnline() {
      setIsOnline(true);
    }

    function handleOffline() {
      setIsOnline(false);
    }

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadProviders() {
      try {
        const [providerResponse, apiKeyResponse, maskResponse] = await Promise.all([
          providersApi.list(),
          apiKeysApi.list(),
          masksApi.list()
        ]);
        if (!cancelled) {
          setProviders(providerResponse.items);
          setApiKeys(apiKeyResponse.items);
          setMasks(maskResponse.items);
        }
      } catch (error) {
        if (!cancelled) {
          setPageError(error instanceof Error ? error.message : "Failed to load providers");
        }
      }
    }

    void loadProviders();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!userId) {
      return;
    }

    const activeUserId = userId;
    let cancelled = false;

    async function loadConversations() {
      conversationStore.setLoadingList(true);
      const cachedItems = await chatCache.listConversations(activeUserId);

      if (cancelled) {
        return;
      }

      if (cachedItems.length) {
        conversationStore.setConversations(cachedItems);
      }

      try {
        const response = await conversationsApi.list();

        if (cancelled) {
          return;
        }

        conversationStore.setConversations(response.items);
        await chatCache.replaceConversations(activeUserId, response.items);
        setPageError(null);
      } catch (error) {
        if (!cancelled && !cachedItems.length) {
          setPageError(error instanceof Error ? error.message : "Failed to load conversations");
        }
      } finally {
        if (!cancelled) {
          conversationStore.setLoadingList(false);
        }
      }
    }

    void loadConversations();

    return () => {
      cancelled = true;
    };
  }, [userId]);

  useEffect(() => {
    conversationStore.setCurrent(conversationId ?? null);
  }, [conversationId]);

  useEffect(() => {
    if (!conversationId || !userId) {
      return;
    }

    const activeUserId = userId;
    const targetConversationId = conversationId;
    let cancelled = false;

    async function loadMessages() {
      conversationStore.setLoadingMessages(true);
      const [cachedConversation, cachedMessages] = await Promise.all([
        chatCache.getConversation(activeUserId, targetConversationId),
        chatCache.listMessages(activeUserId, targetConversationId)
      ]);

      if (cancelled) {
        return;
      }

      if (cachedConversation) {
        conversationStore.upsertConversation(cachedConversation);
      }

      if (cachedConversation || cachedMessages.length) {
        conversationStore.setMessages(targetConversationId, cachedMessages);
      }

      try {
        const [conversation, messagePage] = await Promise.all([
          conversationsApi.get(targetConversationId),
          conversationsApi.listMessages(targetConversationId)
        ]);

        if (cancelled) {
          return;
        }

        conversationStore.upsertConversation(conversation);
        conversationStore.setMessages(targetConversationId, messagePage.items);
        await Promise.all([
          chatCache.upsertConversation(activeUserId, conversation),
          chatCache.replaceMessages(activeUserId, targetConversationId, messagePage.items)
        ]);
        setPageError(null);
      } catch (error) {
        if (!cancelled && !cachedConversation && !cachedMessages.length) {
          setPageError(error instanceof Error ? error.message : "Failed to load conversation");
        }
      } finally {
        if (!cancelled) {
          conversationStore.setLoadingMessages(false);
        }
      }
    }

    void loadMessages();

    return () => {
      cancelled = true;
    };
  }, [conversationId, userId]);

  useEffect(() => {
    if (!currentProviderInfo) {
      setSelectedApiKeyId("");
      return;
    }

    setSelectedApiKeyId((current) =>
      currentProviderInfo.userKeys.some((apiKey) => apiKey.id === current) ? current : ""
    );
  }, [currentProviderInfo]);

  useEffect(() => {
    setModelDraft(currentConversation?.model ?? "");
  }, [currentConversation?.id, currentConversation?.model]);

  useEffect(() => {
    setSelectedMaskId(currentConversation?.maskId ?? "");
  }, [currentConversation?.id, currentConversation?.maskId]);

  function getMask(maskId: string | null) {
    if (!maskId) {
      return null;
    }
    return masks.find((mask) => mask.id === maskId) ?? null;
  }

  async function handleCreateConversation() {
    if (isCreatingConversation || !isOnline || !userId) {
      return;
    }

    const activeUserId = userId;
    conversationStore.setCreatingConversation(true);

    try {
      const defaults = getDefaultSelection(providers);
      const selectedMask = getMask(selectedMaskId || null);
      const conversation = await conversationsApi.create({
        maskId: selectedMask?.id ?? null,
        model: selectedMask?.defaultModel ?? defaults.model,
        provider: selectedMask?.defaultProvider ?? defaults.provider,
        title: null
      });
      conversationStore.upsertConversation(conversation);
      await chatCache.upsertConversation(activeUserId, conversation);
      setPageError(null);
      navigate(`/chat/${conversation.id}`);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to create conversation");
    } finally {
      conversationStore.setCreatingConversation(false);
    }
  }

  async function syncConversationMessages(targetConversationId: string) {
    if (!userId) {
      return;
    }

    const activeUserId = userId;
    const [conversation, messagePage] = await Promise.all([
      conversationsApi.get(targetConversationId),
      conversationsApi.listMessages(targetConversationId)
    ]);
    conversationStore.upsertConversation(conversation);
    conversationStore.setMessages(targetConversationId, messagePage.items);
    await Promise.all([
      chatCache.upsertConversation(activeUserId, conversation),
      chatCache.replaceMessages(activeUserId, targetConversationId, messagePage.items)
    ]);
  }

  async function handleSendMessage(content: string) {
    if (!isOnline) {
      setPageError("Offline mode is read-only. Reconnect to send messages.");
      return;
    }

    if (!userId) {
      setPageError("Session expired. Please sign in again.");
      return;
    }

    try {
      const activeUserId = userId;
      let targetConversationId = currentId;

      if (!targetConversationId) {
        const defaults = getDefaultSelection(providers);
        const selectedMask = getMask(selectedMaskId || null);
        const conversation = await conversationsApi.create({
          maskId: selectedMask?.id ?? null,
          model: selectedMask?.defaultModel ?? defaults.model,
          provider: selectedMask?.defaultProvider ?? defaults.provider,
          title: null
        });
        conversationStore.upsertConversation(conversation);
        await chatCache.upsertConversation(activeUserId, conversation);
        targetConversationId = conversation.id;
        navigate(`/chat/${conversation.id}`);
      }

      if (!targetConversationId) {
        return;
      }

      const userMessage = buildDraftMessage(content);
      const assistantDraft = buildAssistantDraft();
      const abortController = new AbortController();
      conversationStore.addMessage(targetConversationId, userMessage);
      conversationStore.addMessage(targetConversationId, assistantDraft);
      streamStore.start(targetConversationId, abortController);
      setPageError(null);

      await streamChatCompletion(
        {
          conversationId: targetConversationId,
          apiKeyId: selectedApiKeyId || null,
          messages: [{ content, role: "user" }],
          maskId: currentConversation?.maskId ?? null,
          model: currentConversation?.model ?? DEFAULT_MODEL,
          provider: currentConversation?.provider ?? DEFAULT_PROVIDER,
          reasoningEffort: currentConversation?.reasoningEffort ?? null,
          temperature: 0.7,
          topP: 1
        },
        {
          onError(event) {
            const errorMessage = event.message ?? "Chat stream failed";
            streamStore.fail(errorMessage);
            setPageError(errorMessage);
          },
          onMessage(event) {
            if (event.type === "start") {
              streamStore.attachMetadata(event.messageId ?? null, event.requestId ?? null);
              return;
            }

            if (event.type === "delta" && event.content) {
              const currentContent =
                conversationStore
                  .getState()
                  .messagesByConversation[targetConversationId]
                  ?.find((message) => message.id === assistantDraft.id)?.content ?? "";

              conversationStore.updateMessage(targetConversationId, assistantDraft.id, {
                content: `${currentContent}${event.content}`
              });
              return;
            }

            if (event.type === "usage") {
              conversationStore.updateMessage(targetConversationId, assistantDraft.id, {
                completionTokens: event.completion ?? null,
                promptTokens: event.prompt ?? null
              });
              return;
            }
          }
        },
        abortController.signal
      );

      streamStore.finish();
      await syncConversationMessages(targetConversationId);
      streamStore.clear();
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setPageError("Streaming stopped.");
      } else {
        const message = error instanceof Error ? error.message : "Failed to send message";
        streamStore.fail(message);
        setPageError(message);
      }
    }
  }

  function handleAbort() {
    streamStore.abort();
  }

  async function handleUpdateConversationSelection(patch: {
    maskId?: string | null;
    provider?: string;
    model?: string;
    reasoningEffort?: "low" | "medium" | "high" | null;
  }) {
    if (!currentConversation || isStreamingCurrent) {
      return;
    }

    try {
      const nextConversation = await conversationsApi.update(currentConversation.id, patch);
      conversationStore.upsertConversation(nextConversation);

      if (userId) {
        await chatCache.upsertConversation(userId, nextConversation);
      }

      if (patch.provider && patch.provider !== currentConversation.provider) {
        setSelectedApiKeyId("");
      }

      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to update conversation");
    }
  }

  return (
    <div className="chat-layout">
      <Sidebar
        conversations={conversations}
        currentConversationId={currentId}
        isCreatingConversation={isCreatingConversation}
        isOffline={!isOnline}
        isLoading={isLoadingList}
        onCreateConversation={() => void handleCreateConversation()}
        onSelectConversation={(nextConversationId) => navigate(`/chat/${nextConversationId}`)}
      />

      <section className="chat-main">
        <header className="chat-main__header">
          <div>
            <p className="eyebrow">Chat</p>
            <h2>{getConversationHeading(currentConversation)}</h2>
            <p className="muted">
              {currentConversation
                ? `${currentConversation.provider} / ${currentConversation.model}`
                : "Create a conversation and start sending messages."}
            </p>
          </div>
          {currentConversation ? (
            <div className="chat-toolbar">
              <div className="chat-toolbar__controls">
                <label className="toolbar-field">
                  <span>Mask</span>
                  <select
                    disabled={Boolean(isStreamingCurrent)}
                    onChange={(event) => {
                      const nextMaskId = event.target.value || null;
                      const mask = getMask(nextMaskId);
                      setSelectedMaskId(nextMaskId ?? "");
                      void handleUpdateConversationSelection({
                        maskId: nextMaskId,
                        model: mask?.defaultModel ?? currentConversation.model,
                        provider: mask?.defaultProvider ?? currentConversation.provider
                      });
                    }}
                    value={currentConversation.maskId ?? ""}
                  >
                    <option value="">No mask</option>
                    {masks.map((mask) => (
                      <option key={mask.id} value={mask.id}>
                        {mask.avatar ? `${mask.avatar} ${mask.name}` : mask.name}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="toolbar-field">
                  <span>Provider</span>
                  <select
                    disabled={Boolean(isStreamingCurrent)}
                    onChange={(event) => {
                      const provider = providers.find(
                        (candidate) => candidate.name === event.target.value
                      );
                      const nextModel = provider?.models[0]?.id ?? currentConversation.model;
                      void handleUpdateConversationSelection({
                        model: nextModel,
                        provider: event.target.value
                      });
                    }}
                    value={currentConversation.provider}
                  >
                    {providers.map((provider) => (
                      <option
                        disabled={!provider.available}
                        key={provider.name}
                        value={provider.name}
                      >
                        {provider.displayName}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="toolbar-field">
                  <span>Model</span>
                  <input
                    disabled={Boolean(isStreamingCurrent)}
                    list={`provider-models-${currentConversation.id}`}
                    onBlur={() => {
                      const nextModel = modelDraft.trim();

                      if (!nextModel || nextModel === currentConversation.model) {
                        setModelDraft(currentConversation.model);
                        return;
                      }

                      void handleUpdateConversationSelection({
                        model: nextModel
                      });
                    }}
                    onChange={(event) => setModelDraft(event.target.value)}
                    placeholder="Enter model id"
                    value={modelDraft}
                  />
                  <datalist id={`provider-models-${currentConversation.id}`}>
                    {(currentProviderInfo?.models ?? []).map((model) => (
                      <option key={model.id} value={model.id}>
                        {model.displayName}
                      </option>
                    ))}
                  </datalist>
                </label>

                <label className="toolbar-field">
                  <span>Reasoning</span>
                  <select
                    disabled={Boolean(isStreamingCurrent)}
                    onChange={(event) => {
                      const nextReasoningEffort = event.target.value
                        ? (event.target.value as "low" | "medium" | "high")
                        : null;

                      void handleUpdateConversationSelection({
                        reasoningEffort: nextReasoningEffort
                      });
                    }}
                    value={currentConversation.reasoningEffort ?? ""}
                  >
                    {REASONING_EFFORT_OPTIONS.map((option) => (
                      <option key={option.value || "default"} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="toolbar-field">
                  <span>Credential</span>
                  <select
                    disabled={Boolean(isStreamingCurrent)}
                    onChange={(event) => setSelectedApiKeyId(event.target.value)}
                    value={selectedApiKeyId}
                  >
                    <option value="">Server key</option>
                    {(currentProviderInfo?.userKeys ?? []).map((apiKey) => (
                      <option key={apiKey.id} value={apiKey.id}>
                        {apiKey.label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="status-list">
                <span>{currentConversation.messageCount} messages</span>
                {currentMask ? <span>Mask {currentMask.name}</span> : null}
                {currentConversation.lastMessageAt ? (
                  <span>Updated {new Date(currentConversation.lastMessageAt).toLocaleString()}</span>
                ) : null}
                {currentConversation.reasoningEffort ? (
                  <span>Reasoning {currentConversation.reasoningEffort}</span>
                ) : null}
                {selectedApiKey?.baseUrl ? <span>Endpoint {selectedApiKey.baseUrl}</span> : null}
                {selectedApiKey?.baseUrl ? <span>Model is freeform for custom endpoints</span> : null}
              </div>
            </div>
          ) : null}
        </header>

        {!isOnline ? (
          <div className="status-banner status-banner--warning">
            You're offline. Cached conversations stay readable, but new writes are disabled until
            the network returns.
          </div>
        ) : null}

        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}

        <StreamingMessage error={stream?.error ?? null} isStreaming={Boolean(isStreamingCurrent)} />

        <MessageList isLoading={Boolean(conversationId && isLoadingMessages)} messages={messages} />

        <Composer
          disabled={Boolean(conversationId && isLoadingMessages)}
          isOffline={!isOnline}
          isStreaming={Boolean(isStreamingCurrent)}
          onAbort={handleAbort}
          onSubmit={handleSendMessage}
        />
      </section>
    </div>
  );
}
