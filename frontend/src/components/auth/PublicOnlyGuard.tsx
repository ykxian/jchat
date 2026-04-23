import { ReactNode, useEffect } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { ensureSession } from "../../auth/session";
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

  useEffect(() => {
    if (status === "unknown") {
      void ensureSession().catch(() => undefined);
    }
  }, [status]);

  if (status === "unknown" || status === "loading") {
    return (
      <SessionGateFallback
        title="Checking your session"
        description="If you still have a valid refresh cookie, the app will take you back in."
      />
    );
  }

  if (status === "authenticated") {
    return <Navigate replace to={getSafeNext(location.search)} />;
  }

  return <>{children}</>;
}
