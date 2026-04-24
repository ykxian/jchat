import { FormEvent, useEffect, useState } from "react";
import { apiKeysApi } from "../api/apiKeys";
import { providersApi } from "../api/providers";
import type { ApiKeyRecord, ProviderInfo } from "../api/types";
import { useAuthStore } from "../stores/authStore";

export function SettingsPage() {
  const user = useAuthStore((state) => state.user);
  const [providers, setProviders] = useState<ProviderInfo[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKeyRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [form, setForm] = useState({
    baseUrl: "",
    key: "",
    label: "",
    provider: "openai"
  });

  useEffect(() => {
    let cancelled = false;

    async function loadSettings() {
      try {
        const [providerResponse, apiKeyResponse] = await Promise.all([
          providersApi.list(),
          apiKeysApi.list()
        ]);

        if (cancelled) {
          return;
        }

        setProviders(providerResponse.items);
        setApiKeys(apiKeyResponse.items);
        setPageError(null);
      } catch (error) {
        if (!cancelled) {
          setPageError(error instanceof Error ? error.message : "Failed to load settings");
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void loadSettings();

    return () => {
      cancelled = true;
    };
  }, []);

  async function refreshData() {
    const [providerResponse, apiKeyResponse] = await Promise.all([
      providersApi.list(),
      apiKeysApi.list()
    ]);
    setProviders(providerResponse.items);
    setApiKeys(apiKeyResponse.items);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.provider || !form.label.trim() || !form.key.trim()) {
      setPageError("Provider, label, and API key are required.");
      return;
    }

    setIsSaving(true);

    try {
      await apiKeysApi.create({
        baseUrl: form.baseUrl.trim() || null,
        key: form.key.trim(),
        label: form.label.trim(),
        provider: form.provider
      });
      await refreshData();
      setForm({
        baseUrl: "",
        key: "",
        label: "",
        provider: form.provider
      });
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to save API key");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(id: string) {
    try {
      await apiKeysApi.delete(id);
      await refreshData();
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to delete API key");
    }
  }

  return (
    <div className="panel-grid panel-grid--compact">
      <section className="panel panel--hero">
        <p className="eyebrow">Settings</p>
        <h2>Configuration surface</h2>
        <p className="muted">
          Phase 9 makes providers visible, user API keys manageable, and model
          selection explicit before each conversation turns into a chat stream.
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
        <h3>Runtime availability</h3>
        {isLoading ? <p className="muted">Loading providers...</p> : null}
        <div className="settings-provider-grid">
          {providers.map((provider) => (
            <article className="provider-card" key={provider.name}>
              <div className="provider-card__header">
                <div>
                  <strong>{provider.displayName}</strong>
                  <p className="muted">{provider.name}</p>
                </div>
                <span className={provider.available ? "pill" : "status-chip status-chip--muted"}>
                  {provider.available ? "Available" : "Unavailable"}
                </span>
              </div>
              <div className="status-list">
                <span>{provider.hasServerKey ? "Server key present" : "Server key missing"}</span>
                <span>{provider.userKeys.length} user key(s)</span>
              </div>
              <div className="stacked-list">
                {provider.models.map((model) => (
                  <div className="stacked-list__item" key={model.id}>
                    <strong>{model.displayName}</strong>
                    <span>{model.id}</span>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="panel">
        <p className="eyebrow">Secrets</p>
        <h3>Bring your own key</h3>
        <form className="settings-form" onSubmit={handleSubmit}>
          <label className="settings-field">
            <span>Provider</span>
            <select
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  provider: event.target.value
                }))
              }
              value={form.provider}
            >
              {providers.map((provider) => (
                <option key={provider.name} value={provider.name}>
                  {provider.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="settings-field">
            <span>Label</span>
            <input
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  label: event.target.value
                }))
              }
              placeholder="Personal Claude key"
              value={form.label}
            />
          </label>
          <label className="settings-field">
            <span>Base URL</span>
            <input
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  baseUrl: event.target.value
                }))
              }
              placeholder="Optional custom endpoint, e.g. https://proxy.example.com/v1"
              value={form.baseUrl}
            />
          </label>
          <label className="settings-field">
            <span>API key</span>
            <input
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  key: event.target.value
                }))
              }
              placeholder="Paste provider key"
              type="password"
              value={form.key}
            />
          </label>
          <div className="button-row">
            <button className="button button--primary" disabled={isSaving} type="submit">
              {isSaving ? "Saving..." : "Save key"}
            </button>
          </div>
        </form>

        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}

        <div className="stacked-list">
          {apiKeys.map((apiKey) => (
            <div className="stacked-list__item stacked-list__item--actions" key={apiKey.id}>
              <div>
                <strong>{apiKey.label}</strong>
                <span>
                  {apiKey.provider} · ••••{apiKey.last4}
                </span>
                {apiKey.baseUrl ? <span>{apiKey.baseUrl}</span> : null}
              </div>
              <button
                className="button button--ghost"
                onClick={() => void handleDelete(apiKey.id)}
                type="button"
              >
                Remove
              </button>
            </div>
          ))}
          {!isLoading && !apiKeys.length ? (
            <div className="stacked-list__item">
              <span>No user API keys saved yet.</span>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  );
}
