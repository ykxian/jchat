import { authApi } from "../api/auth";
import { refreshAccessToken } from "../api/client";
import { authStore } from "../stores/authStore";

let ensureSessionPromise: Promise<void> | null = null;

export async function ensureSession() {
  const currentState = authStore.getState();

  if (
    currentState.status === "authenticated" &&
    currentState.accessToken &&
    currentState.user
  ) {
    return;
  }

  if (ensureSessionPromise) {
    return ensureSessionPromise;
  }

  authStore.setLoading();

  ensureSessionPromise = (async () => {
    if (!authStore.getState().accessToken) {
      await refreshAccessToken();
    }

    await authApi.getCurrentUser();
  })()
    .catch((error) => {
      authStore.clearAuth();
      throw error;
    })
    .finally(() => {
      ensureSessionPromise = null;
    });

  return ensureSessionPromise;
}
