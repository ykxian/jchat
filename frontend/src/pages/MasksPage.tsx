import { FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { masksApi } from "../api/masks";
import { conversationsApi } from "../api/conversations";
import type { CreateMaskPayload, Mask, ProviderInfo } from "../api/types";
import { providersApi } from "../api/providers";

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
          setPageError(error instanceof Error ? error.message : "Failed to load masks");
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
  }, []);

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
      setPageError(error instanceof Error ? error.message : "Failed to save mask");
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
      setPageError(error instanceof Error ? error.message : "Failed to delete mask");
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
      setPageError(error instanceof Error ? error.message : "Failed to create conversation from mask");
    }
  }

  async function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await refreshMasks(search.trim() || undefined);
      setPageError(null);
    } catch (error) {
      setPageError(error instanceof Error ? error.message : "Failed to search masks");
    }
  }

  return (
    <div className="panel-grid panel-grid--masks">
      <section className="panel panel--hero">
        <p className="eyebrow">Masks</p>
        <h2>Prompt presets</h2>
        <p className="muted">
          Phase 10 adds reusable prompt templates. Pick one to start a conversation faster,
          or create your own private mask with provider defaults.
        </p>
        <form className="search-row" onSubmit={handleSearchSubmit}>
          <input
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name or tag"
            value={search}
          />
          <button className="button button--ghost" type="submit">
            Search
          </button>
        </form>
      </section>

      <section className="panel">
        <p className="eyebrow">{editingMaskId ? "Edit" : "Create"}</p>
        <h3>{editingMaskId ? "Update your mask" : "New custom mask"}</h3>
        <form className="settings-form" onSubmit={handleSubmit}>
          <label className="settings-field">
            <span>Name</span>
            <input
              onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
              value={form.name}
            />
          </label>
          <label className="settings-field">
            <span>Avatar</span>
            <input
              onChange={(event) => setForm((current) => ({ ...current, avatar: event.target.value }))}
              placeholder="Emoji or short label"
              value={form.avatar ?? ""}
            />
          </label>
          <label className="settings-field">
            <span>Default provider</span>
            <select
              onChange={(event) =>
                setForm((current) => ({ ...current, defaultProvider: event.target.value }))
              }
              value={form.defaultProvider ?? ""}
            >
              <option value="">No override</option>
              {providers.map((provider) => (
                <option key={provider.name} value={provider.name}>
                  {provider.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="settings-field">
            <span>Default model</span>
            <input
              onChange={(event) =>
                setForm((current) => ({ ...current, defaultModel: event.target.value }))
              }
              placeholder="Optional model id"
              value={form.defaultModel ?? ""}
            />
          </label>
          <label className="settings-field">
            <span>Tags</span>
            <input
              onChange={(event) =>
                setForm((current) => ({ ...current, tags: stringToTags(event.target.value) }))
              }
              placeholder="code, review, quality"
              value={tagsToString(form.tags ?? [])}
            />
          </label>
          <label className="settings-field">
            <span>System prompt</span>
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
            <span>Visible to other signed-in users</span>
          </label>
          <div className="button-row">
            <button className="button button--primary" disabled={isSaving} type="submit">
              {isSaving ? "Saving..." : editingMaskId ? "Update mask" : "Create mask"}
            </button>
            {editingMaskId ? (
              <button className="button button--ghost" onClick={resetForm} type="button">
                Cancel
              </button>
            ) : null}
          </div>
        </form>
        {pageError ? <div className="auth-feedback auth-feedback--error">{pageError}</div> : null}
      </section>

      <section className="panel">
        <p className="eyebrow">Library</p>
        <h3>Available masks</h3>
        {isLoading ? <p className="muted">Loading masks...</p> : null}
        <div className="mask-grid">
          {masks.map((mask) => (
            <article className="mask-card" key={mask.id}>
              <div className="mask-card__header">
                <div>
                  <strong>{mask.avatar ? `${mask.avatar} ${mask.name}` : mask.name}</strong>
                  <p className="muted">
                    {(mask.defaultProvider ?? "default provider") + " / " + (mask.defaultModel ?? "default model")}
                  </p>
                </div>
                <span className="pill">{mask.ownerId ? (mask.isPublic ? "Public" : "Private") : "Builtin"}</span>
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
                  New chat
                </button>
                {mask.ownerId ? (
                  <button className="button button--ghost" onClick={() => startEditing(mask)} type="button">
                    Edit
                  </button>
                ) : null}
                {mask.ownerId ? (
                  <button
                    className="button button--ghost"
                    onClick={() => void handleDelete(mask.id)}
                    type="button"
                  >
                    Delete
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
