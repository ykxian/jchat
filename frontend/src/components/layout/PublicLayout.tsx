import { Link, NavLink, Outlet } from "react-router-dom";

const authLinks = [
  { to: "/login", label: "Login" },
  { to: "/register", label: "Register" }
];

export function PublicLayout() {
  return (
    <div className="public-shell">
      <section className="public-brand-panel">
        <Link className="brand-mark" to="/chat">
          jchat
        </Link>
        <p className="eyebrow">Phase 2</p>
        <h1>Frontend skeleton for auth and chat.</h1>
        <p className="lede">
          This stage keeps the app intentionally static while fixing the route
          structure, layout contracts, and API boundary that Phase 3 will build
          on.
        </p>
        <div className="status-list">
          <span>React Router shell</span>
          <span>Placeholder auth flows</span>
          <span>Typed API client</span>
        </div>
      </section>

      <section className="public-content-panel">
        <header className="public-topbar">
          <nav className="inline-nav" aria-label="Auth routes">
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
        </header>

        <main className="public-main">
          <Outlet />
        </main>
      </section>
    </div>
  );
}
