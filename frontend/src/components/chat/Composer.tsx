import { FormEvent, useState } from "react";
import type { FileRecord } from "../../api/types";
import { usePreferences } from "../../preferences/preferences";

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
  const { copy } = usePreferences();
  const [value, setValue] = useState("");
  const isInputDisabled = disabled || isOffline;
  const isSendDisabled = isInputDisabled || isStreaming || !value.trim() || isWaitingForAttachments;
  const canUpload = !isInputDisabled && !isStreaming && !isUploading;

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
      <label className="composer__field composer__field--message">
        <textarea
          disabled={isInputDisabled}
          onChange={(event) => setValue(event.target.value)}
          placeholder={
            isOffline
              ? copy.chat.messagePlaceholderOffline
              : copy.chat.messagePlaceholder
          }
          rows={5}
          value={value}
        />
      </label>

      {attachments.length ? (
        <div className="composer__attachments">
          {attachments.map((attachment) => (
            <div className="composer__attachment" key={attachment.id}>
              <div>
                <strong>{attachment.filename}</strong>
                <span>
                  {attachment.status === "ready" ? copy.chat.attachmentsReady : attachment.status}
                </span>
              </div>
              <button
                className="button button--ghost"
                disabled={isStreaming}
                onClick={() => onRemoveAttachment?.(attachment.id)}
                type="button"
              >
                {copy.common.remove}
              </button>
            </div>
          ))}
        </div>
      ) : null}

      <div className="composer__toolbar">
        <label
          className={
            canUpload
              ? "button button--ghost composer__upload"
              : "button button--ghost composer__upload is-disabled"
          }
        >
          <input
            disabled={!canUpload}
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
          <span>{copy.chat.attachments}</span>
        </label>
        <div className="composer__status">
          {attachments.length ? (
            <span className="status-chip status-chip--muted">
              {attachments.length} {copy.chat.attachments}
            </span>
          ) : null}
          {isStreaming ? <span className="pill">{copy.chat.streaming}</span> : null}
        </div>
        <div className="button-row">
          <button className="button button--primary" disabled={isSendDisabled} type="submit">
            {isStreaming ? copy.chat.streaming : copy.chat.send}
          </button>
          <button
            className="button button--ghost"
            disabled={!isStreaming}
            onClick={onAbort}
            type="button"
          >
            {copy.chat.stop}
          </button>
        </div>
      </div>

      <div className="composer__footer">
        {isOffline ? (
          <p className="composer__hint">{copy.chat.attachmentsOfflineHint}</p>
        ) : null}
        {isUploading ? <p className="composer__hint">{copy.chat.attachmentsUploading}</p> : null}
        {isWaitingForAttachments ? (
          <p className="composer__hint">{copy.chat.attachmentsWaiting}</p>
        ) : null}
        {!isOffline && !isUploading && !isWaitingForAttachments ? (
          <p className="composer__hint">{copy.chat.endpointHint}</p>
        ) : null}
      </div>
    </form>
  );
}
