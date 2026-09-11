import { defineConfig, devices } from "@playwright/test";

/**
 * architecture/TESTING_STRATEGY.md § 5-6: Playwright against a real (test-seeded) backend +
 * MongoDB, never mocked. Per D34 there is no Docker/infra in this project — the seed mechanism is
 * the backend's own idempotent `gradlew seedDemoData` task, run against the same local dev stack
 * `start-mentora.ps1` brings up (not a separate CI-only environment), so "works in CI" and "works
 * locally" never diverge. This config does not attempt to start the backend or the Next.js dev
 * server itself (the backend is a compiled Kotlin/Ktor process outside Playwright's remit) —
 * `web/README.md` documents running `start-mentora.ps1` (or an equivalent CI step) first.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  timeout: 90_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
