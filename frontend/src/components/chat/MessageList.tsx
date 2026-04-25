import { useEffect, useRef } from "react";
import type { FileRecord, Message } from "../../api/types";
import { usePreferences } from "../../preferences/preferences";

interface MessageListProps {
  filesById?: Record<string, FileRecord>;
  isLoading: boolean;
  messages: Message[];
}

export function MessageList({ filesById = {}, isLoading, messages }: MessageListProps) {
  const { copy, locale } = usePreferences();
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  if (!messages.length) {
    return (
      <section className="chat-feed chat-feed--empty">
        <div className="chat-empty-state">
          {isLoading ? copy.chat.loadingMessages : copy.chat.emptyMessages}
        </div>
      </section>
    );
  }

  return (
    <section className="chat-feed">
      {messages.map((message) => (
        <article
          key={message.id}
          className={
            message.role === "user"
              ? "message-bubble message-bubble--user"
              : message.role === "tool"
                ? "message-bubble message-bubble--tool"
                : "message-bubble"
          }
        >
          <header className="message-bubble__meta">
            <span className="message-bubble__role">
              {message.role === "user"
                ? copy.chat.you
                : message.role === "tool"
                  ? copy.chat.tool
                  : copy.chat.assistant}
            </span>
            {message.createdAt ? (
              <time className="message-bubble__time">
                {new Date(message.createdAt).toLocaleString(locale)}
              </time>
            ) : null}
          </header>
          <div className="message-bubble__content">
            {message.toolCalls?.length ? (
              <div className="tool-call-list">
                {message.toolCalls.map((toolCall) => (
                  <div className="tool-call-card" key={toolCall.id}>
                    <strong>{toolCall.name}</strong>
                    <pre>{JSON.stringify(toolCall.arguments, null, 2)}</pre>
                  </div>
                ))}
              </div>
            ) : (
              message.content || " "
            )}
          </div>
          {message.promptTokens || message.completionTokens ? (
            <footer className="message-bubble__footer">
              {message.promptTokens ? (
                <span className="status-chip status-chip--muted">
                  {copy.chat.promptTokens} {message.promptTokens}
                </span>
              ) : null}
              {message.completionTokens ? (
                <span className="status-chip status-chip--muted">
                  {copy.chat.completionTokens} {message.completionTokens}
                </span>
              ) : null}
            </footer>
          ) : null}
          {message.fileIds.length ? (
            <div className="message-bubble__attachments">
              {message.fileIds.map((fileId) => {
                const file = filesById[fileId];
                return (
                  <span className="message-attachment" key={fileId}>
                    {file?.filename ?? `${copy.chat.filePrefix}${fileId}`}
                  </span>
                );
              })}
            </div>
          ) : null}
        </article>
      ))}
      <div ref={endRef} />
    </section>
  );
}
