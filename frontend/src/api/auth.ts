import { authStore, type AuthUser } from "../stores/authStore";
import { apiClient } from "./client";

export interface RegisterPayload {
  email: string;
  password: string;
  displayName?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthSession {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export const authApi = {
  register(payload: RegisterPayload) {
    return apiClient.post<AuthUser>("/auth/register", payload, {
      skipAuthRefresh: true
    });
  },
  async login(payload: LoginPayload) {
    const session = await apiClient.post<AuthSession>("/auth/login", payload, {
      skipAuthRefresh: true
    });
    authStore.setAuth(session.user, session.accessToken);
    return session;
  },
  async logout() {
    try {
      await apiClient.post<void>("/auth/logout", undefined, {
        skipAuthRefresh: true
      });
    } finally {
      authStore.clearAuth();
    }
  },
  async getCurrentUser() {
    const user = await apiClient.get<AuthUser>("/auth/me");
    authStore.setUser(user);
    return user;
  }
};
