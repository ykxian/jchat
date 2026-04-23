import { Link } from "react-router-dom";

export function RegisterPage() {
  return (
    <section className="auth-card">
      <p className="eyebrow">Auth</p>
      <h2>Register</h2>
      <p className="muted">
        Registration stays static in Phase 2. The page now matches the planned
        route structure and leaves clear fields for the auth implementation.
      </p>

      <div className="form-grid">
        <label className="field">
          <span>Display Name</span>
          <input placeholder="Alice" type="text" />
        </label>
        <label className="field">
          <span>Email</span>
          <input placeholder="alice@example.com" type="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input placeholder="At least 8 characters" type="password" />
        </label>
      </div>

      <div className="button-row">
        <button className="button button--primary" type="button">
          Create Account
        </button>
        <Link className="button button--ghost" to="/login">
          Back To Login
        </Link>
      </div>
    </section>
  );
}
