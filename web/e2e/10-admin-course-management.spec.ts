import { expect, test } from "@playwright/test";
import { SEED_ADMIN, SEED_INSTRUCTOR, createAndPublishMinimalCourse, enrollInCourse, login, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Admin Course Management. Key assertion: Unpublish
 * removes a course from Explore while leaving existing enrollments intact (verified by asserting
 * a previously-enrolled test student still has player access after). */
test.describe("Admin Course Management", () => {
  test("unpublishing a course hides it from Explore but a previously-enrolled student keeps player access", async ({ page, browser }) => {
    // Force the desktop <table> layout (>=1024px, design-system/COMPONENTS.md DataTable
    // breakpoint) so the row/action-menu locators below are unambiguous regardless of project.
    await page.setViewportSize({ width: 1280, height: 800 });
    await login(page, SEED_INSTRUCTOR);
    const courseTitle = await createAndPublishMinimalCourse(page, "E2E Admin Course");

    // A separate student browser context enrolls before the unpublish.
    const studentContext = await browser.newContext();
    const studentPage = await studentContext.newPage();
    await registerNewStudent(studentPage);
    await enrollInCourse(studentPage, courseTitle);
    await expect(studentPage).toHaveURL(/\/en\/app\/learn\//);
    const playerUrl = studentPage.url();

    // Confirm it's genuinely live in Explore before touching it.
    await page.goto("/en/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(courseTitle);
    await expect(page.getByRole("link", { name: courseTitle, exact: true })).toBeVisible();

    // Admin unpublishes it.
    await login(page, SEED_ADMIN);
    await page.goto("/en/admin/courses");
    await page.getByLabel("Search courses", { exact: true }).fill(courseTitle);
    const row = page.locator(".mtx-data-table tbody tr", { hasText: courseTitle });
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: "Course actions", exact: true }).click();
    await page.getByRole("menuitem", { name: "Unpublish" }).click();
    await page.getByRole("button", { name: "Unpublish", exact: true }).click();
    await expect(page.getByText("Draft", { exact: true }).first()).toBeVisible();

    // Explore no longer lists it for a fresh visit.
    await page.goto("/en/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(courseTitle);
    await expect(page.getByRole("link", { name: courseTitle, exact: true })).toHaveCount(0);

    // The already-enrolled student still has player access — reload the exact player URL from
    // before the unpublish, not just My Learning's listing.
    await studentPage.goto(playerUrl);
    await expect(studentPage.getByText(/Lesson \d+ of \d+|No lessons yet/)).toBeVisible();
    await studentContext.close();
  });
});
