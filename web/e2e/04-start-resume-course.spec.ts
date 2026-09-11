import { expect, test } from "@playwright/test";
import { QUIZ_COURSE_TITLE, enrollInCourse, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Start/Resume Course. Key assertion: resuming lands on
 * the last incomplete lesson at the last known position.
 *
 * QUIZ_COURSE_TITLE's lesson 2 ("Applied workshop") still carries the original seed placeholder
 * video (execution/DECISIONS_LOG.md D59/D60 only upgraded each course's *first* lesson to a real,
 * playable video) — its <video> genuinely errors in a real browser (newly confirmed by this very
 * suite, not previously visible in the claude-in-chrome environment's own document.hidden-stalled
 * video testing). So this test proves the real "resume at last position" mechanic (a video
 * position report via `onPause`/`onTimeUpdate`, course-player-screen.tsx `savePosition`) using
 * lesson 1's real, working video instead of depending on lesson 2's known-placeholder one. */
test.describe("Start / Resume Course", () => {
  test("starting a freshly enrolled course lands on lesson 1 of the curriculum", async ({ page }) => {
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible();
  });

  test("resuming a partially-watched lesson lands back on it at the last reported position", async ({ page }) => {
    test.slow(); // real video metadata load can take a while under local dev conditions
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible();

    const playButton = page.getByRole("button", { name: "Play", exact: true });
    await expect(playButton).toBeEnabled({ timeout: 30_000 });
    await page.evaluate(() => {
      const video = document.querySelector("video");
      if (video) video.currentTime = 8;
    });
    await playButton.click();
    await expect(page.getByRole("button", { name: "Pause", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Pause", exact: true }).click(); // reports position ~8s

    // Simulate a real "leave and come back later" resume, not an in-memory navigation.
    await page.goto("/en/app/my-learning");
    await page.getByRole("link", { name: "Resume", exact: true }).click();
    await page.waitForURL(/\/en\/app\/learn\//);
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible();
    await expect(page.getByRole("button", { name: "Play", exact: true })).toBeEnabled({ timeout: 30_000 });

    const resumedTime = await page.evaluate(() => document.querySelector("video")?.currentTime ?? 0);
    expect(resumedTime).toBeGreaterThan(5); // resumed near the ~8s mark, not restarted at 0
  });
});
