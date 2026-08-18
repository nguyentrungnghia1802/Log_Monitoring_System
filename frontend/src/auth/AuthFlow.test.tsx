import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRouter } from "../app/AppRouter";
import { apiRequest } from "../services/http";
import { AuthProvider } from "./AuthContext";

const user = {
  id: "user-1",
  username: "admin",
  email: "admin@example.test",
  organizationId: "org-1",
  organizationRole: "ORGANIZATION_ADMIN",
  projects: [],
};

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

function renderApp(initialPath = "/") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>
          <AppRouter />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("management authentication flow", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("redirects to login, keeps the access token in memory, and logs out", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockImplementationOnce(() =>
        jsonResponse(
          { error: { code: "INVALID_REFRESH_TOKEN", message: "Expired" } },
          401,
        ),
      )
      .mockImplementationOnce(() =>
        jsonResponse({
          accessToken: "short-access-token",
          expiresInSeconds: 900,
          refreshExpiresAt: "2026-08-25T00:00:00Z",
          user,
        }),
      )
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse({ ok: true }))
      .mockImplementationOnce(() =>
        Promise.resolve(new Response(null, { status: 204 })),
      );

    renderApp("/projects");
    expect(
      await screen.findByRole("heading", { name: "LogMonitor" }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "admin@example.test" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "correct-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(
      await screen.findByRole("heading", { name: "Projects" }),
    ).toBeInTheDocument();
    expect(localStorage.getItem("accessToken")).toBeNull();
    await apiRequest("/probe");
    const probeRequest = fetchMock.mock.calls.at(-1);
    expect(
      (probeRequest?.[1]?.headers as Record<string, string> | undefined)
        ?.Authorization,
    ).toBe("Bearer short-access-token");

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "LogMonitor" }),
      ).toBeInTheDocument(),
    );
  });

  it("restores a refresh-cookie session without rendering login", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementationOnce(() =>
      jsonResponse({
        accessToken: "restored-token",
        expiresInSeconds: 900,
        refreshExpiresAt: "2026-08-25T00:00:00Z",
        user,
      }),
    );
    renderApp("/");
    expect(
      await screen.findByText("Centralized Log Monitoring System"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Sign in" }),
    ).not.toBeInTheDocument();
  });
});
