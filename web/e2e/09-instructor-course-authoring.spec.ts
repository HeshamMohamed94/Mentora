import { expect, test } from "@playwright/test";
import { SEED_INSTRUCTOR, fakeFile, login } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Instructor Course Authoring. Key assertion: full
 * create -> structure -> publish path; Publish is blocked with a visible reason until
 * requirements are met (ux/INSTRUCTOR_ADMIN_UX.md). Uses the real seeded Instructor account
 * (public registration always creates a Student, per AuthService.register). */
test.describe("Instructor Course Authoring", () => {
  test("create -> structure -> publish, blocked with a visible reason until requirements are met", async ({ page }) => {
    const courseTitle = `E2E Authoring Course ${Date.now()}`;
    await login(page, SEED_INSTRUCTOR);
    await page.goto("/en/instructor/courses/new");

    await page.getByLabel("Title", { exact: true }).fill(courseTitle);
    await page.getByLabel("Description", { exact: true }).fill("An end-to-end authored test course.");
    await page.getByRole("combobox", { name: "Category" }).click();
    await page.getByRole("option").first().click();
    await page.getByLabel("Price amount", { exact: true }).fill("49");
    await page.getByLabel("Currency", { exact: true }).fill("USD");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await page.waitForURL(/\/en\/instructor\/courses\/[a-f0-9]+$/);
    await expect(page.getByRole("heading", { name: courseTitle, exact: true })).toBeVisible();

    // Publish is blocked: thumbnail and curriculum are still missing.
    const publishSwitch = page.getByRole("switch");
    await expect(publishSwitch).not.toBeChecked();
    await publishSwitch.click();
    await expect(page.getByText(/needs attention/i).first()).toBeVisible();
    await expect(publishSwitch).not.toBeChecked();

    // Satisfy the thumbnail requirement.
    const thumbnail = fakeFile("thumbnail.jpg", "image/jpeg", 2048);
    await page.locator('input[type="file"][aria-label="Course thumbnail"]').setInputFiles(thumbnail);
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByText("Current thumbnail")).toBeVisible();

    // Add curriculum: one section, one lesson with a video.
    await page.getByRole("tab", { name: "Curriculum" }).click();
    await page.getByLabel("New section title", { exact: true }).fill("Section 1");
    await page.getByRole("button", { name: "Add Section" }).click();
    await expect(page.getByText("Section 1")).toBeVisible();

    await page.getByRole("link", { name: "Add Lesson" }).click();
    await page.waitForURL(/\/lessons\/new$/);
    await page.getByLabel("Title", { exact: true }).fill("E2E Lesson 1");
    await page.getByLabel("Description", { exact: true }).fill("A real lesson added by the E2E suite.");
    const video = fakeFile("lesson.mp4", "video/mp4", 4096);
    await page.locator('input[type="file"][aria-label="Lesson video"]').setInputFiles(video);
    await page.getByRole("button", { name: "Save Lesson" }).click();
    await page.waitForURL(/\?tab=curriculum$/);
    await expect(page.getByRole("link", { name: /E2E Lesson 1/ })).toBeVisible();

    // Now publish should succeed.
    await page.getByRole("tab", { name: "Overview" }).click();
    await expect(page.getByRole("switch")).toBeChecked({ checked: false });
    await page.getByRole("switch").click();
    await expect(page.getByRole("switch")).toBeChecked();
    await expect(page.getByText("Published", { exact: true }).first()).toBeVisible();

    // Verify it shows Published on the Instructor Dashboard's own course list, and is publicly
    // discoverable via Explore (a real cross-surface check, not just the editor's own state).
    await page.goto("/en/instructor");
    const row = page.locator(".mtx-management-card", { hasText: courseTitle });
    await expect(row.getByText("Published", { exact: true })).toBeVisible();

    await page.goto("/en/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(courseTitle);
    await expect(page.getByRole("link", { name: courseTitle, exact: true })).toBeVisible();
  });
});
