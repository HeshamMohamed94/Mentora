"use client";

import { useState, type FormEvent } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { useMyLearning, type MyLearningItem } from "@/lib/api/my-learning";
import { useCourses, type LessonResponse, type SectionResponse } from "@/lib/api/courses";
import { useFollowedLearningPaths } from "@/lib/api/learning-paths";
import {
  CourseProgressCard,
  CourseCard,
  LearningPathCard,
  StatCard,
  EmptyState,
  Icon,
  SearchField,
  ThemeToggle,
} from "@/components/ui";
import { LEVEL_LABEL_KEYS } from "@/lib/i18n/course-labels";
import { formatCount } from "@/lib/i18n/format";
import { Link, useRouter } from "@/i18n/navigation";

/** Same ordering rule as CoursePlayerScreen — sections then lessons, both by `order`. */
function orderedLessons(sections: SectionResponse[]): LessonResponse[] {
  return [...sections]
    .sort((first, second) => first.order - second.order)
    .flatMap((section) => [...section.lessons].sort((first, second) => first.order - second.order));
}

type GreetingBucket = "morning" | "afternoon" | "evening" | "night";

const GREETING_KEYS: Record<GreetingBucket, string> = {
  morning: "greetingMorning",
  afternoon: "greetingAfternoon",
  evening: "greetingEvening",
  night: "greetingNight",
};

/** ux/SCREEN_UX_SPECS.md § 8 acceptance criteria (2026-09-10 ticket): local device time, not
 * server/backend time — `hour` must come from a client-side `Date`. */
function greetingBucket(hour: number): GreetingBucket {
  if (hour >= 5 && hour < 12) return "morning";
  if (hour >= 12 && hour < 17) return "afternoon";
  if (hour >= 17 && hour < 21) return "evening";
  return "night";
}

interface UpNextEntry {
  key: string;
  kind: "next-lesson" | "continue";
  courseId: string;
  courseTitle: string;
  lessonNumber: number;
  lessonTitle: string;
}

/**
 * Reference showcase's Dashboard "UP NEXT" module (Section 22, Student Dashboard mockup) lists
 * concrete next actions rather than a static widget. Built only from data already on
 * `MyLearningItem` (course sections + progress.currentLessonId) — no extra endpoints, no
 * fabricated content (a quiz-due line would need per-course quiz metadata this screen doesn't
 * fetch, so it's intentionally omitted rather than faked).
 */
function upNextEntries(continueItems: MyLearningItem[]): UpNextEntry[] {
  const entries: UpNextEntry[] = [];
  const primary = continueItems[0];
  if (primary) {
    const lessons = orderedLessons(primary.course.sections);
    const currentIndex = lessons.findIndex((lesson) => lesson.lessonId === primary.progress.currentLessonId);
    const nextLesson = lessons[currentIndex + 1];
    if (nextLesson) {
      entries.push({
        key: `next-${primary.course.id}`,
        kind: "next-lesson",
        courseId: primary.course.id,
        courseTitle: primary.course.title,
        lessonNumber: currentIndex + 2,
        lessonTitle: nextLesson.title,
      });
    }
  }
  const secondary = continueItems[1];
  if (secondary) {
    const lessons = orderedLessons(secondary.course.sections);
    const currentIndex = lessons.findIndex((lesson) => lesson.lessonId === secondary.progress.currentLessonId);
    const current = lessons[currentIndex] ?? lessons[0];
    if (current) {
      entries.push({
        key: `continue-${secondary.course.id}`,
        kind: "continue",
        courseId: secondary.course.id,
        courseTitle: secondary.course.title,
        lessonNumber: Math.max(currentIndex, 0) + 1,
        lessonTitle: current.title,
      });
    }
  }
  return entries.slice(0, 3);
}

/**
 * product/SCREEN_INVENTORY.md § 8 (Dashboard / Home). "Certificates" stat reuses the completed-
 * course count rather than a separate `GET /certificates` call — a course only reaches
 * `courseCompletedAt` at the exact moment its certificate becomes eligible for issuance
 * (backend/CertificateService.checkAndIssueIfComplete), so the two counts are equal by
 * construction, not an approximation.
 */
