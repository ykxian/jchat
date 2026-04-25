import { usePreferences } from "../../preferences/preferences";

interface StreamingMessageProps {
  error: string | null;
  isStreaming: boolean;
}

export function StreamingMessage({ error, isStreaming }: StreamingMessageProps) {
  const { copy } = usePreferences();

  if (error) {
    return <div className="stream-banner stream-banner--error">{error}</div>;
  }

  if (!isStreaming) {
    return null;
  }

  return (
    <div className="stream-banner">
      <span className="stream-banner__dot" aria-hidden="true" />
      {copy.chat.streaming}
    </div>
  );
}
