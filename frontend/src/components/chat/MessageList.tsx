import { useEffect, useRef } from "react";
import type { FileRecord, Message } from "../../api/types";

interface MessageListProps {
  filesById?: Record<string, FileRecord>;
  isLoading: boolean;
  messages: Message[];
}

function getEmptyLabel(isLoading: boolean) {
  return isLoading ? "Loading message history..." : "Send the first message to start this conversation.";
}

export function MessageList({ filesById = {}, isLoading, messages }: MessageListProps) {
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  if (!messages.length) {
    return (
      <section className="chat-feed chat-feed--empty">
        <div className="chat-empty-state">{getEmptyLabel(isLoading)}</div>
      </section>
    );
  }

  return (
    <section className="chat-feed">
      {messages.map((message) => (
        <article
          key={message.id}
          className={
            message.role === "user" ? "message-bubble message-bubble--user" : "message-bubble"
          }
        >
          <header className="message-bubble__meta">
            <span>
              {message.role === "user"
                ? "You"
                : message.role === "tool"
                  ? "Tool"
                  : "Assistant"}
            </span>
            {message.createdAt ? <time>{new Date(message.createdAt).toLocaleString()}</time> : null}
          </header>
          <div className="message-bubble__content">
            {message.toolCalls?.length ? (
              <div>
                {message.toolCalls.map((toolCall) => (
                  <div key={toolCall.id}>
                    <strong>{toolCall.name}</strong>
                    <pre>{JSON.stringify(toolCall.arguments, null, 2)}</pre>
                  </div>
                ))}
              </div>
            ) : (
              message.content || " "
            )}
          </div>
          {message.fileIds.length ? (
            <div className="message-bubble__attachments">
              {message.fileIds.map((fileId) => {
                const file = filesById[fileId];
                return (
                  <span className="message-attachment" key={fileId}>
                    {file?.filename ?? `File #${fileId}`}
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
