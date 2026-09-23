import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

/**
 * H2/C3 (execution/PHASE_8_ACCEPTANCE_CRITERIA.md) — bounded Vitest + React Testing Library
 * unit/component tier for the highest-value/highest-risk logic only (quiz results data
 * mapping, demo-checkout enrollment gating, auth form validation) — NOT exhaustive coverage.
 * Playwright (`npm run test:e2e`) remains the real end-to-end suite; this tier never
 * duplicates it, it only covers pure/hook-level logic that E2E exercises indirectly.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest-setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
  },
});
