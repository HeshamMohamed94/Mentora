import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { ApiError } from "./client";
import { apiFetch } from "./client";
import { useLatestAttempt, useQuiz } from "./quiz";

vi.mock("./client", async () => {
  const actual = await vi.importActual<typeof import("./client")>("./client");
  return { ...actual, apiFetch: vi.fn() };
});

/**
 * Bounded Vitest unit tier (H2/C3) — quiz results/scoring *data* logic. Quiz scoring itself
 * (isCorrect, score, passed) is computed server-side (architecture/TESTING_STRATEGY.md § 1);
 * the Quiz Results screen (owned by another in-flight Phase 8 task, not touched here) only
 * displays what these hooks fetch. What IS real frontend logic worth covering: the
 * not-found-vs-error mapping `getQuiz`/`getLatestAttempt` perform before a component ever sees
 * the data (web/src/lib/api/quiz.ts) — a wrong mapping here would either crash the results
 * screen on a legitimate "no attempt yet" state or silently swallow a real backend error.
 */
function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useQuiz", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("resolves to null (not an error) when the backend reports QUIZ_NOT_FOUND", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(404, "QUIZ_NOT_FOUND", "not found"));
    const { result } = renderHook(() => useQuiz("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });

  it("surfaces any other backend error instead of swallowing it as not-found", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(500, "INTERNAL_ERROR", "boom"));
    const { result } = renderHook(() => useQuiz("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
  });
});

describe("useLatestAttempt", () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset();
  });

  it("resolves to null (not an error) when the backend reports ATTEMPT_NOT_FOUND", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(404, "ATTEMPT_NOT_FOUND", "not found"));
    const { result } = renderHook(() => useLatestAttempt("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });

  it("returns the real attempt payload (score/passed/breakdown) untouched on success", async () => {
    const attempt = {
      score: 80,
      passed: true,
      breakdown: [{ questionId: "q1", correctOptionId: "o1", isCorrect: true, selectedOptionId: "o1" }],
    };
    vi.mocked(apiFetch).mockResolvedValueOnce(attempt);
    const { result } = renderHook(() => useLatestAttempt("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(attempt);
  });

  it("surfaces any other backend error instead of swallowing it as not-found", async () => {
    vi.mocked(apiFetch).mockRejectedValueOnce(new ApiError(500, "INTERNAL_ERROR", "boom"));
    const { result } = renderHook(() => useLatestAttempt("course-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
  });
});
