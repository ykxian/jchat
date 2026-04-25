import { FormEvent, useEffect, useState } from "react";
import { apiKeysApi } from "../api/apiKeys";
import { providersApi } from "../api/providers";
import type { ApiKeyRecord, ProviderInfo } from "../api/types";
import { SegmentedControl } from "../components/ui/SegmentedControl";
import { usePreferences } from "../preferences/preferences";
import { useAuthStore } from "../stores/authStore";

export function SettingsPage() {
  const user = useAuthStore((state) => state.user);
  const { copy, getDefaultApiKeyId, locale, setDefaultApiKeyId, setLocale, setTheme, theme } =
    usePreferences();
  const [providers, setProviders] = useState<ProviderInfo[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKeyRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isApiKeyModalOpen, setIsApiKeyModalOpen] = useState(false);
  const [activeProvider, setActiveProvider] = useState("openai");
  const [pageError, setPageError] = useState<string | null>(null);
  const [form, setForm] = useState({
    baseUrl: "",
    key: "",
    label: "",
    provider: "openai"
  });

  const activeProviderInfo =
    providers.find((provider) => provider.name === activeProvider) ?? providers[0] ?? null;
  const activeProviderKeys = apiKeys.filter(
    (apiKey) => apiKey.provider === (activeProviderInfo?.name ?? activeProvider)
  );

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
        setActiveProvider((current) =>
          providerResponse.items.some((provider) => provider.name === current)
            ? current
            : (providerResponse.items[0]?.name ?? "openai")
        );
        setApiKeys(apiKeyResponse.items);
        setPageError(null);
      } catch (error) {
        if (!cancelled) {
          setPageError(error instanceof Error ? error.message : copy.settings.loadError);
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
  }, [copy.settings.loadError]);

  async function refreshData() {
    const [providerResponse, apiKeyResponse] = await Promise.all([
      providersApi.list(),
      apiKeysApi.list()
    ]);
    setProviders(providerResponse.items);
    setActiveProvider((current) =>
      providerResponse.items.some((provider) => provider.name === current)
        ? current
        : (providerResponse.items[0]?.name ?? "openai")
    );
    setApiKeys(apiKeyResponse.items);
  }

  function resetForm(provider = providers[0]?.name ?? "openai") {
    setForm({
      baseUrl: "",
      key: "",
      label: "",
      provider
    });
  }

  function openApiKeyModal(provider?: string) {
    resetForm(provider);
    setPageError(null);
    setIsApiKeyModalOpen(true);
  }

  function closeApiKeyModal() {
    setIsApiKeyModalOpen(false);
    resetForm(form.provider);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!form.provider || !form.label.trim() || !form.key.trim()) {
      setPageError(copy.settings.requiredError);
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
      closeApiKeyModal();
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : copy.settings.saveError);
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(id: string) {
    try {
      const apiKey = apiKeys.find((item) => item.id === id);
      await apiKeysApi.delete(id);
      if (apiKey && getDefaultApiKeyId(apiKey.provider) === id) {
        setDefaultApiKeyId(apiKey.provider, "");
      }
      await refreshData();
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : copy.settings.deleteError);
    }
  }

  useEffect(() => {
    if (!isApiKeyModalOpen) {
      return;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        closeApiKeyModal();
      }
    }

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isApiKeyModalOpen]);

  return (
    <div className="panel-grid panel-grid--compact">
      <section className="panel panel--hero">
        <p className="eyebrow">{copy.settings.badge}</p>
        <h2>{copy.settings.title}</h2>
        <p className="muted">{copy.settings.description}</p>
      </section>

      <section className="panel">
        <p className="eyebrow">{copy.settings.appearanceTitle}</p>
        <h3>{copy.settings.appearanceDescription}</h3>
        <div className="settings-form">
          <div className="preference-stack preference-stack--wide">
            <span>{copy.language.label}</span>
            <SegmentedControl
              ariaLabel={copy.language.label}
              onChange={setLocale}
              options={[
                { label: copy.language.zh, value: "zh-CN" },
                { label: copy.language.en, value: "en-US" }
              ]}
              value={locale}
            />
          </div>
          <div className="preference-stack preference-stack--wide">
            <span>{copy.theme.label}</span>
            <SegmentedControl
              ariaLabel={copy.theme.label}
              onChange={setTheme}
              options={[
                { label: copy.theme.system, value: "system" },
                { label: copy.theme.light, value: "light" },
                { label: copy.theme.dark, value: "dark" }
              ]}
              value={theme}
            />
          </div>
        </div>
      </section>

      <section className="panel">
        <p className="eyebrow">{copy.settings.badge}</p>
        <h3>{copy.settings.profileTitle}</h3>
        <dl className="profile-list">
          <div>
            <dt>{copy.settings.displayName}</dt>
            <dd>{user?.displayName ?? "Unknown"}</dd>
          </div>
          <div>
            <dt>{copy.settings.email}</dt>
            <dd>{user?.email ?? "Unavailable"}</dd>
          </div>
          <div>
            <dt>{copy.settings.verified}</dt>
            <dd>{user?.emailVerified ? copy.settings.yes : copy.settings.no}</dd>
          </div>
        </dl>
      </section>

      <section className="panel">
        <p className="eyebrow">{copy.settings.badge}</p>
        <h3>{copy.settings.bringYourOwnKeyTitle}</h3>
        <p className="muted">{copy.settings.bringYourOwnKeyDescription}</p>
        <div className="settings-section__header">
          <div>
            <p className="eyebrow">{copy.settings.apiKeyWorkspaceTitle}</p>
            <p className="muted">{copy.settings.apiKeyWorkspaceDescription}</p>
          </div>
          <button
            className="button button--primary"
            onClick={() => openApiKeyModal(activeProviderInfo?.name)}
            type="button"
          >
            {copy.settings.addApiKey}
          </button>
        </div>

        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}

        <div className="settings-key-workspace">
          <div className="settings-key-workspace__providers">
            <SegmentedControl
              ariaLabel={copy.settings.provider}
              onChange={setActiveProvider}
              options={providers.map((provider) => ({
                label: provider.displayName,
                value: provider.name
              }))}
              value={activeProviderInfo?.name ?? activeProvider}
            />
          </div>

          <section className="settings-key-group">
            <div className="settings-key-group__header">
              <div>
                <strong>{activeProviderInfo?.displayName ?? copy.settings.provider}</strong>
                <p className="muted">
                  {activeProviderKeys.length} {copy.settings.apiKeyCount}
                </p>
              </div>
              <button
                className="button button--ghost"
                onClick={() => openApiKeyModal(activeProviderInfo?.name)}
                type="button"
              >
                {copy.settings.addApiKey}
              </button>
            </div>

            <label className="settings-field">
              <span>{copy.settings.defaultApiKeyLabel}</span>
              <select
                onChange={(event) =>
                  setDefaultApiKeyId(activeProviderInfo?.name ?? activeProvider, event.target.value)
                }
                value={getDefaultApiKeyId(activeProviderInfo?.name ?? activeProvider)}
              >
                <option value="">{copy.settings.defaultApiKeyEmpty}</option>
                {activeProviderKeys.map((apiKey) => (
                  <option key={apiKey.id} value={apiKey.id}>
                    {apiKey.label} · ••••{apiKey.last4}
                  </option>
                ))}
              </select>
            </label>

            <div className="settings-key-group__list">
              <div className="stacked-list">
                {activeProviderKeys.map((apiKey) => (
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
                      {copy.common.remove}
                    </button>
                  </div>
                ))}
                {!activeProviderKeys.length ? (
                  <div className="stacked-list__item">
                    <span>
                      {isLoading ? copy.common.loading : copy.settings.noKeysForProvider}
                    </span>
                  </div>
                ) : null}
              </div>
            </div>
          </section>
        </div>
      </section>

      {isApiKeyModalOpen ? (
        <div
          aria-modal="true"
          className="modal-backdrop"
          onClick={closeApiKeyModal}
          role="dialog"
        >
          <section className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-card__header">
              <div>
                <p className="eyebrow">{copy.settings.badge}</p>
                <h3>{copy.settings.addApiKey}</h3>
              </div>
              <button
                className="button button--ghost"
                onClick={closeApiKeyModal}
                type="button"
              >
                {copy.common.cancel}
              </button>
            </div>

            <form className="settings-form" onSubmit={handleSubmit}>
              <label className="settings-field">
                <span>{copy.settings.provider}</span>
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
                <span>{copy.settings.label}</span>
                <input
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      label: event.target.value
                    }))
                  }
                  placeholder={copy.settings.labelPlaceholder}
                  value={form.label}
                />
              </label>
              <label className="settings-field">
                <span>{copy.settings.baseUrl}</span>
                <input
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      baseUrl: event.target.value
                    }))
                  }
                  placeholder={copy.settings.baseUrlPlaceholder}
                  value={form.baseUrl}
                />
              </label>
              <label className="settings-field">
                <span>{copy.settings.apiKey}</span>
                <input
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      key: event.target.value
                    }))
                  }
                  placeholder={copy.settings.apiKeyPlaceholder}
                  type="password"
                  value={form.key}
                />
              </label>
              <div className="button-row">
                <button className="button button--primary" disabled={isSaving} type="submit">
                  {isSaving ? copy.settings.savingKey : copy.settings.saveKey}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </div>
  );
}
