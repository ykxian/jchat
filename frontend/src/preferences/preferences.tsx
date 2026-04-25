import {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";

export type Locale = "zh-CN" | "en-US";
export type ThemePreference = "system" | "light" | "dark";
type ResolvedTheme = "light" | "dark";
type DefaultApiKeyIds = Record<string, string>;

interface PreferencesContextValue {
  copy: MessageCatalog;
  defaultApiKeyIds: DefaultApiKeyIds;
  getDefaultApiKeyId: (provider: string) => string;
  locale: Locale;
  resolvedTheme: ResolvedTheme;
  setDefaultApiKeyId: (provider: string, apiKeyId: string) => void;
  setLocale: (locale: Locale) => void;
  setTheme: (theme: ThemePreference) => void;
  theme: ThemePreference;
}

type DeepStringify<T> = T extends string ? string : { [K in keyof T]: DeepStringify<T[K]> };
type MessageCatalog = DeepStringify<(typeof messages)["zh-CN"]>;

const LOCALE_STORAGE_KEY = "jchat.locale";
const THEME_STORAGE_KEY = "jchat.theme";
const DEFAULT_API_KEYS_STORAGE_KEY = "jchat.defaultApiKeyIds";

const messages = {
  "zh-CN": {
    appName: "jchat",
    common: {
      available: "可用",
      cancel: "取消",
      create: "创建",
      createNew: "新建",
      default: "默认",
      delete: "删除",
      edit: "编辑",
      loading: "加载中...",
      noData: "暂无数据",
      offline: "离线",
      private: "私有",
      public: "公开",
      remove: "移除",
      save: "保存",
      search: "搜索",
      unavailable: "不可用",
      update: "更新"
    },
    theme: {
      label: "主题",
      light: "浅色",
      dark: "深色",
      system: "跟随系统"
    },
    language: {
      label: "语言",
      zh: "中文",
      en: "English"
    },
    shell: {
      workspace: "工作台",
      subtitle: "一个兼顾速度、结构和沉浸感的 AI 对话空间。",
      navChat: "对话",
      navMasks: "面具",
      navSettings: "设置",
      signedInAs: "当前账号",
      readyFor: "当前能力",
      feature1: "流式对话与历史会话",
      feature2: "Provider / 模型 / Mask 组合切换",
      feature3: "附件上传与本地缓存恢复",
      feature4: "统一的语言与主题偏好",
      headline: "AI Workspace",
      statsOnline: "在线工作流",
      statsStreaming: "流式响应",
      statsPersonalized: "个性化偏好",
      signOut: "退出登录",
      signingOut: "退出中...",
      publicBadge: "现代聊天界面",
      publicTitle: "把 AI 工作区做得更好看，也更顺手。",
      publicDescription:
        "登录、聊天、面具与设置页现在共用统一视觉系统，并支持中英文与浅深色切换。",
      publicFeature1: "流式消息反馈",
      publicFeature2: "动态玻璃卡片与层次背景",
      publicFeature3: "响应式布局",
      localeLabel: "当前语言",
      themeLabel: "当前主题"
    },
    auth: {
      badge: "身份验证",
      signInTitle: "欢迎回来",
      signInDescription: "使用邮箱和密码继续进入你的工作区。",
      registerTitle: "创建账号",
      registerDescription: "先注册，再通过安全会话进入完整聊天空间。",
      registeredSuccess: "账号已创建，现在登录继续。",
      signIn: "登录",
      signingIn: "登录中...",
      createAccount: "创建账号",
      creatingAccount: "创建中...",
      backToLogin: "返回登录",
      email: "邮箱",
      password: "密码",
      displayName: "昵称",
      emailPlaceholder: "alice@example.com",
      passwordPlaceholderSignIn: "请输入密码",
      passwordPlaceholderRegister: "至少 8 位，建议包含字母和数字",
      displayNamePlaceholder: "例如：Alice",
      createAccountCta: "注册新账号",
      unableToSignIn: "暂时无法登录，请稍后重试。",
      unableToRegister: "暂时无法创建账号，请稍后重试。"
    },
    session: {
      restoringTitle: "正在恢复工作区",
      restoringDescription: "正在检查刷新 Cookie 并恢复你的会话。",
      checkingTitle: "正在检查会话状态",
      checkingDescription: "如果刷新 Cookie 仍然有效，系统会自动带你回到工作区。"
    },
    chat: {
      badge: "对话",
      emptyTitle: "新的对话",
      emptyDescription: "创建会话后即可开始发送消息。",
      offlineBanner: "当前处于离线模式，缓存内容仍可查看，但暂时不能创建或发送新内容。",
      streaming: "助手正在思考并生成回复...",
      stopped: "已停止生成。",
      requestAborted: "请求已中止",
      loadProvidersError: "加载 provider 失败",
      loadConversationsError: "加载会话失败",
      loadConversationError: "加载当前会话失败",
      createConversationError: "创建会话失败",
      updateConversationError: "更新会话失败",
      uploadError: "上传附件失败",
      uploadOfflineError: "请恢复网络后再上传附件。",
      sendOfflineError: "离线模式只读，恢复网络后才能发送消息。",
      sessionExpired: "会话已过期，请重新登录。",
      sendError: "发送消息失败",
      mask: "面具",
      noMask: "不使用面具",
      provider: "Provider",
      model: "模型",
      reasoning: "推理强度",
      credential: "凭据",
      followSettings: "跟随设置",
      endpointHint: "自定义端点可使用自由输入模型名",
      messages: "条消息",
      updatedAt: "更新于",
      reasoningLabel: "推理",
      messageInput: "消息",
      messagePlaceholder: "输入你的问题、需求或想法…",
      messagePlaceholderOffline: "离线状态下仅可查看缓存，恢复网络后才能发送。",
      attachments: "附件",
      attachmentsReady: "就绪",
      attachmentsUploading: "正在上传附件...",
      attachmentsWaiting: "附件仍在处理，请等待完成后再发送。",
      attachmentsOfflineHint: "离线模式下不可上传附件。",
      send: "发送",
      stop: "停止",
      newChat: "新建对话",
      creating: "创建中...",
      offlineButton: "离线中",
      sidebarTitle: "会话列表",
      sidebarDescription: "最近的上下文会自动排到前面。",
      loadingConversations: "正在加载会话...",
      emptyConversations: "还没有会话，先创建一个开始聊天。",
      emptyConversationsOffline: "当前离线，如本地有缓存会显示在这里。",
      untitledConversation: "未命名会话",
      pinned: "已固定",
      archived: "已归档",
      justNow: "刚刚",
      minutesAgo: "分钟前",
      hoursAgo: "小时前",
      daysAgo: "天前",
      loadingMessages: "正在加载消息历史...",
      emptyMessages: "发送第一条消息，开始这段对话。",
      you: "你",
      assistant: "助手",
      tool: "工具",
      filePrefix: "文件 #",
      noConversationSelected: "先新建会话，或者从左侧选择一个继续。",
      promptTokens: "输入 Token",
      completionTokens: "输出 Token"
    },
    settings: {
      badge: "设置",
      title: "个性化与连接管理",
      description: "统一管理外观偏好、账户资料、provider 状态与自定义 API Key。",
      appearanceTitle: "界面偏好",
      appearanceDescription: "这些偏好会保存在当前浏览器中。",
      profileTitle: "账户资料",
      displayName: "昵称",
      email: "邮箱",
      verified: "邮箱已验证",
      yes: "是",
      no: "否",
      bringYourOwnKeyTitle: "自定义 API Key",
      bringYourOwnKeyDescription: "为不同 provider 保存单独凭据，也可指定自定义 base URL。",
      addApiKey: "添加 API Key",
      manageApiKeys: "已保存的 API Key",
      emptyApiKeysDescription: "还没有保存任何用户 API Key，添加后可在聊天里按 provider 使用。",
      apiKeyWorkspaceTitle: "API Key 管理",
      apiKeyWorkspaceDescription: "切换 provider 查看已有 key，并设定默认使用项。",
      defaultApiKeyTitle: "默认 API Key",
      defaultApiKeyDescription: "为每个 provider 设定默认使用的凭据，聊天页会自动跟随这里的设置。",
      defaultApiKeyEmpty: "未设置默认 API Key",
      defaultApiKeyLabel: "默认使用",
      apiKeyCount: "个密钥",
      noKeysForProvider: "该 Provider 还没有保存任何 API Key。",
      provider: "Provider",
      label: "标签",
      baseUrl: "Base URL",
      apiKey: "API Key",
      labelPlaceholder: "例如：我的 Claude Key",
      baseUrlPlaceholder: "可选，例如 https://proxy.example.com/v1",
      apiKeyPlaceholder: "粘贴 provider 密钥",
      saveKey: "保存密钥",
      savingKey: "保存中...",
      noKeys: "还没有保存任何用户 API Key。",
      loadingProviders: "正在加载 provider...",
      loadError: "加载设置失败",
      saveError: "保存 API Key 失败",
      deleteError: "删除 API Key 失败",
      requiredError: "Provider、标签和 API Key 为必填项。"
    },
    masks: {
      badge: "面具",
      title: "Prompt 预设",
      description: "用一套可复用的身份、语气和模型默认值，快速开始新对话。",
      searchPlaceholder: "按名称或标签搜索",
      searchError: "搜索面具失败",
      loadError: "加载面具失败",
      saveError: "保存面具失败",
      deleteError: "删除面具失败",
      createConversationError: "通过面具创建会话失败",
      createTitle: "新建自定义面具",
      editTitle: "编辑你的面具",
      createBadge: "创建",
      editBadge: "编辑",
      name: "名称",
      avatar: "头像",
      defaultProvider: "默认 Provider",
      defaultModel: "默认模型",
      tags: "标签",
      systemPrompt: "System Prompt",
      visibleToOthers: "对其他已登录用户可见",
      noOverride: "不覆盖",
      avatarPlaceholder: "可填 emoji 或短标签",
      modelPlaceholder: "可选模型 ID",
      tagsPlaceholder: "例如：code, review, quality",
      saveCreating: "创建面具",
      saveUpdating: "更新面具",
      saving: "保存中...",
      libraryTitle: "面具库",
      loadingMasks: "正在加载面具...",
      builtin: "内置",
      newChat: "新建对话",
      defaultProviderText: "默认 provider",
      defaultModelText: "默认模型"
    }
  },
  "en-US": {
    appName: "jchat",
    common: {
      available: "Available",
      cancel: "Cancel",
      create: "Create",
      createNew: "Create",
      default: "Default",
      delete: "Delete",
      edit: "Edit",
      loading: "Loading...",
      noData: "No data",
      offline: "Offline",
      private: "Private",
      public: "Public",
      remove: "Remove",
      save: "Save",
      search: "Search",
      unavailable: "Unavailable",
      update: "Update"
    },
    theme: {
      label: "Theme",
      light: "Light",
      dark: "Dark",
      system: "System"
    },
    language: {
      label: "Language",
      zh: "中文",
      en: "English"
    },
    shell: {
      workspace: "Workspace",
      subtitle: "An AI workspace tuned for speed, structure, and focus.",
      navChat: "Chat",
      navMasks: "Masks",
      navSettings: "Settings",
      signedInAs: "Signed In As",
      readyFor: "Ready For",
      feature1: "Streaming chat with persisted history",
      feature2: "Provider, model, and mask switching",
      feature3: "Attachments and local cache recovery",
      feature4: "Unified language and theme preferences",
      headline: "AI Workspace",
      statsOnline: "Online workflow",
      statsStreaming: "Streaming replies",
      statsPersonalized: "Personalized UI",
      signOut: "Sign Out",
      signingOut: "Signing Out...",
      publicBadge: "Modern Chat UI",
      publicTitle: "A cleaner, more interactive AI workspace.",
      publicDescription:
        "Auth, chat, masks, and settings now share one visual system with bilingual copy and light or dark themes.",
      publicFeature1: "Streaming feedback",
      publicFeature2: "Layered glass surfaces",
      publicFeature3: "Responsive layout",
      localeLabel: "Language",
      themeLabel: "Theme"
    },
    auth: {
      badge: "Auth",
      signInTitle: "Welcome back",
      signInDescription: "Use your email and password to continue into the workspace.",
      registerTitle: "Create your account",
      registerDescription: "Register first, then enter the full chat workspace through a secure session.",
      registeredSuccess: "Account created. Sign in to continue.",
      signIn: "Sign In",
      signingIn: "Signing In...",
      createAccount: "Create Account",
      creatingAccount: "Creating...",
      backToLogin: "Back To Login",
      email: "Email",
      password: "Password",
      displayName: "Display Name",
      emailPlaceholder: "alice@example.com",
      passwordPlaceholderSignIn: "Enter your password",
      passwordPlaceholderRegister: "At least 8 characters. Letters and digits recommended",
      displayNamePlaceholder: "e.g. Alice",
      createAccountCta: "Create Account",
      unableToSignIn: "Unable to sign in right now. Please try again.",
      unableToRegister: "Unable to create your account right now. Please try again."
    },
    session: {
      restoringTitle: "Restoring your workspace",
      restoringDescription: "Checking the refresh cookie and restoring your session.",
      checkingTitle: "Checking your session",
      checkingDescription: "If the refresh cookie is still valid, the app will take you back in."
    },
    chat: {
      badge: "Chat",
      emptyTitle: "New conversation",
      emptyDescription: "Create a conversation and start sending messages.",
      offlineBanner:
        "You're offline. Cached content stays readable, but creating or sending new content is disabled until the network returns.",
      streaming: "Assistant is responding...",
      stopped: "Streaming stopped.",
      requestAborted: "Request aborted",
      loadProvidersError: "Failed to load providers",
      loadConversationsError: "Failed to load conversations",
      loadConversationError: "Failed to load conversation",
      createConversationError: "Failed to create conversation",
      updateConversationError: "Failed to update conversation",
      uploadError: "Failed to upload attachments",
      uploadOfflineError: "Reconnect before uploading attachments.",
      sendOfflineError: "Offline mode is read-only. Reconnect to send messages.",
      sessionExpired: "Session expired. Please sign in again.",
      sendError: "Failed to send message",
      mask: "Mask",
      noMask: "No mask",
      provider: "Provider",
      model: "Model",
      reasoning: "Reasoning",
      credential: "Credential",
      followSettings: "Follow settings",
      endpointHint: "Custom endpoints support freeform model ids",
      messages: "messages",
      updatedAt: "Updated",
      reasoningLabel: "Reasoning",
      messageInput: "Message",
      messagePlaceholder: "Ask for anything, refine an idea, or continue the thread...",
      messagePlaceholderOffline: "Offline mode is read-only. Reconnect to send a message.",
      attachments: "Attachments",
      attachmentsReady: "Ready",
      attachmentsUploading: "Uploading attachments...",
      attachmentsWaiting: "Wait for attachments to finish processing before sending.",
      attachmentsOfflineHint: "Attachments are disabled while offline.",
      send: "Send",
      stop: "Stop",
      newChat: "New Chat",
      creating: "Creating...",
      offlineButton: "Offline",
      sidebarTitle: "Conversations",
      sidebarDescription: "Recent context stays at the top.",
      loadingConversations: "Loading conversations...",
      emptyConversations: "No conversations yet. Create one to start chatting.",
      emptyConversationsOffline: "You're offline. Cached conversations will appear here when available.",
      untitledConversation: "Untitled conversation",
      pinned: "Pinned",
      archived: "Archived",
      justNow: "just now",
      minutesAgo: "min ago",
      hoursAgo: "h ago",
      daysAgo: "d ago",
      loadingMessages: "Loading message history...",
      emptyMessages: "Send the first message to start this conversation.",
      you: "You",
      assistant: "Assistant",
      tool: "Tool",
      filePrefix: "File #",
      noConversationSelected: "Create a conversation first, or pick one from the sidebar.",
      promptTokens: "Prompt tokens",
      completionTokens: "Completion tokens"
    },
    settings: {
      badge: "Settings",
      title: "Preferences and connection management",
      description: "Manage appearance, account details, provider availability, and personal API keys in one place.",
      appearanceTitle: "Appearance",
      appearanceDescription: "These preferences are stored in this browser.",
      profileTitle: "Profile",
      displayName: "Display name",
      email: "Email",
      verified: "Email verified",
      yes: "Yes",
      no: "No",
      bringYourOwnKeyTitle: "Bring your own key",
      bringYourOwnKeyDescription: "Save credentials per provider and optionally override the base URL.",
      addApiKey: "Add API key",
      manageApiKeys: "Saved API keys",
      emptyApiKeysDescription: "No user API keys saved yet. Add one to use provider-specific credentials in chat.",
      apiKeyWorkspaceTitle: "API key workspace",
      apiKeyWorkspaceDescription: "Switch providers to inspect saved keys and choose the default one.",
      defaultApiKeyTitle: "Default API keys",
      defaultApiKeyDescription: "Pick the preferred credential for each provider. Chat will follow these defaults automatically.",
      defaultApiKeyEmpty: "No default API key selected",
      defaultApiKeyLabel: "Default",
      apiKeyCount: "key(s)",
      noKeysForProvider: "No API keys saved for this provider yet.",
      provider: "Provider",
      label: "Label",
      baseUrl: "Base URL",
      apiKey: "API key",
      labelPlaceholder: "e.g. Personal Claude key",
      baseUrlPlaceholder: "Optional, e.g. https://proxy.example.com/v1",
      apiKeyPlaceholder: "Paste provider key",
      saveKey: "Save Key",
      savingKey: "Saving...",
      noKeys: "No user API keys saved yet.",
      loadingProviders: "Loading providers...",
      loadError: "Failed to load settings",
      saveError: "Failed to save API key",
      deleteError: "Failed to delete API key",
      requiredError: "Provider, label, and API key are required."
    },
    masks: {
      badge: "Masks",
      title: "Prompt presets",
      description: "Use reusable personas, tones, and model defaults to start new conversations faster.",
      searchPlaceholder: "Search by name or tag",
      searchError: "Failed to search masks",
      loadError: "Failed to load masks",
      saveError: "Failed to save mask",
      deleteError: "Failed to delete mask",
      createConversationError: "Failed to create conversation from mask",
      createTitle: "New custom mask",
      editTitle: "Update your mask",
      createBadge: "Create",
      editBadge: "Edit",
      name: "Name",
      avatar: "Avatar",
      defaultProvider: "Default provider",
      defaultModel: "Default model",
      tags: "Tags",
      systemPrompt: "System prompt",
      visibleToOthers: "Visible to other signed-in users",
      noOverride: "No override",
      avatarPlaceholder: "Emoji or short label",
      modelPlaceholder: "Optional model id",
      tagsPlaceholder: "code, review, quality",
      saveCreating: "Create mask",
      saveUpdating: "Update mask",
      saving: "Saving...",
      libraryTitle: "Mask library",
      loadingMasks: "Loading masks...",
      builtin: "Builtin",
      newChat: "New chat",
      defaultProviderText: "default provider",
      defaultModelText: "default model"
    }
  }
};

const PreferencesContext = createContext<PreferencesContextValue | null>(null);

function getStoredLocale(): Locale {
  if (typeof window === "undefined") {
    return "zh-CN";
  }

  const value = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return value === "en-US" || value === "zh-CN" ? value : "zh-CN";
}

function getStoredTheme(): ThemePreference {
  if (typeof window === "undefined") {
    return "system";
  }

  const value = window.localStorage.getItem(THEME_STORAGE_KEY);
  return value === "light" || value === "dark" || value === "system" ? value : "system";
}

function getStoredDefaultApiKeyIds(): DefaultApiKeyIds {
  if (typeof window === "undefined") {
    return {};
  }

  try {
    const value = window.localStorage.getItem(DEFAULT_API_KEYS_STORAGE_KEY);

    if (!value) {
      return {};
    }

    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" ? parsed as DefaultApiKeyIds : {};
  } catch {
    return {};
  }
}

function resolveTheme(theme: ThemePreference) {
  if (
    theme === "system" &&
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-color-scheme: dark)").matches
  ) {
    return "dark";
  }

  return theme === "system" ? "light" : theme;
}

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(() => getStoredLocale());
  const [theme, setTheme] = useState<ThemePreference>(() => getStoredTheme());
  const [defaultApiKeyIds, setDefaultApiKeyIds] = useState<DefaultApiKeyIds>(() =>
    getStoredDefaultApiKeyIds()
  );
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>(() => resolveTheme(getStoredTheme()));

  useEffect(() => {
    const mediaQuery =
      typeof window === "undefined" ? null : window.matchMedia("(prefers-color-scheme: dark)");

    function updateTheme() {
      setResolvedTheme(resolveTheme(theme));
    }

    updateTheme();
    mediaQuery?.addEventListener("change", updateTheme);

    return () => {
      mediaQuery?.removeEventListener("change", updateTheme);
    };
  }, [theme]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    }
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    }
  }, [theme]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(DEFAULT_API_KEYS_STORAGE_KEY, JSON.stringify(defaultApiKeyIds));
    }
  }, [defaultApiKeyIds]);

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
  }, [resolvedTheme]);

  const value = useMemo<PreferencesContextValue>(
    () => ({
      copy: messages[locale],
      defaultApiKeyIds,
      getDefaultApiKeyId: (provider: string) => defaultApiKeyIds[provider] ?? "",
      locale,
      resolvedTheme,
      setDefaultApiKeyId: (provider: string, apiKeyId: string) => {
        setDefaultApiKeyIds((current) => {
          if (!apiKeyId) {
            const { [provider]: _removed, ...rest } = current;
            return rest;
          }

          return {
            ...current,
            [provider]: apiKeyId
          };
        });
      },
      setLocale,
      setTheme,
      theme
    }),
    [defaultApiKeyIds, locale, resolvedTheme, theme]
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  const context = useContext(PreferencesContext);

  if (!context) {
    throw new Error("usePreferences must be used within PreferencesProvider");
  }

  return context;
}
