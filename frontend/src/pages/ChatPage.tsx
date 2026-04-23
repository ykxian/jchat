import { Link, useParams } from "react-router-dom";
import { useAuthStore } from "../stores/authStore";

export function ChatPage() {
  const { conversationId } = useParams();
  const user = useAuthStore((state) => state.user);

  return (
    <div className="panel-grid">
      <section className="panel panel--hero">
        <p className="eyebrow">Chat</p>
        <h2>{conversationId ? `Conversation ${conversationId}` : "Chat Workspace"}</h2>
        <p className="muted">
          Protected routing is now active. The workspace knows which user is
          signed in, and the next phase can attach conversation loading and SSE
          streaming on top of this authenticated shell.
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
        <p className="eyebrow">Session</p>
        <h3>Auth bootstrap is complete</h3>
        <ul className="feature-list">
          <li>{user?.displayName ?? "Unknown user"} is attached to the SPA state</li>
          <li>Bearer token stays in memory only</li>
          <li>Refresh cookie can restore the workspace after reload</li>
        </ul>
      </section>

      <section className="panel">
        <p className="eyebrow">Message Composer</p>
        <h3>Reserved for the streaming workflow</h3>
        <p className="muted">
          The UI is intentionally static here. Phase 5 can attach conversation
          data, then Phase 6 can add message send, abort, and stream state.
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
