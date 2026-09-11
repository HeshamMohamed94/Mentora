import { expect, test } from "@playwright/test";
import { QUIZ_COURSE_TITLE, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Explore -> Course Details. Key assertion: search/filter
 * narrows results; tapping a card navigates with correct data. */
test.describe("Explore -> Course Details", () => {
  test("search narrows the course list to a matching title", async ({ page }) => {
    await registerNewStudent(page);
    await page.goto("/en/app/explore");
    await expect(page.getByRole("link", { name: "Building Reliable REST APIs", exact: true })).toBeVisible();
    await page.getByLabel("Search courses", { exact: true }).fill("Kotlin");
    await expect(page.getByRole("link", { name: "Kotlin Coroutines in Practice", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Building Reliable REST APIs", exact: true })).toHaveCount(0);
  });

  test("a search with no matches shows the empty state", async ({ page }) => {
    await registerNewStudent(page);
    await page.goto("/en/app/explore");
    // A single nonsense token with no real-word overlap — MongoDB's $text search is OR-of-terms,
    // so a multi-word query sharing a common word (e.g. "course") with real seed data would
    // legitimately still match other documents; that's real search behavior, not a bug to work
    // around with a more elaborate query.
    await page.getByLabel("Search courses", { exact: true }).fill("qwzxjklvbnasdfnomatch");
    await expect(page.getByText("No courses match your search")).toBeVisible();
  });

  test("opening a course card navigates to the correct Course Details page", async ({ page }) => {
    await registerNewStudent(page);
    await page.goto("/en/app/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(QUIZ_COURSE_TITLE);
    await page.getByRole("link", { name: QUIZ_COURSE_TITLE, exact: true }).click();
    await page.waitForURL(/\/en\/app\/courses\//);
    await expect(page.getByRole("heading", { name: QUIZ_COURSE_TITLE, exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Curriculum" })).toBeVisible();
  });
});
