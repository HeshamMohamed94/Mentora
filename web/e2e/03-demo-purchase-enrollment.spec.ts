import { expect, test } from "@playwright/test";
import { registerNewStudent } from "./helpers";

const COURSE = "Kotlin Coroutines in Practice";

/** architecture/TESTING_STRATEGY.md § 6 — Demo Purchase -> Enrollment. Key assertion: no
 * real-payment field ever renders; completion creates exactly one enrollment (re-running the flow
 * against an already-enrolled course shows "Continue Learning," never a duplicate purchase). */
test.describe("Demo Purchase -> Enrollment", () => {
  test("checkout has zero payment-card fields and completes into a real enrollment", async ({ page }) => {
    await registerNewStudent(page);
    await page.goto("/en/app/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(COURSE);
    await page.getByRole("link", { name: COURSE, exact: true }).click();
    await page.getByRole("button", { name: "Enroll", exact: true }).click();
    await page.waitForURL(/\/en\/app\/checkout\//);

    await expect(page.getByText("Demo Payment")).toBeVisible();
    await expect(page.locator('input[type="text"][name*="card" i]')).toHaveCount(0);
    await expect(page.locator('input[name*="cvv" i], input[name*="cvc" i]')).toHaveCount(0);
    await expect(page.getByLabel(/card number/i)).toHaveCount(0);

    await page.getByRole("button", { name: "Complete Demo Purchase" }).click();
    await expect(page.getByText("Payment Successful")).toBeVisible();
    await expect(page.getByText("You're now enrolled!")).toBeVisible();

    await page.getByRole("button", { name: "Start Learning" }).click();
    await page.waitForURL(/\/en\/app\/learn\//);
  });

  test("re-visiting Course Details for an already-enrolled course shows Continue Learning, never a duplicate purchase option", async ({ page }) => {
    await registerNewStudent(page);
    await page.goto("/en/app/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(COURSE);
    await page.getByRole("link", { name: COURSE, exact: true }).click();
    await page.getByRole("button", { name: "Enroll", exact: true }).click();
    await page.getByRole("button", { name: "Complete Demo Purchase" }).click();
    await expect(page.getByText("Payment Successful")).toBeVisible();

    await page.goto("/en/app/explore");
    await page.getByLabel("Search courses", { exact: true }).fill(COURSE);
    await page.getByRole("link", { name: COURSE, exact: true }).click();
    await page.waitForURL(/\/en\/app\/courses\//);
    await expect(page.getByRole("button", { name: "Continue Learning", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Enroll", exact: true })).toHaveCount(0);
  });
});
