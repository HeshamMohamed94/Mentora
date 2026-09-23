import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import type { AuthUser } from "../auth/types";
import { apiRequest } from "./client";
import { useEnrollments, useIsEnrolled } from "./enrollment";

vi.mock("./client", async () => {
  const actual = await vi.importActual<typeof import("./client")>("./client");
  return { ...actual, apiRequest: vi.fn() };
});

const mockUseCurrentUser = vi.fn();
vi.mock("../auth/use-current-user", () => ({
  useCurrentUser: () => mockUseCurrentUser(),
}));

/**
 * Bounded Vitest unit tier (H2/C3) — demo-checkout/enrollment flow logic. `CheckoutScreen`
 * itself is a thin render of `useCheckoutPreview`/`useCompleteCheckout` (no branching logic of
 * its own beyond loading/error/success states already covered by `web/e2e/03-demo-checkout*`);
 * the real client-side logic worth a fast unit test is `useEnrollments`'s role-gating (a Guest
 * or non-student session must never fire an authenticated `/enrollments` call) and
 * `useIsEnrolled`'s derivation, which the Course Details/Checkout entry point relies on to
 * decide "Enroll" vs. "Continue Learning".
 */
function studentUser(): AuthUser {
  return { id: "u1", email: "s@example.com", name: "Student", role: "student", createdAt: "2026-01-01T00:00:00Z" };
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useEnrollments", () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset();
    mockUseCurrentUser.mockReset();
  });

  it("never calls the enrollments endpoint for a signed-out (Guest) session", async () => {
    mockUseCurrentUser.mockReturnValue({ data: undefined });
    renderHook(() => useEnrollments(), { wrapper: createWrapper() });
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("fetches the caller's own enrollments for a student session", async () => {
    mockUseCurrentUser.mockReturnValue({ data: studentUser() });
    vi.mocked(apiRequest).mockResolvedValueOnce({ data: [], meta: {} });
    const { result } = renderHook(() => useEnrollments(), { wrapper: createWrapper() });
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith("/enrollments?limit=100"));
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useIsEnrolled", () => {
  beforeEach(() => {
    vi.mocked(apiRequest).mockReset();
    mockUseCurrentUser.mockReset();
  });

  it("returns true when the course id is present among the student's enrollments", async () => {
    mockUseCurrentUser.mockReturnValue({ data: studentUser() });
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: [{ id: "e1", courseId: "course-1", source: "demo", enrolledAt: "2026-01-01T00:00:00Z", status: "active" }],
      meta: {},
    });
    const { result } = renderHook(() => useIsEnrolled("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current).toBe(true));
  });

  it("returns false when the course id is absent from the student's enrollments", async () => {
    mockUseCurrentUser.mockReturnValue({ data: studentUser() });
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: [{ id: "e1", courseId: "course-other", source: "demo", enrolledAt: "2026-01-01T00:00:00Z", status: "active" }],
      meta: {},
    });
    const { result } = renderHook(() => useIsEnrolled("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(apiRequest).toHaveBeenCalled());
    expect(result.current).toBe(false);
  });

  it("returns false (never throws) for a Guest session with no enrollments query in flight", () => {
    mockUseCurrentUser.mockReturnValue({ data: undefined });
    const { result } = renderHook(() => useIsEnrolled("course-1"), { wrapper: createWrapper() });
    expect(result.current).toBe(false);
  });
});
