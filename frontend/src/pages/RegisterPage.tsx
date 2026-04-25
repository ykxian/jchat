import { FormEvent, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { authApi } from "../api/auth";
import { ApiError } from "../api/client";
import { usePreferences } from "../preferences/preferences";

function getSafeNext(searchParams: URLSearchParams) {
  const next = searchParams.get("next");

  if (!next || !next.startsWith("/")) {
    return "/chat";
  }

  return next;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return fallback;
}

export function RegisterPage() {
  const navigate = useNavigate();
  const { copy } = usePreferences();
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
      setErrorMessage(getErrorMessage(error, copy.auth.unableToRegister));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">{copy.auth.badge}</p>
      <h2>{copy.auth.registerTitle}</h2>
      <p className="muted">{copy.auth.registerDescription}</p>

      {errorMessage ? (
        <div className="auth-feedback auth-feedback--error">{errorMessage}</div>
      ) : null}

      <form className="form-grid" onSubmit={handleSubmit}>
        <label className="field">
          <span>{copy.auth.displayName}</span>
          <input
            autoComplete="nickname"
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder={copy.auth.displayNamePlaceholder}
            type="text"
            value={displayName}
          />
        </label>
        <label className="field">
          <span>{copy.auth.email}</span>
          <input
            autoComplete="email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder={copy.auth.emailPlaceholder}
            required
            type="email"
            value={email}
          />
        </label>
        <label className="field">
          <span>{copy.auth.password}</span>
          <input
            autoComplete="new-password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder={copy.auth.passwordPlaceholderRegister}
            required
            type="password"
            value={password}
          />
        </label>

        <div className="button-row">
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? copy.auth.creatingAccount : copy.auth.createAccountCta}
          </button>
          <Link className="button button--ghost" to={`/login?next=${encodeURIComponent(nextPath)}`}>
            {copy.auth.backToLogin}
          </Link>
        </div>
      </form>
    </section>
  );
}
