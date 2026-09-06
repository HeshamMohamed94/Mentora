import { z } from "zod";

// Client-side validation is a UX convenience only — the backend's own validation is
// authoritative (architecture/AUTH_SECURITY.md § 8, § 1).
export const loginSchema = z.object({
  email: z.string().min(1).email(),
  password: z.string().min(1),
});
export type LoginInput = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  name: z.string().min(1).max(120),
  email: z.string().min(1).email(),
  password: z.string().min(8),
});
export type RegisterInput = z.infer<typeof registerSchema>;
