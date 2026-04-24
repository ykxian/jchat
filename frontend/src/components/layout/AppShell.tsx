import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { authApi } from "../../api/auth";
import { useAuthStore } from "../../stores/authStore";

const navigation = [
  { to: "/chat", label: "Chat" },
  { to: "/settings", label: "Settings" }
];

export function AppShell() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);

    try {
      await authApi.logout();
    } finally {
      setIsLoggingOut(false);
      navigate("/login", { replace: true });
    }
  }

  return (
    <div className="shell">
      <aside className="shell-sidebar">
        <div className="shell-sidebar__header">
          <p className="eyebrow">jchat</p>
          <h1>Workspace</h1>
          <p className="muted">
            Auth, conversation history, and streaming chat now share the same
            protected shell.
          </p>
        </div>

        <nav aria-label="Primary" className="shell-nav">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              className={({ isActive }) =>
                isActive ? "shell-nav__link shell-nav__link--active" : "shell-nav__link"
              }
              to={item.to}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <section className="shell-note">
          <p className="eyebrow">Signed In As</p>
          <div className="shell-account">
            <strong>{user?.displayName ?? "Unknown User"}</strong>
            <span>{user?.email ?? "No email loaded"}</span>
          </div>
        </section>

        <section className="shell-note">
          <p className="eyebrow">Ready For</p>
          <ul className="feature-list">
            <li>Conversation list and message reload after refresh</li>
            <li>Streaming chat linked to authenticated requests</li>
            <li>Settings and provider expansion in later phases</li>
          </ul>
        </section>
      </aside>

      <div className="shell-main">
        <header className="shell-header">
          <div>
            <p className="eyebrow">Workspace</p>
            <h2>React SPA</h2>
          </div>
          <div className="button-row">
            <span className="pill">Auth + conversations + SSE</span>
            <button
              className="button button--ghost"
              disabled={isLoggingOut}
              onClick={handleLogout}
              type="button"
            >
              {isLoggingOut ? "Signing Out..." : "Sign Out"}
            </button>
          </div>
        </header>

        <main className="shell-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
