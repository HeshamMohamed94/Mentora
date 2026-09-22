import { expect, test } from "@playwright/test";
import { registerNewStudent } from "./helpers";

/** The one seeded course authored natively in Arabic (`backend/.../SeedData.kt` `UX_COURSE`) — the
 * right fixture for an Arabic-locale Explore assertion, since Explore's `language` filter only
 * surfaces courses whose content language matches the active UI locale (or that carry a translation
 * for it) — `Building Reliable REST APIs` (English-authored, no `ar` translation) is correctly absent
 * from `/ar/app/explore`, which is real product behavior, not a defect. */
const AR_COURSE_TITLE = "أساسيات تصميم تجربة المستخدم";

/** architecture/TESTING_STRATEGY.md § 6 — Language switch English <-> Arabic. Key assertion: `dir`
 * attribute flips, a known string renders translated, a subsequent page navigation preserves the
 * chosen locale. */
test.describe("Language switch EN <-> AR", () => {
  test("switching to Arabic in Settings flips dir, translates a known string, and persists across navigation", async ({ page }) => {
    await registerNewStudent(page);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible(); // session fully settled
    await page.goto("/en/app/settings");
    await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
    await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();

    await page.getByRole("combobox", { name: "Language" }).click();
    await page.getByRole("option", { name: "العربية" }).click();
    await page.waitForURL(/\/ar\/app\/settings/);

    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "الإعدادات" })).toBeVisible(); // settings.title AR

    await page.goto("/ar/app");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "لوحة التحكم" })).toBeVisible(); // dashboard.title AR
  });

  // B8 — sample a couple more screens beyond Settings, confirming the persisted `ar` locale carries
  // through to other in-scope surfaces (Explore, Course Details), not just the screen where the
  // switch itself happened.
  test("the persisted Arabic locale also renders Explore and Course Details correctly", async ({ page }) => {
    await registerNewStudent(page);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible(); // session fully settled
    await page.goto("/en/app/settings");
    await page.getByRole("combobox", { name: "Language" }).click();
    await page.getByRole("option", { name: "العربية" }).click();
    await page.waitForURL(/\/ar\/app\/settings/);

    await page.goto("/ar/app/explore");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "استكشف", exact: true })).toBeVisible(); // explore.title AR
    await page.getByLabel("ابحث عن الدورات", { exact: true }).fill(AR_COURSE_TITLE);
    const courseLink = page.getByRole("link", { name: AR_COURSE_TITLE, exact: true });
    await expect(courseLink).toBeVisible();
    await courseLink.click();
    await page.waitForURL(/\/ar\/app\/courses\//);

    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: AR_COURSE_TITLE, exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "المنهج" })).toBeVisible(); // courseDetails.curriculum AR
  });

  test("switching back to English restores ltr and English strings", async ({ page }) => {
    await registerNewStudent(page);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible(); // session fully settled
    await page.goto("/ar/app/settings");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "الإعدادات" })).toBeVisible();

    await page.getByRole("combobox", { name: "اللغة" }).click();
    await page.getByRole("option", { name: "English" }).click();
    await page.waitForURL(/\/en\/app\/settings/);

    await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
    await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  });
});
