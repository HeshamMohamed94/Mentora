import { expect, test } from "@playwright/test";
import { QUIZ_COURSE_TITLE, completeAllLessons, enrollInCourse, passQuiz, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Complete a Quiz. Key assertion: correct/incorrect
 * breakdown renders with icon+text+color (never color-only, checked via DOM structure not just a
 * visual snapshot); failing routes to Retry, passing routes toward completion. */
test.describe("Complete a Quiz", () => {
  test("a passing attempt shows Passed and a correct/incorrect breakdown with real state text, then routes toward completion", async ({ page }) => {
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await completeAllLessons(page);
    await page.getByRole("link", { name: "Take Quiz" }).click();
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz$/);

    await passQuiz(page);
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz\/results$/);

    await expect(page.getByText("Passed", { exact: true })).toBeVisible();
    await expect(page.getByText("Score")).toBeVisible();
    // The breakdown must expose state as real text (icon+text+color), not color-only.
    const correctLabels = page.getByText("Correct", { exact: true });
    await expect(correctLabels.first()).toBeVisible();

    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await expect(page.getByText("Course completed!")).toBeVisible();
    await expect(page.getByRole("button", { name: "View Certificate" })).toBeVisible();
  });

  test("a failing attempt shows Failed and offers Retry Quiz, not a completion state", async ({ page }) => {
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await completeAllLessons(page);
    await page.getByRole("link", { name: "Take Quiz" }).click();
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz$/);

    // Deliberately pick the second (seed-data-incorrect) option for every question to fail.
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const options = page.locator(".mtx-answer-option");
      await options.nth(1).click();
      const submit = page.getByRole("button", { name: "Submit Quiz" });
      if (await submit.isVisible().catch(() => false)) {
        await submit.click();
        break;
      }
      await page.getByRole("button", { name: "Next", exact: true }).click();
    }
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz\/results$/);

    await expect(page.getByText("Failed", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Retry Quiz" })).toBeVisible();
    await expect(page.getByText("Course completed!")).toHaveCount(0);
  });
});
