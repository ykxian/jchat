# PWA Module

> Progressive Web App：可安装、app shell 缓存、离线读历史。
>
> Context：M4 阶段引入。在 v1 之前，前端就是普通 SPA。PWA 让 jchat 能像原生应用一样安装到桌面 / 手机，关网也能打开看历史。

---

## 1. 职责边界

**做**：
- Web App Manifest（`manifest.webmanifest`）
- Service Worker（通过 Workbox + `vite-plugin-pwa`）
- App Shell 缓存策略（HTML / JS / CSS / 字体 / 图标）
- 离线历史读（从 IndexedDB 读）
- 安装横幅 / 提示组件
- 版本升级提示

**不做**：
- 离线发消息（网络回来后补发）— v2 再说
- Push notifications — v2
- 后台同步（Background Sync API）— v2

---

## 2. 技术栈

- `vite-plugin-pwa@0.20+` — Vite 插件，生成 manifest + service worker
- Workbox — Google 的 SW 库，底层被 `vite-plugin-pwa` 封装
- Dexie — IndexedDB 抽象（已在 `frontend.md` 用过）

---

## 3. Vite 配置

`frontend/vite.config.ts`：

```ts
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "prompt",                 // 不自动升级，提示用户
      includeAssets: ["favicon.svg", "apple-touch-icon.png", "safari-pinned-tab.svg"],
      manifest: {
        name: "jchat",
        short_name: "jchat",
        description: "多 LLM 对话应用（仿 NextChat）",
        theme_color: "#0b0b0e",
        background_color: "#0b0b0e",
        display: "standalone",
        start_url: "/chat",
        scope: "/",
        icons: [
          { src: "/pwa-192x192.png", sizes: "192x192", type: "image/png" },
          { src: "/pwa-512x512.png", sizes: "512x512", type: "image/png" },
          { src: "/pwa-512x512.png", sizes: "512x512", type: "image/png", purpose: "maskable" }
        ],
      },
      workbox: {
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api\//],
        globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith("/api/"),
            handler: "NetworkOnly",            // API 请求不缓存
          },
          {
            urlPattern: ({ request }) =>
              request.destination === "image" ||
              request.destination === "font" ||
              request.destination === "style" ||
              request.destination === "script",
            handler: "StaleWhileRevalidate",
            options: { cacheName: "assets", expiration: { maxAgeSeconds: 30 * 24 * 60 * 60 } }
          },
        ],
      },
    }),
  ],
});
```

---

## 4. 资源

`frontend/public/` 下需要：
- `favicon.svg`（主 icon）
- `pwa-192x192.png`、`pwa-512x512.png`（可由一个 SVG 生成，脚本或 https://realfavicongenerator.net/）
- `apple-touch-icon.png`（180x180）
- `safari-pinned-tab.svg`（黑白线稿）

可以先用简单的字母图标（"J" 字），v2 再做完整的 brand。

---

## 5. 注册与升级提示

在 `src/main.tsx` 注入 SW 注册 + 升级提示组件：

```ts
import { registerSW } from "virtual:pwa-register";

const updateSW = registerSW({
  onNeedRefresh() {
    // 显示"有新版本，点击刷新"提示
    window.dispatchEvent(new CustomEvent("pwa-need-refresh"));
  },
  onOfflineReady() {
    window.dispatchEvent(new CustomEvent("pwa-offline-ready"));
  },
});

// 监听事件 → 在 layout 组件中渲染 toast
```

`src/components/layout/PwaUpdater.tsx`：

```tsx
export function PwaUpdater() {
  const [needRefresh, setNeedRefresh] = useState(false);
  useEffect(() => {
    const h = () => setNeedRefresh(true);
    window.addEventListener("pwa-need-refresh", h);
    return () => window.removeEventListener("pwa-need-refresh", h);
  }, []);
  if (!needRefresh) return null;
  return (
    <Toast>
      新版本可用
      <Button onClick={() => location.reload()}>刷新</Button>
    </Toast>
  );
}
```

---

## 6. 安装横幅

浏览器会自动处理 `beforeinstallprompt` 事件。我们暴露一个按钮在 Settings 页面给用户手动触发：

