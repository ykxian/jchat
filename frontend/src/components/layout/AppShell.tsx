import { NavLink, Outlet } from "react-router-dom";

const navigation = [
  { to: "/chat", label: "Chat" },
  { to: "/settings", label: "Settings" }
];

export function AppShell() {
  return (
    <div className="shell">
      <aside className="shell-sidebar">
        <div className="shell-sidebar__header">
          <p className="eyebrow">jchat</p>
          <h1>Frontend Foundation</h1>
          <p className="muted">
            Phase 2 delivers the route frame and API boundary. Auth and live
            chat logic land next.
          </p>
        </div>

        <nav className="shell-nav" aria-label="Primary">
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
          <p className="eyebrow">Ready For</p>
          <ul className="feature-list">
            <li>JWT-based auth screens</li>
            <li>Conversation list and message pane</li>
            <li>Settings forms and provider management</li>
          </ul>
        </section>
      </aside>

      <div className="shell-main">
        <header className="shell-header">
          <div>
            <p className="eyebrow">Workspace</p>
            <h2>React SPA shell</h2>
          </div>
          <span className="pill">Routes + layout + API client</span>
        </header>

        <main className="shell-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
