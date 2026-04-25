import { ReactNode, useEffect } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { ensureSession } from "../../auth/session";
import { usePreferences } from "../../preferences/preferences";
import { useAuthStore } from "../../stores/authStore";
import { SessionGateFallback } from "./SessionGateFallback";

interface PublicOnlyGuardProps {
  children: ReactNode;
}

function getSafeNext(search: string) {
  const params = new URLSearchParams(search);
  const next = params.get("next");

  if (!next || !next.startsWith("/")) {
    return "/chat";
  }

  return next;
}

export function PublicOnlyGuard({ children }: PublicOnlyGuardProps) {
  const location = useLocation();
  const status = useAuthStore((state) => state.status);
  const { copy } = usePreferences();

  useEffect(() => {
    if (status === "unknown") {
      void ensureSession().catch(() => undefined);
    }
  }, [status]);

  if (status === "unknown" || status === "loading") {
    return (
      <SessionGateFallback
        title={copy.session.checkingTitle}
        description={copy.session.checkingDescription}
      />
    );
  }

  if (status === "authenticated") {
    return <Navigate replace to={getSafeNext(location.search)} />;
  }

  return <>{children}</>;
}