```tsx
export function InstallButton() {
  const [promptEvent, setPromptEvent] = useState<BeforeInstallPromptEvent | null>(null);

  useEffect(() => {
    const h = (e: Event) => {
      e.preventDefault();
      setPromptEvent(e as BeforeInstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", h);
    return () => window.removeEventListener("beforeinstallprompt", h);
  }, []);

  if (!promptEvent) return null;

  return (
    <Button onClick={async () => {
      await promptEvent.prompt();
      const { outcome } = await promptEvent.userChoice;
      if (outcome === "accepted") setPromptEvent(null);
    }}>
      安装到桌面
    </Button>
  );
}
```

---

## 7. 离线策略

### 7.1 静态资源
上面 Workbox 配置里 `StaleWhileRevalidate` — 用户打开应用时显示缓存，同时后台拉新版本；下次打开时生效。

### 7.2 路由回退
`navigateFallback: "/index.html"` — 离线访问任意 SPA 路由都返回 `index.html`，由 React Router 处理。但 `/api/*` 不走这条规则（由 `navigateFallbackDenylist`）。

### 7.3 数据层（IndexedDB）
- `conversationStore` 的加载逻辑：
  ```
  1. 先从 Dexie 读本地缓存 → 立即渲染（秒开）
  2. 异步 fetch /conversations → 成功则覆盖；失败（离线）则保持本地数据
  ```
- 同理 `messages`（当前会话）：先本地 → 再拉 → 合并。
- **写操作**（发消息、编辑 title）：仅在线可用；离线态 UI 置灰 + 提示"当前离线"。

### 7.4 离线提示组件

```tsx
export function OfflineIndicator() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => {
      window.removeEventListener("online", up);
      window.removeEventListener("offline", down);
    };
  }, []);
  if (online) return null;
  return <div className="banner">当前离线 — 可阅读历史，发送消息请检查网络</div>;
}
```

---

## 8. 测试

### 手工
- Chrome DevTools → Application → Service Workers 查看注册状态
- DevTools → Network → Offline 勾选 → 刷新应用，验证能加载 + 能查看历史
- DevTools → Application → Manifest 验证字段正确
- Lighthouse PWA 评分（目标 ≥ 90）

### 自动化
- Playwright 里模拟离线场景（`page.context().setOffline(true)`）验证历史可读

---

## 9. 部署注意

- **`sw.js` 不能缓存**：nginx 配置里已加 `Cache-Control: no-cache`（见 [DEPLOYMENT.md](../DEPLOYMENT.md#53-infranginxnginxconf)）。否则用户永远拿不到新 SW。
- **HTTPS 必需**：SW 只能在 HTTPS 或 `localhost` 下工作。dev 是 localhost 没事；prod 一定要上 HTTPS（nginx 前面加 Caddy / Let's Encrypt）。v1 DEPLOYMENT.md 未自带 HTTPS，外面套一层。
- **Scope**：`scope: "/"` 允许 SW 控制整个站点。若要限制，改 `/chat` 只在 chat 区域生效。
- **Icons 必须齐全**：至少 192 + 512；否则 Chrome 不触发安装提示。

---

## 10. 常见陷阱

- **SW 更新不生效**：用户的旧 SW 长期控制页面。`registerType: "prompt"` 让用户手动点刷新；或 `autoUpdate` 强制（但会打断正在用的页面）。
- **开发时 SW 持续困扰**：`vite-plugin-pwa` 的 `devOptions.enabled` 默认 false，dev 下不注册 SW，省心。要测 SW → `npm run preview`。
- **fetch + ReadableStream 不能缓存**：SSE 响应没法走 Workbox cache（也不该）；确保 `NetworkOnly` 配置对 `/api/` 生效。
- **Cookie 与 SW**：SW 代理的请求默认不带 cookie。需要 `fetch(url, { credentials: "include" })`。
- **Safari 支持**：iOS Safari 对 PWA 支持比 Chrome 弱；start_url 可能不受尊重；standalone 图标可能需要 `apple-touch-icon`。v1 以 Chrome 为准，Safari best-effort。

---

## 11. v2+ 方向

- **Background Sync**：离线发消息排队，网络回来后自动补发。
- **Push Notifications**：新消息到达的推送（需要后端 Web Push 支持）。
- **Periodic Background Sync**：周期性后台拉新消息（Chrome 限 PWA 已安装）。
- **File Handling API**：把 jchat 注册为特定文件类型的处理器（如 `.md` → 打开对应 mask 新建会话）。
