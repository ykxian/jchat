import type { Conversation } from "../../api/types";
import { usePreferences } from "../../preferences/preferences";

interface SidebarProps {
  conversations: Conversation[];
  currentConversationId: string | null;
  isCreatingConversation: boolean;
  isOffline?: boolean;
  isLoading: boolean;
  onCreateConversation: () => void;
  onSelectConversation: (conversationId: string) => void;
}

function getRelativeTime(timestamp: string | null, copy: ReturnType<typeof usePreferences>["copy"]) {
  if (!timestamp) {
    return null;
  }

  const diffMs = Date.now() - new Date(timestamp).getTime();

  if (diffMs < 60_000) {
    return copy.chat.justNow;
  }

  const minutes = Math.floor(diffMs / 60_000);

  if (minutes < 60) {
    return `${minutes}${copy.chat.minutesAgo}`;
  }

  const hours = Math.floor(minutes / 60);

  if (hours < 24) {
    return `${hours}${copy.chat.hoursAgo}`;
  }

  const days = Math.floor(hours / 24);
  return `${days}${copy.chat.daysAgo}`;
}

function getConversationMeta(conversation: Conversation) {
  return `${conversation.provider} / ${conversation.model}`;
}

export function Sidebar({
  conversations,
  currentConversationId,
  isCreatingConversation,
  isOffline = false,
  isLoading,
  onCreateConversation,
  onSelectConversation
}: SidebarProps) {
  const { copy } = usePreferences();

  return (
    <aside className="chat-sidebar">
      <div className="chat-sidebar__top">
        <div className="chat-sidebar__intro">
          <p className="chat-sidebar__label">{copy.chat.sidebarTitle}</p>
          <h3>{copy.chat.newChat}</h3>
        </div>
        <div className="chat-sidebar__meta">
          <span className={isOffline ? "status-chip status-chip--warning" : "pill"}>
            {isOffline ? copy.chat.offlineButton : copy.shell.statsOnline}
          </span>
          <span className="status-chip status-chip--muted">
            {conversations.length} {copy.chat.sidebarTitle}
          </span>
        </div>
        <button
          className="button button--primary"
          disabled={isCreatingConversation || isOffline}
          onClick={onCreateConversation}
          type="button"
        >
          {isCreatingConversation
            ? copy.chat.creating
            : isOffline
              ? copy.chat.offlineButton
              : copy.chat.newChat}
        </button>
      </div>

      <div className="chat-sidebar__list" role="list">
        {isLoading && !conversations.length ? (
          <div className="conversation-item conversation-item--placeholder">
            {copy.chat.loadingConversations}
          </div>
        ) : null}

        {!isLoading && !conversations.length ? (
          <div className="conversation-item conversation-item--placeholder">
            {isOffline
              ? copy.chat.emptyConversationsOffline
              : copy.chat.emptyConversations}
          </div>
        ) : null}

        {conversations.map((conversation) => {
          const isActive = conversation.id === currentConversationId;
          const relativeTime = getRelativeTime(
            conversation.lastMessageAt ?? conversation.updatedAt ?? conversation.createdAt,
            copy
          );

          return (
            <button
              key={conversation.id}
              className={isActive ? "conversation-item conversation-item--active" : "conversation-item"}
              onClick={() => onSelectConversation(conversation.id)}
              type="button"
            >
              <div className="conversation-item__header">
                <strong className="conversation-item__title">
                  {conversation.title?.trim() || copy.chat.untitledConversation}
                </strong>
                {relativeTime ? <span className="conversation-item__time">{relativeTime}</span> : null}
              </div>
              <span className="conversation-item__subtitle">{getConversationMeta(conversation)}</span>
              <div className="conversation-item__badges">
                {conversation.pinned ? <span className="status-chip">{copy.chat.pinned}</span> : null}
                {conversation.archived ? (
                  <span className="status-chip status-chip--muted">{copy.chat.archived}</span>
                ) : null}
                <span className="status-chip status-chip--muted">{conversation.messageCount}</span>
              </div>
            </button>
          );
        })}
      </div>
    </aside>
  );
}
