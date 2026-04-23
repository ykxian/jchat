import { useAuthStore } from "../stores/authStore";

export function SettingsPage() {
  const user = useAuthStore((state) => state.user);

  return (
    <div className="panel-grid panel-grid--compact">
      <section className="panel panel--hero">
        <p className="eyebrow">Settings</p>
        <h2>Configuration surface</h2>
        <p className="muted">
          The auth layer is now live. This page can already render the current
          account payload that came from the backend.
        </p>
      </section>

      <section className="panel">
        <p className="eyebrow">Profile</p>
        <h3>Account information</h3>
        <dl className="profile-list">
          <div>
            <dt>Display name</dt>
            <dd>{user?.displayName ?? "Unknown"}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{user?.email ?? "Unavailable"}</dd>
          </div>
          <div>
            <dt>Email verified</dt>
            <dd>{user?.emailVerified ? "Yes" : "No"}</dd>
          </div>
        </dl>
      </section>

      <section className="panel">
        <p className="eyebrow">Providers</p>
        <h3>LLM defaults</h3>
        <ul className="feature-list">
          <li>Default provider selector</li>
          <li>Default model picker</li>
          <li>Temperature and output controls</li>
        </ul>
      </section>

      <section className="panel">
        <p className="eyebrow">Secrets</p>
        <h3>API key handling</h3>
        <ul className="feature-list">
          <li>Server-managed key usage in early phases</li>
          <li>User BYOK support in later settings work</li>
          <li>Encrypted-at-rest key lifecycle</li>
        </ul>
      </section>
    </div>
  );
}
