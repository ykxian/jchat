import { ReactNode } from "react";

type AppShellProps = {
  children: ReactNode;
};

export function AppShell({ children }: AppShellProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">jchat</p>
          <h1>Phase 0 Scaffold</h1>
        </div>
        <nav className="nav">
          <a href="/chat">Chat</a>
          <a href="/settings">Settings</a>
        </nav>
      </aside>
      <main className="content">{children}</main>
    </div>
  );
}

