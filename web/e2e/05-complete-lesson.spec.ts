import { expect, test } from "@playwright/test";
import { QUIZ_COURSE_TITLE, enrollInCourse, registerNewStudent } from "./helpers";

/** architecture/TESTING_STRATEGY.md § 6 — Complete a Lesson. Key assertion: progress bar
 * updates; auto-advance to next lesson.
 *
 * The Course Player has two real, distinct ways to complete a lesson (course-player-screen.tsx
 * `markComplete`): the "Mark Complete" button (`autoAdvance = false` — updates progress but stays
 * on the same lesson, since a learner may still want to re-watch or read Resources) and the video
 * reaching its natural end (`onEnded` -> `markComplete(true)` — this is the actual auto-advance
 * path). Both are tested here as the two genuinely different product behaviors they are. */
test.describe("Complete a Lesson", () => {
  test("Mark Complete updates the progress bar and shows Completed, without leaving the lesson", async ({ page }) => {
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);

    const progressBar = page.getByRole("progressbar", { name: "Overall course progress" });
    await expect(progressBar).toHaveAttribute("aria-valuenow", "0");
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible();

    await page.getByRole("button", { name: "Mark Complete" }).click();

    await expect(progressBar).toHaveAttribute("aria-valuenow", "50");
    await expect(page.getByRole("button", { name: "Completed", exact: true })).toBeVisible();
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible(); // still on lesson 1 — no auto-advance here

    const curriculum = page.getByRole("complementary", { name: "Course curriculum" });
    await expect(curriculum.getByText("Completed").first()).toBeVisible();
  });

  test("the video reaching its end marks the lesson complete and auto-advances to the next lesson", async ({ page }) => {
    await registerNewStudent(page);
    await enrollInCourse(page, QUIZ_COURSE_TITLE);
    await expect(page.getByText("Lesson 1 of 2")).toBeVisible();

    const playButton = page.getByRole("button", { name: "Play", exact: true });
    await expect(playButton).toBeEnabled({ timeout: 15_000 });
    // Seek to just before the end, then play — the real <video> naturally reaches its end and
    // fires `ended` within a moment, exercising the real auto-advance path (not a fake shortcut).
    await page.evaluate(() => {
      const video = document.querySelector("video");
      if (video && video.duration) video.currentTime = Math.max(0, video.duration - 0.4);
    });
    await playButton.click();

    await expect(page.getByText("Lesson 2 of 2")).toBeVisible({ timeout: 15_000 });
    const progressBar = page.getByRole("progressbar", { name: "Overall course progress" });
    await expect(progressBar).toHaveAttribute("aria-valuenow", "50");
  });
});
