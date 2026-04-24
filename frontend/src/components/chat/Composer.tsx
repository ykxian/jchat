import { FormEvent, useState } from "react";

interface ComposerProps {
  disabled?: boolean;
  isStreaming?: boolean;
  isOffline?: boolean;
  onAbort: () => void;
  onSubmit: (content: string) => Promise<void>;
}

export function Composer({
  disabled = false,
  isStreaming = false,
  isOffline = false,
  onAbort,
  onSubmit
}: ComposerProps) {
  const [value, setValue] = useState("");
  const isInputDisabled = disabled || isOffline;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextValue = value.trim();

    if (!nextValue || isInputDisabled || isStreaming) {
      return;
    }

    await onSubmit(nextValue);
    setValue("");
  }

  return (
    <form className="composer" onSubmit={handleSubmit}>
      <label className="composer__field">
        <span>Message</span>
        <textarea
          disabled={isInputDisabled}
          onChange={(event) => setValue(event.target.value)}
          placeholder={
            isOffline
              ? "Offline mode is read-only. Reconnect to send a message."
              : "Ask something..."
          }
          rows={4}
          value={value}
        />
      </label>

      {isOffline ? (
        <p className="composer__hint">Offline mode keeps cached history readable but disables writes.</p>
      ) : null}

      <div className="button-row">
        <button
          className="button button--primary"
          disabled={isInputDisabled || isStreaming || !value.trim()}
          type="submit"
        >
          {isStreaming ? "Streaming..." : "Send"}
        </button>
        <button
          className="button button--ghost"
          disabled={!isStreaming}
          onClick={onAbort}
          type="button"
        >
          Stop
        </button>
      </div>
    </form>
  );
}
