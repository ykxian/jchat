import { FormEvent, useEffect, useState } from "react";
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

export function LoginPage() {
  const navigate = useNavigate();
  const { copy } = usePreferences();
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
      setErrorMessage(getErrorMessage(error, copy.auth.unableToSignIn));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="auth-card">
      <p className="eyebrow">{copy.auth.badge}</p>
      <h2>{copy.auth.signInTitle}</h2>
      <p className="muted">{copy.auth.signInDescription}</p>

      {registered ? (
        <div className="auth-feedback auth-feedback--success">
          {copy.auth.registeredSuccess}
        </div>
      ) : null}

      {errorMessage ? (
        <div className="auth-feedback auth-feedback--error">{errorMessage}</div>
      ) : null}

      <form className="form-grid" onSubmit={handleSubmit}>
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
            autoComplete="current-password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder={copy.auth.passwordPlaceholderSignIn}
            required
            type="password"
            value={password}
          />
        </label>

        <div className="button-row">
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? copy.auth.signingIn : copy.auth.signIn}
          </button>
          <Link className="button button--ghost" to={`/register?next=${encodeURIComponent(nextPath)}`}>
            {copy.auth.createAccount}
          </Link>
        </div>
      </form>
    </section>
  );
}
