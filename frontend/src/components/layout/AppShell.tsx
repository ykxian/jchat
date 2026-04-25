import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { authApi } from "../../api/auth";
import { usePreferences } from "../../preferences/preferences";
import { useAuthStore } from "../../stores/authStore";

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const { copy, resolvedTheme } = usePreferences();
  const navigation = [
    { to: "/chat", label: copy.shell.navChat },
    { to: "/masks", label: copy.shell.navMasks },
    { to: "/settings", label: copy.shell.navSettings }
  ];
  const currentNav = navigation.find((item) => location.pathname.startsWith(item.to));

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
          <div className="shell-brand">
            <span className="brand-mark brand-mark--compact">{copy.appName}</span>
          </div>
          <p className="shell-sidebar__label">{copy.shell.workspace}</p>
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
      </aside>

      <div className="shell-main">
        <header className="shell-header">
          <div className="shell-header__copy">
            <h2>{currentNav?.label ?? copy.shell.headline}</h2>
          </div>
          <div className="shell-header__actions">
            <span className="status-chip">{resolvedTheme}</span>
            <button
              className="button button--ghost"
              disabled={isLoggingOut}
              onClick={handleLogout}
              type="button"
            >
              {isLoggingOut ? copy.shell.signingOut : copy.shell.signOut}
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
