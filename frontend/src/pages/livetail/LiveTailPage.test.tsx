import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LiveTailPage } from "./LiveTailPage";

type MockStompOptions = {
  onConnect?: () => void;
};

const stompState = vi.hoisted(() => ({
  options: undefined as MockStompOptions | undefined,
  handler: undefined as ((message: { body: string }) => void) | undefined,
  unsubscribe: vi.fn(),
  deactivate: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../../auth/authToken", () => ({
  getAccessToken: vi.fn(() => "access-token"),
}));

vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn().mockImplementation(function (options: MockStompOptions) {
    stompState.options = options;
    return {
      activate: vi.fn(),
      deactivate: stompState.deactivate,
      subscribe: vi.fn(
        (
          _destination: string,
          handler: (message: { body: string }) => void,
        ) => {
          stompState.handler = handler;
          return { unsubscribe: stompState.unsubscribe };
        },
      ),
    };
  }),
}));

afterEach(() => {
  stompState.options = undefined;
  stompState.handler = undefined;
  vi.clearAllMocks();
});

beforeEach(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
    configurable: true,
    value: vi.fn(),
  });
});

describe("LiveTailPage", () => {
  it("connects, buffers while paused, resumes, filters, and clears events", async () => {
    const { unmount } = render(<LiveTailPage />);

    expect(screen.getByText("CONNECTING...")).toBeInTheDocument();
    await act(async () => {
      stompState.options?.onConnect?.();
    });
    expect(await screen.findByText("LIVE STREAM")).toBeInTheDocument();

    act(() => {
      stompState.handler?.({
        body: JSON.stringify({
          id: "event-1",
          timestamp: "2026-08-18T00:00:00Z",
          level: "ERROR",
          service: "checkout",
          environment: "production",
          eventType: "FAILURE",
          message: "First failure",
          projectId: "demo-project",
          traceId: "trace-1",
        }),
      });
    });
    expect(await screen.findByText("First failure")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Pause Stream/ }));
    act(() => {
      stompState.handler?.({
        body: JSON.stringify({
          id: "event-2",
          timestamp: "2026-08-18T00:01:00Z",
          level: "WARN",
          service: "billing",
          environment: "production",
          eventType: "RETRY",
          message: "Buffered retry",
          projectId: "demo-project",
        }),
      });
    });
    expect(screen.queryByText("Buffered retry")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Resume Stream/ }));
    expect(await screen.findByText("Buffered retry")).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText("Filter service..."), {
      target: { value: "billing" },
    });
    expect(screen.getByText("EVENTS: 1")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Clear Console" }));
    expect(screen.queryByText("Buffered retry")).not.toBeInTheDocument();
    expect(screen.getByText("EVENTS: 0")).toBeInTheDocument();

    unmount();
    await waitFor(() => expect(stompState.deactivate).toHaveBeenCalled());
    expect(stompState.unsubscribe).toHaveBeenCalled();
  });
});
