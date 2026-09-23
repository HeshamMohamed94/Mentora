import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./schemas";

/**
 * Bounded Vitest unit tier (H2/C3) — auth form validation logic. `LoginForm`/`RegisterForm`
 * (web/src/app/[locale]/(public)/{login,register}/*-form.tsx) wire these zod schemas straight
 * into react-hook-form via `zodResolver` with no extra logic of their own, so exercising the
 * schemas directly covers the real validation behavior end users hit, per-field, without the
 * fragile provider/router mocking a full form render would need for equivalent coverage.
 */
describe("loginSchema", () => {
  it("accepts a well-formed email and any non-empty password", () => {
    const result = loginSchema.safeParse({ email: "student@example.com", password: "x" });
    expect(result.success).toBe(true);
  });

  it("rejects a malformed email", () => {
    const result = loginSchema.safeParse({ email: "not-an-email", password: "secret123" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path[0] === "email")).toBe(true);
    }
  });

  it("rejects an empty password", () => {
    const result = loginSchema.safeParse({ email: "student@example.com", password: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path[0] === "password")).toBe(true);
    }
  });

  it("rejects an empty email", () => {
    const result = loginSchema.safeParse({ email: "", password: "secret123" });
    expect(result.success).toBe(false);
  });
});

describe("registerSchema", () => {
  it("accepts a valid name/email/8+-char password", () => {
    const result = registerSchema.safeParse({
      name: "Ada Lovelace",
      email: "ada@example.com",
      password: "longenough",
    });
    expect(result.success).toBe(true);
  });

  it("rejects an empty name", () => {
    const result = registerSchema.safeParse({ name: "", email: "ada@example.com", password: "longenough" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path[0] === "name")).toBe(true);
    }
  });

  it("rejects a password shorter than 8 characters", () => {
    const result = registerSchema.safeParse({ name: "Ada", email: "ada@example.com", password: "short1" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path[0] === "password")).toBe(true);
    }
  });

  it("rejects a malformed email", () => {
    const result = registerSchema.safeParse({ name: "Ada", email: "not-an-email", password: "longenough" });
    expect(result.success).toBe(false);
  });
});
