import { expect, test } from "@playwright/test";
import { SEED_PASSWORD, registerNewStudent, uniqueEmail } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Register/Login. Key assertion: successful auth lands on
 * the correct role-appropriate screen; wrong credentials show the generic error, never revealing
 * which field was wrong (architecture/AUTH_SECURITY.md § 2). */
test.describe("Register / Login", () => {
  test("register lands a new Student on the Dashboard", async ({ page }) => {
    const { email } = await registerNewStudent(page);
    await expect(page).toHaveURL(/\/en\/app$/);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
    void email;
  });

  test("login with the just-registered account lands on the Dashboard", async ({ page }) => {
    const { email } = await registerNewStudent(page);
    await page.getByRole("button", { name: "Logout" }).click();
    await page.waitForURL(/\/en\/login/);
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(SEED_PASSWORD);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/en\/app$/);
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  });

  test("wrong password shows the generic incorrect-credentials error, not a field-specific one", async ({ page }) => {
    const email = uniqueEmail("nonexistent");
    await page.goto("/en/login");
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill("WrongPassword1");
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page.getByText("Incorrect email or password.")).toBeVisible();
    await expect(page).toHaveURL(/\/en\/login/);
  });
});
