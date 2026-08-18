import { apiRequest } from "./http";

export interface AuthenticatedUser {
  id: string;
  username: string;
  email: string;
  organizationId: string;
  organizationRole: string;
  projects: Array<{ projectId: string; role: string }>;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  refreshExpiresAt: string;
  user: AuthenticatedUser;
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiRequest<AuthResponse>(
    "/auth/login",
    {
      method: "POST",
      body: JSON.stringify({ email, password }),
    },
    false,
  );
}

export function refreshSession(): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/auth/refresh", { method: "POST" }, false);
}

export function logout(): Promise<void> {
  return apiRequest<void>("/auth/logout", { method: "POST" });
}
