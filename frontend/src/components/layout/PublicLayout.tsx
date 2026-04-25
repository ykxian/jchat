import { Link, NavLink, Outlet } from "react-router-dom";
import { SegmentedControl } from "../ui/SegmentedControl";
import { usePreferences } from "../../preferences/preferences";

export function PublicLayout() {
  const { copy, locale, setLocale, setTheme, theme } = usePreferences();
  const authLinks = [
    { to: "/login", label: copy.auth.signIn },
    { to: "/register", label: copy.auth.createAccount }
  ];

  return (
    <div className="public-shell">
      <section className="public-brand-panel">
        <Link className="brand-mark" to="/chat">
          {copy.appName}
        </Link>
        <p className="eyebrow">{copy.shell.publicBadge}</p>
        <h1 className="public-brand-panel__title">{copy.shell.publicTitle}</h1>
        <p className="lede">{copy.shell.publicDescription}</p>
        <div className="status-list public-brand-panel__features">
          <span>{copy.shell.publicFeature1}</span>
          <span>{copy.shell.publicFeature2}</span>
          <span>{copy.shell.publicFeature3}</span>
        </div>
        <div className="hero-orb hero-orb--large" aria-hidden="true" />
      </section>

      <section className="public-content-panel">
        <header className="public-topbar">
          <nav aria-label="Auth routes" className="inline-nav">
            {authLinks.map((link) => (
              <NavLink
                key={link.to}
                className={({ isActive }) =>
                  isActive ? "inline-nav__link inline-nav__link--active" : "inline-nav__link"
                }
                to={link.to}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="preference-row">
            <div className="preference-stack">
              <span>{copy.shell.localeLabel}</span>
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
            <div className="preference-stack">
              <span>{copy.shell.themeLabel}</span>
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
        </header>

        <main className="public-main">
          <Outlet />
        </main>
      </section>
    </div>
  );
}
