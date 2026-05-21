import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import Page from "./page";

describe("Page", () => {
  it("renders TubeNote text", () => {
    render(<Page />);
    expect(screen.getByText("TubeNote")).toBeInTheDocument();
  });
});
