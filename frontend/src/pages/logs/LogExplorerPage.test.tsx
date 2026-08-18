import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { fetchLogById, fetchLogs } from "../../services/api";
import { LogExplorerPage } from "./LogExplorerPage";

vi.mock("../../services/api", () => ({
  fetchLogById: vi.fn(),
  fetchLogs: vi.fn(),
}));

describe("LogExplorerPage", () => {
  it("searches scoped logs and opens event details", async () => {
    vi.mocked(fetchLogs).mockResolvedValue({
      events: [
        {
          id: "event-1",
          timestamp: "2026-08-18T00:00:00Z",
          level: "ERROR",
          service: "checkout",
          environment: "production",
          eventType: "FAILURE",
          message: "Payment failed",
          projectId: "demo-project",
        },
      ],
      hasMore: false,
    });
    vi.mocked(fetchLogById).mockResolvedValue({
      id: "event-1",
      timestamp: "2026-08-18T00:00:00Z",
      level: "ERROR",
      service: "checkout",
      environment: "production",
      eventType: "FAILURE",
      message: "Payment failed",
      receivedAt: "2026-08-18T00:00:01Z",
      expireAt: "2026-08-25T00:00:01Z",
      organizationId: "org-1",
      projectId: "demo-project",
      apiKeyId: "key-1",
      exception: { type: "PaymentException" },
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <LogExplorerPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Payment failed")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Level"), {
      target: { value: "ERROR" },
    });
    fireEvent.change(screen.getByLabelText("Service"), {
      target: { value: "checkout" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("Payment failed")).toBeInTheDocument();
    expect(fetchLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        projectId: "demo-project",
        level: "ERROR",
        service: "checkout",
        limit: 100,
      }),
    );

    fireEvent.click(screen.getByText("Payment failed"));
    await waitFor(() =>
      expect(screen.getByLabelText("Event details")).toHaveTextContent(
        "PaymentException",
      ),
    );
    expect(screen.getByLabelText("Event details")).toHaveTextContent("event-1");
    expect(fetchLogById).toHaveBeenCalledWith("demo-project", "event-1");
  });

  it("loads the next cursor page without discarding the first page", async () => {
    vi.mocked(fetchLogs)
      .mockResolvedValueOnce({
        events: [
          {
            id: "event-1",
            timestamp: "2026-08-18T00:00:00Z",
            level: "ERROR",
            service: "checkout",
            environment: "production",
            eventType: "FAILURE",
            message: "First failure",
            projectId: "demo-project",
          },
        ],
        hasMore: true,
        nextCursor: "cursor-1",
      })
      .mockResolvedValueOnce({
        events: [
          {
            id: "event-2",
            timestamp: "2026-08-18T00:01:00Z",
            level: "WARN",
            service: "checkout",
            environment: "production",
            eventType: "RETRY",
            message: "Second event",
            projectId: "demo-project",
          },
        ],
        hasMore: false,
      });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <LogExplorerPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("First failure")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("Second event")).toBeInTheDocument();
    expect(screen.getByText("First failure")).toBeInTheDocument();
    expect(fetchLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        projectId: "demo-project",
        cursor: "cursor-1",
        limit: 100,
      }),
    );
  });

  it("renders an empty result state and a query error state", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.mocked(fetchLogs).mockResolvedValueOnce({ events: [], hasMore: false });
    const { unmount } = render(
      <QueryClientProvider client={queryClient}>
        <LogExplorerPage />
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("No events match these filters."),
    ).toBeInTheDocument();
    unmount();

    vi.mocked(fetchLogs).mockRejectedValueOnce(new Error("Search unavailable"));
    render(
      <QueryClientProvider client={queryClient}>
        <LogExplorerPage />
      </QueryClientProvider>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Search unavailable",
    );
  });
});
