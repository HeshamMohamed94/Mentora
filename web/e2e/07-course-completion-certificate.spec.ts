import { expect, test } from "@playwright/test";
import { QUIZ_COURSE_TITLE, completeAllLessons, enrollInCourse, passQuiz, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Course Completion -> Certificate. Key assertion: the
 * certificate appears immediately after the completing action, with correct denormalized
 * snapshot data. */
test.describe("Course Completion -> Certificate", () => {
  test("completing the course's quiz immediately issues a certificate visible in Certificates List and Detail", async ({ page }) => {
    const student = await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await completeAllLessons(page);
    await page.getByRole("link", { name: "Take Quiz" }).click();
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz$/);
    await passQuiz(page);
    await page.waitForURL(/\/en\/app\/learn\/.*\/quiz\/results$/);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.getByRole("button", { name: "View Certificate" }).click();
    await page.waitForURL(/\/en\/app\/certificates$/);

    const certificateCard = page.locator("article", { hasText: QUIZ_COURSE_TITLE });
    await expect(certificateCard).toBeVisible();
    await certificateCard.getByRole("button", { name: "View", exact: true }).click();
    await page.waitForURL(/\/en\/app\/certificates\/MTR-/);

    const document = page.locator("article.mtx-certificate-document");
    await expect(document).toBeVisible();
    await expect(document.getByText("Certificate of Completion")).toBeVisible();
    await expect(document.getByRole("heading", { name: student.name, exact: true })).toBeVisible();
    await expect(document.getByText(QUIZ_COURSE_TITLE)).toBeVisible();
    await expect(document.getByText(/Certificate ID: MTR-/)).toBeVisible();
  });
});
