import { FormEvent, useEffect, useState } from "react";
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

  return "Unable to sign in right now. Please try again.";
}

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const emailFromQuery = searchParams.get("email") ?? "";
  const registered = searchParams.get("registered") === "1";
  const nextPath = getSafeNext(searchParams);
  const [email, setEmail] = useState(emailFromQuery);
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    setEmail(emailFromQuery);
  }, [emailFromQuery]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await authApi.login({
        email,
        password
      });
      navigate(nextPath, { replace: true });
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">Auth</p>
      <h2>Sign In</h2>
      <p className="muted">
        Use your email and password to get a fresh access token while the
        refresh token stays in an HttpOnly cookie.
      </p>

      {registered ? (
        <div className="auth-feedback auth-feedback--success">
          Account created. Sign in to continue.
        </div>
      ) : null}

      {errorMessage ? (
        <div className="auth-feedback auth-feedback--error">{errorMessage}</div>
      ) : null}

      <form className="form-grid" onSubmit={handleSubmit}>
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
            autoComplete="current-password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="At least 8 characters"
            required
            type="password"
            value={password}
          />
        </label>

        <div className="button-row">
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Signing In..." : "Sign In"}
          </button>
          <Link className="button button--ghost" to={`/register?next=${encodeURIComponent(nextPath)}`}>
            Create Account
          </Link>
        </div>
      </form>
    </section>
  );
}
