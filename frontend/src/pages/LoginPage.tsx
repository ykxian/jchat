import { Link } from "react-router-dom";

export function LoginPage() {
  return (
    <section className="auth-card">
      <p className="eyebrow">Auth</p>
      <h2>Login</h2>
      <p className="muted">
        Phase 3 will connect this form to <code>/api/v1/auth/login</code> and
        in-memory token state. For now it defines the route and screen contract.
      </p>

      <div className="form-grid">
        <label className="field">
          <span>Email</span>
          <input placeholder="alice@example.com" type="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input placeholder="••••••••" type="password" />
        </label>
      </div>

      <div className="button-row">
        <button className="button button--primary" type="button">
          Sign In
        </button>
        <Link className="button button--ghost" to="/register">
          Create Account
        </Link>
      </div>
    </section>
  );
}
