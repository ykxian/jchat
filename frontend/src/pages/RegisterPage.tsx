import { FormEvent, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { authApi } from "../api/auth";
import { ApiError } from "../api/client";

function getSafeNext(searchParams: URLSearchParams) {
  const next = searchParams.get("next");

  if (!next || !next.startsWith("/")) {
    return "/chat";
  }

  return next;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "Unable to create your account right now. Please try again.";
}

export function RegisterPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const nextPath = getSafeNext(searchParams);
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await authApi.register({
        displayName: displayName.trim() || undefined,
        email,
        password
      });
      navigate(
        `/login?registered=1&email=${encodeURIComponent(email)}&next=${encodeURIComponent(nextPath)}`,
        { replace: true }
      );
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">Auth</p>
      <h2>Create Account</h2>
      <p className="muted">
        Registration writes the user record first. After that, sign in and let
        the refresh cookie keep the session alive.
      </p>

      {errorMessage ? (
        <div className="auth-feedback auth-feedback--error">{errorMessage}</div>
      ) : null}

      <form className="form-grid" onSubmit={handleSubmit}>
        <label className="field">
          <span>Display Name</span>
          <input
            autoComplete="nickname"
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="Alice"
            type="text"
            value={displayName}
          />
        </label>
        <label className="field">
          <span>Email</span>
          <input
            autoComplete="email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="alice@example.com"
            required
            type="email"
            value={email}
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            autoComplete="new-password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="At least 8 characters with letters and digits"
            required
            type="password"
            value={password}
          />
        </label>

        <div className="button-row">
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Creating..." : "Create Account"}
          </button>
          <Link className="button button--ghost" to={`/login?next=${encodeURIComponent(nextPath)}`}>
            Back To Login
          </Link>
        </div>
      </form>
    </section>
  );
}
