import { FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { masksApi } from "../api/masks";
import { conversationsApi } from "../api/conversations";
import type { CreateMaskPayload, Mask, ProviderInfo } from "../api/types";
import { providersApi } from "../api/providers";
import { usePreferences } from "../preferences/preferences";

const EMPTY_FORM: CreateMaskPayload = {
  avatar: "",
  defaultModel: "",
  defaultProvider: "",
  isPublic: false,
  name: "",
  systemPrompt: "",
  tags: []
};

function tagsToString(tags: string[]) {
  return tags.join(", ");
}

function stringToTags(value: string) {
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function getDefaultSelection(providers: ProviderInfo[]) {
  const available = providers.find((provider) => provider.available) ?? providers[0];
  const model = available?.models[0];

  return {
    model: model?.id ?? null,
    provider: available?.name ?? null
  };
}

export function MasksPage() {
  const navigate = useNavigate();
  const { copy } = usePreferences();
  const [providers, setProviders] = useState<ProviderInfo[]>([]);
  const [masks, setMasks] = useState<Mask[]>([]);
  const [editingMaskId, setEditingMaskId] = useState<string | null>(null);
  const [form, setForm] = useState<CreateMaskPayload>(EMPTY_FORM);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [maskResponse, providerResponse] = await Promise.all([
          masksApi.list(),
          providersApi.list()
        ]);
        if (cancelled) {
          return;
        }
        setMasks(maskResponse.items);
        setProviders(providerResponse.items);
        setPageError(null);
      } catch (error) {
        if (!cancelled) {
          setPageError(error instanceof Error ? error.message : copy.masks.loadError);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, [copy.masks.loadError]);

  async function refreshMasks(query?: string) {
    const response = await masksApi.list({ q: query ?? search });
    setMasks(response.items);
  }

  function startEditing(mask: Mask) {
    setEditingMaskId(mask.ownerId ? mask.id : null);
    setForm({
      avatar: mask.avatar ?? "",
      defaultModel: mask.defaultModel ?? "",
      defaultProvider: mask.defaultProvider ?? "",
      isPublic: mask.isPublic,
      name: mask.name,
      systemPrompt: mask.systemPrompt,
      tags: mask.tags
    });
  }

  function resetForm() {
    setEditingMaskId(null);
    setForm(EMPTY_FORM);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);

    try {
      const payload: CreateMaskPayload = {
        avatar: form.avatar?.trim() || null,
        defaultModel: form.defaultModel?.trim() || null,
        defaultProvider: form.defaultProvider?.trim() || null,
        isPublic: Boolean(form.isPublic),
        name: form.name.trim(),
        systemPrompt: form.systemPrompt.trim(),
        tags: form.tags ?? []
      };

      if (editingMaskId) {
        await masksApi.update(editingMaskId, payload);
      } else {
        await masksApi.create(payload);
      }

      await refreshMasks();
      resetForm();
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : copy.masks.saveError);
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(id: string) {
    try {
      await masksApi.delete(id);
      await refreshMasks();
      if (editingMaskId === id) {
        resetForm();
      }
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : copy.masks.deleteError);
    }
  }

  async function handleCreateConversation(mask: Mask) {
    try {
      const defaults = getDefaultSelection(providers);
      const conversation = await conversationsApi.create({
        maskId: mask.id,
        model: mask.defaultModel ?? defaults.model ?? undefined,
        provider: mask.defaultProvider ?? defaults.provider ?? undefined,
        title: null
      });
      navigate(`/chat/${conversation.id}`);
    } catch (error) {
      setPageError(
        error instanceof Error ? error.message : copy.masks.createConversationError
      );
    }
  }

  async function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await refreshMasks(search.trim() || undefined);
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : copy.masks.searchError);
    }
  }

  return (
    <div className="panel-grid panel-grid--masks">
      <section className="panel panel--hero">
        <p className="eyebrow">{copy.masks.badge}</p>
        <h2>{copy.masks.title}</h2>
        <p className="muted">{copy.masks.description}</p>
        <form className="search-row" onSubmit={handleSearchSubmit}>
          <input
            onChange={(event) => setSearch(event.target.value)}
            placeholder={copy.masks.searchPlaceholder}
            value={search}
          />
          <button className="button button--ghost" type="submit">
            {copy.common.search}
          </button>
        </form>
      </section>

      <section className="panel">
        <p className="eyebrow">{editingMaskId ? copy.masks.editBadge : copy.masks.createBadge}</p>
        <h3>{editingMaskId ? copy.masks.editTitle : copy.masks.createTitle}</h3>
        <form className="settings-form" onSubmit={handleSubmit}>
          <label className="settings-field">
            <span>{copy.masks.name}</span>
            <input
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              value={form.name}
            />
          </label>
          <label className="settings-field">
            <span>{copy.masks.avatar}</span>
            <input
              onChange={(event) => setForm((current) => ({ ...current, avatar: event.target.value }))}
              placeholder={copy.masks.avatarPlaceholder}
              value={form.avatar ?? ""}
            />
          </label>
          <label className="settings-field">
            <span>{copy.masks.defaultProvider}</span>
            <select
              onChange={(event) =>
                setForm((current) => ({ ...current, defaultProvider: event.target.value }))
              }
              value={form.defaultProvider ?? ""}
            >
              <option value="">{copy.masks.noOverride}</option>
              {providers.map((provider) => (
                <option key={provider.name} value={provider.name}>
                  {provider.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="settings-field">
            <span>{copy.masks.defaultModel}</span>
            <input
              onChange={(event) =>
                setForm((current) => ({ ...current, defaultModel: event.target.value }))
              }
              placeholder={copy.masks.modelPlaceholder}
              value={form.defaultModel ?? ""}
            />
          </label>
          <label className="settings-field">
            <span>{copy.masks.tags}</span>
            <input
              onChange={(event) =>
                setForm((current) => ({ ...current, tags: stringToTags(event.target.value) }))
              }
              placeholder={copy.masks.tagsPlaceholder}
              value={tagsToString(form.tags ?? [])}
            />
          </label>
          <label className="settings-field">
            <span>{copy.masks.systemPrompt}</span>
            <textarea
              className="settings-textarea"
              onChange={(event) =>
                setForm((current) => ({ ...current, systemPrompt: event.target.value }))
              }
              rows={8}
              value={form.systemPrompt}
            />
          </label>
          <label className="checkbox-row">
            <input
              checked={Boolean(form.isPublic)}
              onChange={(event) =>
                setForm((current) => ({ ...current, isPublic: event.target.checked }))
              }
              type="checkbox"
            />
            <span>{copy.masks.visibleToOthers}</span>
          </label>
          <div className="button-row">
            <button className="button button--primary" disabled={isSaving} type="submit">
              {isSaving
                ? copy.masks.saving
                : editingMaskId
                  ? copy.masks.saveUpdating
                  : copy.masks.saveCreating}
            </button>
            {editingMaskId ? (
              <button className="button button--ghost" onClick={resetForm} type="button">
                {copy.common.cancel}
              </button>
            ) : null}
          </div>
        </form>
        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}
      </section>

      <section className="panel">
        <p className="eyebrow">{copy.masks.badge}</p>
        <h3>{copy.masks.libraryTitle}</h3>
        {isLoading ? <p className="muted">{copy.masks.loadingMasks}</p> : null}
        <div className="mask-grid">
          {masks.map((mask) => (
            <article className="mask-card" key={mask.id}>
              <div className="mask-card__header">
                <div>
                  <strong>{mask.avatar ? `${mask.avatar} ${mask.name}` : mask.name}</strong>
                  <p className="muted">
                    {(mask.defaultProvider ?? copy.masks.defaultProviderText) +
                      " / " +
                      (mask.defaultModel ?? copy.masks.defaultModelText)}
                  </p>
                </div>
                <span className="pill">
                  {mask.ownerId
                    ? mask.isPublic
                      ? copy.common.public
                      : copy.common.private
                    : copy.masks.builtin}
                </span>
              </div>
              <p className="mask-card__prompt">{mask.systemPrompt}</p>
              <div className="status-list">
                {mask.tags.map((tag) => (
                  <span key={`${mask.id}-${tag}`}>{tag}</span>
                ))}
              </div>
              <div className="button-row">
                <button
                  className="button button--primary"
                  onClick={() => void handleCreateConversation(mask)}
                  type="button"
                >
                  {copy.masks.newChat}
                </button>
                {mask.ownerId ? (
                  <button className="button button--ghost" onClick={() => startEditing(mask)} type="button">
                    {copy.common.edit}
                  </button>
                ) : null}
                {mask.ownerId ? (
                  <button
                    className="button button--ghost"
                    onClick={() => void handleDelete(mask.id)}
                    type="button"
                  >
                    {copy.common.delete}
                  </button>
                ) : null}
              </div>
            </article>
          ))}
          {!isLoading && !masks.length ? <p className="muted">No masks found.</p> : null}
        </div>
      </section>
    </div>
  );
}
