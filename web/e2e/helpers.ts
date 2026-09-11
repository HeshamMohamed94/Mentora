import { expect, Page } from "@playwright/test";

/** Demo seed accounts from backend/src/main/kotlin/com/mentora/backend/SeedData.kt — never mutate their
 * role-defining state (only read, or use them for flows that require a pre-existing Instructor/Admin
 * role, since public registration always creates a Student per AuthService.register). */
export const SEED_PASSWORD = "MentoraDemo1";
export const SEED_ADMIN = { email: "admin@mentora.dev", password: SEED_PASSWORD };
export const SEED_INSTRUCTOR = { email: "instructor1@mentora.dev", password: SEED_PASSWORD };

/** The one seed course with a real quiz (SeedData.kt QUIZ_COURSE) — also the one with 2 real lessons. */
export const QUIZ_COURSE_TITLE = "Building Reliable REST APIs";

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@e2e.mentora.test`;
}

/** A tiny in-memory buffer accepted by the real upload pipeline (MediaService validates
 * content-type/size, not real file contents — the same fact SeedData.kt's own placeholder
 * uploads rely on, see execution/DECISIONS_LOG.md D59). */
export function fakeFile(name: string, mimeType: string, sizeBytes = 1024) {
  return { name, mimeType, buffer: Buffer.alloc(sizeBytes, 1) };
}

/** `backend/.../plugins/RateLimiting.kt` caps the real "auth" bucket (register + login combined)
 * at 10 requests/minute — a real, intentional anti-abuse limit, not something to weaken for tests.
 * This suite registers a fresh Student in nearly every test, so a full run can transiently exceed
 * it; the frontend has no specific copy for a 429 and falls through to `auth.genericError`
 * ("Something went wrong. Please try again."). Retrying with backoff here is how a real client
 * would behave against a rate limit — not a workaround for a product defect.
 *
 * KNOWN GAP — WebKit only, investigated and disclosed rather than worked around: authenticated
 * flows (register/login and everything downstream) reliably fail to redirect under
 * `--project=webkit` against this local, plain-HTTP dev stack. `AuthRoutes.kt` sets the session
 * cookies with `secure=true` + `SameSite=Lax` (correct, unchanged production security practice) —
 * a diagnostic during this task showed the cookies genuinely get set (real JWT values, correct
 * flags requested) but WebKit's own reported cookie state showed `sameSite: "None"` rather than
 * the requested `Lax`, and the very next request (Next's middleware, checking for that cookie)
 * bounces back to `/login?redirect=...` — not a one-off timing blip: raising every timeout well
 * past what a real race would need, and re-navigating straight to the redirect target instead of
 * resubmitting the form, were both tried and neither recovers it. This reads as WebKit applying a
 * stricter (and here, non-standard-relaxed) interpretation of `Secure`/`SameSite` cookies over
 * `http://localhost` than Chromium/Firefox's well-known "localhost is trustworthy" relaxation —
 * not a defect in the app's cookie configuration, which is the security-correct choice for a real
 * HTTPS deployment. A real fix (serving local dev over HTTPS, or a project-wide cookie-policy
 * change) is out of this task's scope; see execution/DECISIONS_LOG.md for the full account. */
export async function registerNewStudent(page: Page, locale: "en" | "ar" = "en") {
  const email = uniqueEmail("student");
  const name = "E2E Student";
  for (let attempt = 1; attempt <= 4; attempt++) {
    await page.goto(`/${locale}/register`);
    await page.getByLabel("Name", { exact: true }).fill(name);
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(SEED_PASSWORD);
    await page.getByRole("button", { name: "Create Account" }).click();
    const landed = await page.waitForURL(new RegExp(`/${locale}/app`), { timeout: 15_000 }).then(() => true).catch(() => false);
    if (landed) return { email, password: SEED_PASSWORD, name };
    const alreadyExists = await page.getByText("An account with this email already exists.").isVisible().catch(() => false);
    if (alreadyExists) {
      await login(page, { email, password: SEED_PASSWORD }, locale);
      return { email, password: SEED_PASSWORD, name };
    }
    if (attempt === 4) throw new Error(`registerNewStudent: registration did not succeed after ${attempt} attempts`);
    await page.waitForTimeout(8_000 * attempt);
  }
  throw new Error("unreachable");
}

/** Shares the real "auth" rate-limit bucket with registration (see registerNewStudent) — same
 * real-client-style retry-with-backoff, not a product workaround. */
export async function login(page: Page, credentials: { email: string; password: string }, locale: "en" | "ar" = "en") {
  for (let attempt = 1; attempt <= 4; attempt++) {
    await page.goto(`/${locale}/login`);
    await page.getByLabel("Email", { exact: true }).fill(credentials.email);
    await page.getByLabel("Password", { exact: true }).fill(credentials.password);
    await page.getByRole("button", { name: "Login" }).click();
    const landed = await page.waitForURL(new RegExp(`/${locale}/(app|instructor|admin)`), { timeout: 15_000 }).then(() => true).catch(() => false);
    if (landed) return;
    if (attempt === 4) throw new Error(`login: did not succeed after ${attempt} attempts`);
    await page.waitForTimeout(8_000 * attempt);
  }
}

/** From the authenticated app shell: Explore -> search -> open course -> Enroll -> Demo Checkout ->
 * Complete Demo Purchase -> Start Learning. Lands on the Course Player. */
