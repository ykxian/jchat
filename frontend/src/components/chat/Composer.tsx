import { FormEvent, useState } from "react";

interface ComposerProps {
  disabled?: boolean;
  isStreaming?: boolean;
  onAbort: () => void;
  onSubmit: (content: string) => Promise<void>;
}

export function Composer({
  disabled = false,
  isStreaming = false,
  onAbort,
  onSubmit
}: ComposerProps) {
  const [value, setValue] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextValue = value.trim();

    if (!nextValue || disabled || isStreaming) {
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
          disabled={disabled}
          onChange={(event) => setValue(event.target.value)}
          placeholder="Ask something..."
          rows={4}
          value={value}
        />
      </label>

      <div className="button-row">
        <button
          className="button button--primary"
          disabled={disabled || isStreaming || !value.trim()}
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
