import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HomePage } from "./HomePage";

describe("HomePage", () => {
  it("renders the system title", () => {
    render(<HomePage />);

    expect(
      screen.getByText("Centralized Log Monitoring System"),
    ).toBeInTheDocument();
  });
});