export async function enrollInCourse(page: Page, courseTitle: string, locale: "en" | "ar" = "en") {
  await page.goto(`/${locale}/app/explore`);
  await page.getByLabel("Search courses", { exact: true }).fill(courseTitle);
  await page.getByRole("link", { name: courseTitle, exact: true }).click();
  await page.waitForURL(new RegExp(`/${locale}/app/courses/`));
  const enrollButton = page.getByRole("button", { name: "Enroll", exact: true });
  const continueButton = page.getByRole("button", { name: "Continue Learning", exact: true });
  if (await continueButton.isVisible().catch(() => false)) {
    await continueButton.click();
    await page.waitForURL(new RegExp(`/${locale}/app/learn/`));
    return;
  }
  await enrollButton.click();
  await page.waitForURL(new RegExp(`/${locale}/app/checkout/`));
  await page.getByRole("button", { name: "Complete Demo Purchase" }).click();
  await expect(page.getByText("Payment Successful")).toBeVisible();
  await page.getByRole("button", { name: "Start Learning" }).click();
  await page.waitForURL(new RegExp(`/${locale}/app/learn/`));
}

/** Clicks "Mark Complete" for every lesson in curriculum order, waiting for each to register as
 * Completed before moving to the next — mirrors a real learner working through the course, not a
 * direct API shortcut, per the testing strategy's "against a real backend... via the UI" intent. */
export async function completeAllLessons(page: Page) {
  await expect(page.getByRole("heading", { level: 2 }).first()).toBeVisible();
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const markCompleteButton = page.getByRole("button", { name: "Mark Complete" });
    const alreadyCompleted = page.getByRole("button", { name: "Completed", exact: true });
    if (await alreadyCompleted.isVisible().catch(() => false)) {
      // already completed — fall through to Next below
    } else {
      await markCompleteButton.click();
      await expect(alreadyCompleted).toBeVisible();
    }
    const nextButton = page.getByRole("button", { name: "Next", exact: true });
    if (await nextButton.isDisabled()) break;
    await nextButton.click();
  }
}

/** Creates and publishes a real, backend-valid course as the seeded Instructor: title/description/
 * category/price, a thumbnail, one section, and one lesson with a video — CourseService.publish's
 * actual full requirement set (categoryId/priceDisplay/thumbnail/>=1 section AND every section has
 * >=1 lesson AND every lesson has a video — all four are real backend checks, not merely a
 * frontend-only readiness signal). Used to set up the Admin Course Management flow. Must be called
 * from a page already authenticated as SEED_INSTRUCTOR. Returns the created course's title.
 */
export async function createAndPublishMinimalCourse(page: Page, titlePrefix: string): Promise<string> {
  const title = `${titlePrefix} ${Date.now()}`;
  await page.goto("/en/instructor/courses/new");
  await page.getByLabel("Title", { exact: true }).fill(title);
  await page.getByLabel("Description", { exact: true }).fill("A minimal E2E course for the Admin flow.");
  await page.getByRole("combobox", { name: "Category" }).click();
  await page.getByRole("option").first().click();
  await page.getByLabel("Price amount", { exact: true }).fill("19");
  await page.getByLabel("Currency", { exact: true }).fill("USD");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await page.waitForURL(/\/en\/instructor\/courses\/[a-f0-9]+$/);

  await page.locator('input[type="file"][aria-label="Course thumbnail"]').setInputFiles(fakeFile("thumbnail.jpg", "image/jpeg", 2048));
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByText("Current thumbnail")).toBeVisible();

  await page.getByRole("tab", { name: "Curriculum" }).click();
  await page.getByLabel("New section title", { exact: true }).fill("Section 1");
  await page.getByRole("button", { name: "Add Section" }).click();
  await expect(page.getByText("Section 1")).toBeVisible();

  await page.getByRole("link", { name: "Add Lesson" }).click();
  await page.waitForURL(/\/lessons\/new$/);
  await page.getByLabel("Title", { exact: true }).fill("Lesson 1");
  await page.getByLabel("Description", { exact: true }).fill("A real lesson added by the E2E suite.");
  await page.locator('input[type="file"][aria-label="Lesson video"]').setInputFiles(fakeFile("lesson.mp4", "video/mp4", 4096));
  await page.getByRole("button", { name: "Save Lesson" }).click();
  await page.waitForURL(/\?tab=curriculum$/);

  await page.getByRole("tab", { name: "Overview" }).click();
  await page.getByRole("switch").click();
  await expect(page.getByRole("switch")).toBeChecked();
  return title;
}

/** Answers every question with its first (seed-data-correct, see SeedData.kt's `question()` helper —
 * options are always [correct, incorrect] in stored order, never shuffled server-side) option, then
 * submits — a deterministic guaranteed pass for QUIZ_COURSE_TITLE's quiz. */
export async function passQuiz(page: Page) {
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const options = page.locator(".mtx-answer-option");
    await expect(options.first()).toBeVisible();
    await options.first().click();
    const submit = page.getByRole("button", { name: "Submit Quiz" });
    const next = page.getByRole("button", { name: "Next", exact: true });
    if (await submit.isVisible().catch(() => false)) {
      await submit.click();
      break;
    }
    await next.click();
  }
}
