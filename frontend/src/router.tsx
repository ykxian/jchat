import { createBrowserRouter, Navigate } from "react-router-dom";
import { AuthGuard } from "./components/auth/AuthGuard";
import { PublicOnlyGuard } from "./components/auth/PublicOnlyGuard";
import { AppShell } from "./components/layout/AppShell";
import { PublicLayout } from "./components/layout/PublicLayout";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";
import { MasksPage } from "./pages/MasksPage";
import { RegisterPage } from "./pages/RegisterPage";
import { SettingsPage } from "./pages/SettingsPage";

export const router = createBrowserRouter([
  {
    path: "/",
    children: [
      {
        element: (
          <PublicOnlyGuard>
            <PublicLayout />
          </PublicOnlyGuard>
        ),
        children: [
          { path: "login", element: <LoginPage /> },
          { path: "register", element: <RegisterPage /> }
        ]
      },
      {
        element: (
          <AuthGuard>
            <AppShell />
          </AuthGuard>
        ),
        children: [
          { index: true, element: <Navigate to="/chat" replace /> },
          { path: "chat", element: <ChatPage /> },
          { path: "chat/:conversationId", element: <ChatPage /> },
          { path: "masks", element: <MasksPage /> },
          { path: "settings", element: <SettingsPage /> }
        ]
      },
      { path: "*", element: <Navigate to="/chat" replace /> }
    ]
  }
]);
