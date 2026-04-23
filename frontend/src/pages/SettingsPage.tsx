export function SettingsPage() {
  return (
    <div className="panel-grid panel-grid--compact">
      <section className="panel panel--hero">
        <p className="eyebrow">Settings</p>
        <h2>Configuration surface</h2>
        <p className="muted">
          The route now exists as a stable destination for provider preferences,
          API key management, and user profile actions.
        </p>
      </section>

      <section className="panel">
        <p className="eyebrow">Profile</p>
        <h3>Account information</h3>
        <ul className="feature-list">
          <li>Display name</li>
          <li>Email and avatar</li>
          <li>Password change entry</li>
        </ul>
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
