import { FormEvent, useState } from "react";
import type { FileRecord } from "../../api/types";

interface ComposerProps {
  attachments?: FileRecord[];
  disabled?: boolean;
  isStreaming?: boolean;
  isOffline?: boolean;
  isUploading?: boolean;
  isWaitingForAttachments?: boolean;
  onAbort: () => void;
  onRemoveAttachment?: (fileId: string) => void;
  onSubmit: (content: string) => Promise<void>;
  onUploadFiles?: (files: File[]) => void;
}

export function Composer({
  attachments = [],
  disabled = false,
  isStreaming = false,
  isOffline = false,
  isUploading = false,
  isWaitingForAttachments = false,
  onAbort,
  onRemoveAttachment,
  onSubmit,
  onUploadFiles
}: ComposerProps) {
  const [value, setValue] = useState("");
  const isInputDisabled = disabled || isOffline;
  const isSendDisabled = isInputDisabled || isStreaming || !value.trim() || isWaitingForAttachments;

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

      <label className="composer__field">
        <span>Attachments</span>
        <input
          disabled={isInputDisabled || isStreaming || isUploading}
          multiple
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            if (files.length) {
              onUploadFiles?.(files);
            }
            event.target.value = "";
          }}
          type="file"
        />
      </label>

      {attachments.length ? (
        <div className="composer__attachments">
          {attachments.map((attachment) => (
            <div className="composer__attachment" key={attachment.id}>
              <div>
                <strong>{attachment.filename}</strong>
                <span>{attachment.status === "ready" ? "Ready" : attachment.status}</span>
              </div>
              <button
                className="button button--ghost"
                disabled={isStreaming}
                onClick={() => onRemoveAttachment?.(attachment.id)}
                type="button"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      ) : null}

      {isOffline ? (
        <p className="composer__hint">Offline mode keeps cached history readable but disables writes.</p>
      ) : null}
      {isUploading ? <p className="composer__hint">Uploading attachments...</p> : null}
      {isWaitingForAttachments ? (
        <p className="composer__hint">Wait for attachments to finish processing before sending.</p>
      ) : null}

      <div className="button-row">
        <button
          className="button button--primary"
          disabled={isSendDisabled}
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
