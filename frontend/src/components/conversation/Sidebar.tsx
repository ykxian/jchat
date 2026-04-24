import type { Conversation } from "../../api/types";

interface SidebarProps {
  conversations: Conversation[];
  currentConversationId: string | null;
  isCreatingConversation: boolean;
  isLoading: boolean;
  onCreateConversation: () => void;
  onSelectConversation: (conversationId: string) => void;
}

function getConversationLabel(conversation: Conversation) {
  return conversation.title?.trim() || "Untitled conversation";
}

function getConversationMeta(conversation: Conversation) {
  return `${conversation.provider} / ${conversation.model}`;
}

export function Sidebar({
  conversations,
  currentConversationId,
  isCreatingConversation,
  isLoading,
  onCreateConversation,
  onSelectConversation
}: SidebarProps) {
  return (
    <aside className="chat-sidebar">
      <div className="chat-sidebar__top">
        <div>
          <p className="eyebrow">Conversations</p>
          <h3>Your workspace</h3>
        </div>
        <button
          className="button button--primary"
          disabled={isCreatingConversation}
          onClick={onCreateConversation}
          type="button"
        >
          {isCreatingConversation ? "Creating..." : "New Chat"}
        </button>
      </div>

      <div className="chat-sidebar__list" role="list">
        {isLoading && !conversations.length ? (
          <div className="conversation-item conversation-item--placeholder">Loading conversations...</div>
        ) : null}

        {!isLoading && !conversations.length ? (
          <div className="conversation-item conversation-item--placeholder">
            No conversations yet. Create one to start chatting.
          </div>
        ) : null}

        {conversations.map((conversation) => {
          const isActive = conversation.id === currentConversationId;

          return (
            <button
              key={conversation.id}
              className={isActive ? "conversation-item conversation-item--active" : "conversation-item"}
              onClick={() => onSelectConversation(conversation.id)}
              type="button"
            >
              <strong>{getConversationLabel(conversation)}</strong>
              <span>{getConversationMeta(conversation)}</span>
            </button>
          );
        })}
      </div>
    </aside>
  );
}
