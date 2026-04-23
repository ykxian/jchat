import { Link, useParams } from "react-router-dom";

export function ChatPage() {
  const { conversationId } = useParams();

  return (
    <div className="panel-grid">
      <section className="panel panel--hero">
        <p className="eyebrow">Chat</p>
        <h2>{conversationId ? `Conversation ${conversationId}` : "Chat Workspace"}</h2>
        <p className="muted">
          This route is ready for both <code>/chat</code> and{" "}
          <code>/chat/:conversationId</code>. The next phase can wire
          conversation loading, optimistic message drafts, and SSE streaming on
          top of this frame.
        </p>
        <div className="button-row">
          <Link className="button button--primary" to="/chat/42">
            Open Sample Conversation
          </Link>
          <Link className="button button--ghost" to="/settings">
            View Settings
          </Link>
        </div>
      </section>

      <section className="panel">
        <p className="eyebrow">Message Composer</p>
        <h3>Reserved for the streaming workflow</h3>
        <p className="muted">
          The UI is intentionally static here. Phase 3 can add auth first, then
          Phase 4 can attach message send, abort, and stream state.
        </p>
        <div className="placeholder-box">
          <span>Draft user message input</span>
          <span>Send / stop actions</span>
        </div>
      </section>

      <section className="panel">
        <p className="eyebrow">Conversation Sidebar</p>
        <h3>Data hooks are not attached yet</h3>
        <ul className="feature-list">
          <li>Conversation list query placeholder</li>
          <li>Current conversation route sync</li>
          <li>Recent message history region</li>
        </ul>
      </section>

      <section className="panel">
        <p className="eyebrow">Protocol Notes</p>
        <h3>Backend contract already fixed</h3>
        <ul className="feature-list">
          <li><code>POST /api/v1/chat/completions</code> for SSE responses</li>
          <li><code>/api/v1/conversations</code> for list and detail queries</li>
          <li>Auth via bearer token plus refresh cookie rotation</li>
        </ul>
      </section>
    </div>
  );
}
