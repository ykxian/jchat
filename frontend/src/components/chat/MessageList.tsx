import { useEffect, useRef } from "react";
import type { Message } from "../../api/types";

interface MessageListProps {
  isLoading: boolean;
  messages: Message[];
}

function getEmptyLabel(isLoading: boolean) {
  return isLoading ? "Loading message history..." : "Send the first message to start this conversation.";
}

export function MessageList({ isLoading, messages }: MessageListProps) {
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
            <span>{message.role === "user" ? "You" : "Assistant"}</span>
            {message.createdAt ? <time>{new Date(message.createdAt).toLocaleString()}</time> : null}
          </header>
          <div className="message-bubble__content">{message.content || " "}</div>
        </article>
      ))}
      <div ref={endRef} />
    </section>
  );
}
