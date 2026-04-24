interface StreamingMessageProps {
  error: string | null;
  isStreaming: boolean;
}

export function StreamingMessage({ error, isStreaming }: StreamingMessageProps) {
  if (error) {
    return <div className="stream-banner stream-banner--error">{error}</div>;
  }

  if (!isStreaming) {
    return null;
  }

  return <div className="stream-banner">Assistant is responding...</div>;
}
