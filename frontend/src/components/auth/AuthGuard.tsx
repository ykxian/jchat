import { ReactNode, useEffect } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { ensureSession } from "../../auth/session";
import { useAuthStore } from "../../stores/authStore";
import { SessionGateFallback } from "./SessionGateFallback";

interface AuthGuardProps {
  children: ReactNode;
}

function buildNextUrl(pathname: string, search: string, hash: string) {
  const next = `${pathname}${search}${hash}`;
  return `/login?next=${encodeURIComponent(next)}`;
}

export function AuthGuard({ children }: AuthGuardProps) {
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
        title="Restoring your workspace"
        description="Checking the refresh cookie and loading your account session."
      />
    );
  }

  if (status !== "authenticated") {
    return (
      <Navigate
        replace
        to={buildNextUrl(location.pathname, location.search, location.hash)}
      />
    );
  }

  return <>{children}</>;
}
