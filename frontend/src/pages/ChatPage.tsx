import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { streamChatCompletion } from "../api/chat";
import { conversationsApi } from "../api/conversations";
import type { Conversation, Message } from "../api/types";
import { Composer } from "../components/chat/Composer";
import { MessageList } from "../components/chat/MessageList";
import { StreamingMessage } from "../components/chat/StreamingMessage";
import { Sidebar } from "../components/conversation/Sidebar";
import { conversationStore, useConversationStore } from "../stores/conversationStore";
import { streamStore, useStreamStore } from "../stores/streamStore";

const DEFAULT_PROVIDER = "openai";
const DEFAULT_MODEL = "gpt-4o-mini";

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

export function ChatPage() {
  const navigate = useNavigate();
  const { conversationId } = useParams();
  const [pageError, setPageError] = useState<string | null>(null);
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

  useEffect(() => {
    let cancelled = false;

    async function loadConversations() {
      conversationStore.setLoadingList(true);

      try {
        const response = await conversationsApi.list();

        if (cancelled) {
          return;
        }

        conversationStore.setConversations(response.items);
        setPageError(null);
      } catch (error) {
        if (!cancelled) {
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
  }, []);

  useEffect(() => {
    conversationStore.setCurrent(conversationId ?? null);
  }, [conversationId]);

  useEffect(() => {
    if (!conversationId) {
      return;
    }

    const targetConversationId = conversationId;
    let cancelled = false;

    async function loadMessages() {
      conversationStore.setLoadingMessages(true);

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
        setPageError(null);
      } catch (error) {
        if (!cancelled) {
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
  }, [conversationId]);

  const sidebarConversations = useMemo(() => conversations, [conversations]);

  async function handleCreateConversation() {
    if (isCreatingConversation) {
      return;
    }

    conversationStore.setCreatingConversation(true);

    try {
      const conversation = await conversationsApi.create({
        model: DEFAULT_MODEL,
        provider: DEFAULT_PROVIDER,
        title: null
      });
      conversationStore.upsertConversation(conversation);
      setPageError(null);
      navigate(`/chat/${conversation.id}`);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to create conversation");
    } finally {
      conversationStore.setCreatingConversation(false);
    }
  }

  async function syncConversationMessages(targetConversationId: string) {
    const [conversation, messagePage] = await Promise.all([
      conversationsApi.get(targetConversationId),
      conversationsApi.listMessages(targetConversationId)
    ]);
    conversationStore.upsertConversation(conversation);
    conversationStore.setMessages(targetConversationId, messagePage.items);
  }

  async function handleSendMessage(content: string) {
    try {
      let targetConversationId = currentId;

      if (!targetConversationId) {
        const conversation = await conversationsApi.create({
          model: DEFAULT_MODEL,
          provider: DEFAULT_PROVIDER,
          title: null
        });
        conversationStore.upsertConversation(conversation);
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
          messages: [{ content, role: "user" }],
          model: currentConversation?.model ?? DEFAULT_MODEL,
          provider: currentConversation?.provider ?? DEFAULT_PROVIDER,
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

  return (
    <div className="chat-layout">
      <Sidebar
        conversations={sidebarConversations}
        currentConversationId={currentId}
        isCreatingConversation={isCreatingConversation}
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
            <div className="status-list">
              <span>{currentConversation.messageCount} messages</span>
              {currentConversation.lastMessageAt ? (
                <span>Updated {new Date(currentConversation.lastMessageAt).toLocaleString()}</span>
              ) : null}
            </div>
          ) : null}
        </header>

        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}

        <StreamingMessage error={stream?.error ?? null} isStreaming={Boolean(isStreamingCurrent)} />

        <MessageList isLoading={Boolean(conversationId && isLoadingMessages)} messages={messages} />

        <Composer
          disabled={Boolean(conversationId && isLoadingMessages)}
          isStreaming={Boolean(isStreamingCurrent)}
          onAbort={handleAbort}
          onSubmit={handleSendMessage}
        />
      </section>
    </div>
  );
}
