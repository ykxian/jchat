import { useSyncExternalStore } from "react";
import { conversationStore } from "./conversationStore";
import { streamStore } from "./streamStore";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  emailVerified: boolean;
  createdAt: string | null;
}

export type AuthStatus = "unknown" | "loading" | "authenticated" | "anonymous";

export interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
  accessToken: string | null;
}

type Listener = () => void;
type Selector<T> = (state: AuthState) => T;

const listeners = new Set<Listener>();

let state: AuthState = {
  status: "unknown",
  user: null,
  accessToken: null
};

function notify() {
  for (const listener of listeners) {
    listener();
  }
}

function setState(nextState: AuthState) {
  state = nextState;
  notify();
}

function subscribe(listener: Listener) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

function getState() {
  return state;
}

export const authStore = {
  subscribe,
  getState,
  setAuth(user: AuthUser, accessToken: string) {
    setState({
      status: "authenticated",
      user,
      accessToken
    });
  },
  setUser(user: AuthUser) {
    setState({
      status: state.accessToken ? "authenticated" : "anonymous",
      user,
      accessToken: state.accessToken
    });
  },
  setLoading() {
    setState({
      status: "loading",
      user: state.user,
      accessToken: state.accessToken
    });
  },
  clearAuth() {
    conversationStore.reset();
    streamStore.clear();
    setState({
      status: "anonymous",
      user: null,
      accessToken: null
    });
  }
};

export function useAuthStore<T>(selector: Selector<T>) {
  return useSyncExternalStore(authStore.subscribe, () => selector(state), () => selector(state));
}
