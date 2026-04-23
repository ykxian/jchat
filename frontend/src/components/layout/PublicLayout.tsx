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
        <p className="eyebrow">Phase 4</p>
        <h1>Auth routes are now connected to the backend.</h1>
        <p className="lede">
          Access tokens stay in memory, refresh happens through HttpOnly
          cookies, and route guards restore the session before rendering the
          protected workspace.
        </p>
        <div className="status-list">
          <span>Register and login</span>
          <span>401 automatic refresh</span>
          <span>Protected route restore</span>
        </div>
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
        </header>

        <main className="public-main">
          <Outlet />
        </main>
      </section>
    </div>
  );
}