export function DashboardScreen() {
  const t = useTranslations("dashboard");
  const tExplore = useTranslations("explore");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const router = useRouter();
  const { data: user } = useCurrentUser();
  const myLearning = useMyLearning();
  const followedPaths = useFollowedLearningPaths();
  const [search, setSearch] = useState("");

  function handleSearchSubmit(event: FormEvent) {
    event.preventDefault();
    const query = search.trim();
    if (query) router.push(`/app/explore?q=${encodeURIComponent(query)}`);
  }

  const firstName = user?.name.split(" ")[0] ?? "";
  const greetingKey = GREETING_KEYS[greetingBucket(new Date().getHours())];

  const enrolledIds = new Set(myLearning.items.map((item) => item.course.id));
  const recommendedQuery = useCourses({ limit: 8 });
  const recommended = (recommendedQuery.data?.items ?? []).filter((c) => !enrolledIds.has(c.id)).slice(0, 4);

  const inProgress = myLearning.items.filter((item) => item.progress.completionPercent < 100);
  const completedCount = myLearning.items.length - inProgress.length;
  const continueItems = inProgress.slice(0, 3);
  const upNext = upNextEntries(continueItems);
  const avgProgress = myLearning.items.length
    ? Math.round(
        myLearning.items.reduce((sum, item) => sum + item.progress.completionPercent, 0) / myLearning.items.length,
      )
    : 0;

  return (
    <div className="mx-auto max-w-[1280px] px-4 py-8 tablet:px-6 desktop:px-8">
      <div className="mb-6 flex items-center justify-between gap-4">
        <form onSubmit={handleSearchSubmit} className="w-full tablet:max-w-[440px]">
          <SearchField
            value={search}
            onChange={setSearch}
            label={t("searchLabel")}
            placeholder={t("searchPlaceholder")}
            clearLabel={tCommon("clearSearch")}
          />
        </form>
        <ThemeToggle />
      </div>

      <div className="mb-8">
        <h1 className="mtx-text-heading-h1">{t("title")}</h1>
        {user && (
          <>
            <p className="mtx-text-heading-h3 mt-2">{t(greetingKey, { name: firstName })}</p>
            <p className="mtx-text-body-large mt-1" style={{ color: "var(--color-text-secondary)" }}>
              {t("subtitle")}
            </p>
          </>
        )}
      </div>

      <div className="mb-8 grid grid-cols-2 gap-4 tablet:grid-cols-4">
        <StatCard value={formatCount(inProgress.length, locale)} label={t("statInProgress")} />
        <StatCard value={t("percentComplete", { percent: formatCount(avgProgress, locale) })} label={t("statAvgProgress")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCompleted")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCertificates")} />
      </div>

      <div className="grid grid-cols-1 gap-8 desktop:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] desktop:items-start">
        <div className="flex flex-col gap-8">
          {continueItems.length > 0 && (
            <section>
              <h2 className="mtx-text-heading-h2 mb-4">{t("continueLearning")}</h2>
              <div className="mtx-progress-list">
                {continueItems.map((item) => (
                  <CourseProgressCard
                    key={item.course.id}
                    course={item.course}
                    percent={item.progress.completionPercent}
                    percentLabel={t("percentComplete", { percent: formatCount(item.progress.completionPercent, locale) })}
                    resumeLabel={t("resume")}
                  />
                ))}
              </div>
            </section>
          )}

          {!myLearning.isLoading && myLearning.items.length === 0 && (
            <EmptyState
              title={t("emptyTitle")}
              description={t("emptyDescription")}
              actionLabel={t("exploreCourses")}
              onAction={() => router.push("/app/explore")}
            />
          )}

          {followedPaths.items.length > 0 && (
            <section>
              <h2 className="mtx-text-heading-h2 mb-4">{t("pathsInProgress")}</h2>
              <div className="mtx-course-grid">
                {followedPaths.items.map((path) => (
                  <LearningPathCard
                    key={path.id}
                    path={{ id: path.id, title: path.title, description: path.description, courseCount: path.courses.length }}
                    courseCountLabel={t("pathCourseCount", { count: path.courses.length })}
                    viewLabel={t("viewPath")}
                    basePath="/app"
                  />
                ))}
              </div>
            </section>
          )}

          {recommended.length > 0 && (
            <section>
              <h2 className="mtx-text-heading-h2 mb-4">{t("recommended")}</h2>
              <div className="mtx-course-grid">
                {recommended.map((course) => (
                  <CourseCard
                    key={course.id}
                    course={course}
                    levelLabel={tExplore(LEVEL_LABEL_KEYS[course.level])}
                    locale={locale}
                    viewLabel={tExplore("viewCourse")}
                    basePath="/app"
                  />
                ))}
              </div>
            </section>
          )}
        </div>

        <div className="flex flex-col gap-6">
          <aside className="mtx-dashboard-ai-nudge">
            <span className="mtx-dashboard-ai-nudge-eyebrow">
              <Icon name="aiTutor" size={20} />
              {t("aiTutorEyebrow")}
            </span>
            <h2 className="mtx-text-heading-h4">{t("aiTutorNudgeTitle")}</h2>
            <p className="mtx-text-body-small">{t("aiTutorNudgeDescription")}</p>
            <Link href="/app/ai-tutor" className="mtx-btn mtx-btn-primary">
              {t("aiTutorNudgeCta")}
            </Link>
          </aside>

          {upNext.length > 0 && (
            <aside className="mtx-dashboard-upnext">
              <span className="mtx-dashboard-upnext-eyebrow">{t("upNextTitle")}</span>
              <ul className="mtx-dashboard-upnext-list">
                {upNext.map((entry) => (
                  <li key={entry.key}>
                    <Link href={`/app/learn/${entry.courseId}`} className="mtx-dashboard-upnext-link">
                      {entry.kind === "next-lesson"
                        ? t("upNextLesson", { number: formatCount(entry.lessonNumber, locale), title: entry.lessonTitle })
                        : t("upNextContinue", {
                            title: entry.courseTitle,
                            number: formatCount(entry.lessonNumber, locale),
                          })}
                    </Link>
                  </li>
                ))}
              </ul>
            </aside>
          )}
        </div>
      </div>
    </div>
  );
}
