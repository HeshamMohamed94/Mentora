import { expect, test } from "@playwright/test";
import { registerNewStudent } from "./helpers";

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
    await expect(page.getByText("الإعدادات")).toBeVisible(); // settings.title AR

    await page.goto("/ar/app");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { name: "لوحة التحكم" })).toBeVisible(); // dashboard.title AR
  });

  test("switching back to English restores ltr and English strings", async ({ page }) => {
    await registerNewStudent(page);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible(); // session fully settled
    await page.goto("/ar/app/settings");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByText("الإعدادات")).toBeVisible();

    await page.getByRole("combobox", { name: "اللغة" }).click();
    await page.getByRole("option", { name: "English" }).click();
    await page.waitForURL(/\/en\/app\/settings/);

    await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
    await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  });
});
