import { getTranslations } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { PublicNavbar } from "@/components/navigation/public-navbar";
import { Button, CategoryChip, CourseCard, CourseThumbnail, Icon, LearningPathCard } from "@/components/ui";
import { listCourses } from "@/lib/api/courses";
import { listLearningPaths } from "@/lib/api/learning-paths";
import { listCategories } from "@/lib/api/categories";
import { LEVEL_LABEL_KEYS, CONTENT_LANGUAGE_LABEL_KEYS } from "@/lib/i18n/course-labels";

// Revalidated on a schedule, not fully static (architecture/WEB_ARCHITECTURE.md § 1) — a
// build-time-frozen featured-courses list would go stale the moment an Instructor
// publishes/unpublishes a course.
export const revalidate = 300;

/** Landing (product/SCREEN_INVENTORY.md § 1). Server-rendered per architecture/WEB_ARCHITECTURE.md
 * § 1 — featured content is fetched directly (no client hooks) so the crawlable HTML includes
 * real course/path data, not an empty shell. */
export default async function LandingPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations("landing");
  const explore = await getTranslations("explore");
  const paths = await getTranslations("learningPaths");

  const [{ items: courses }, learningPaths, categories] = await Promise.all([
    listCourses({ limit: 4, language: locale }),
    listLearningPaths(),
    listCategories(),
  ]);
  const categoryNameById = new Map(categories.map((c) => [c.id, c.name]));
  const totalCourseCount = categories.reduce((sum, c) => sum + c.courseCount, 0);
  const collageCourses = courses.slice(0, 4);

  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content" className="mx-auto max-w-[1280px] px-4 py-12 tablet:px-6 desktop:px-8">
        <section className="mtx-hero">
          {/* Responsive sizing comes from the generated media-query blocks on the CSS vars
              themselves (tools/token-pipeline/generate.js), not a Tailwind breakpoint variant —
              custom classes like mtx-text-* aren't Tailwind utilities Tailwind can prefix. */}
          <div className="mtx-hero-content">
            <CategoryChip label={t("heroEyebrow")} />
            <h1 className="mtx-text-display-large">{t("heroTitle")}</h1>
            <p className="mtx-text-body-large" style={{ color: "var(--color-text-secondary)", maxWidth: "60ch" }}>
              {t("heroSubtitle")}
            </p>
            <div className="flex flex-wrap gap-3">
              <Link href="/explore">
                <Button variant="primary">{t("exploreCourses")}</Button>
              </Link>
              <Link href="/register">
                <Button variant="secondary">{t("getStarted")}</Button>
              </Link>
            </div>
            <div className="mtx-hero-stats">
              <span className="mtx-hero-stat">
                <Icon name="myLearning" size={20} />
                {t("statCourses", { count: totalCourseCount })}
              </span>
              <span className="mtx-hero-stat">
                <Icon name="learningPaths" size={20} />
                {t("statPaths", { count: learningPaths.length })}
              </span>
              <span className="mtx-hero-stat">
                <Icon name="search" size={20} />
                {t("statLanguages")}
              </span>
            </div>
          </div>

          {collageCourses.length > 0 && (
            <div className="mtx-hero-collage">
              {collageCourses.map((course) => (
                <div key={course.id} className="mtx-hero-collage-item">
                  <CourseThumbnail
                    mediaId={course.thumbnailMediaId}
                    className="mtx-hero-collage-thumbnail"
                    seed={course.id}
                    categoryId={course.categoryId}
                    iconSize={24}
                  />
                  <p className="mtx-text-label-large mtx-hero-collage-title">{course.title}</p>
                </div>
              ))}
            </div>
          )}
        </section>

        {courses.length > 0 && (
          <section className="py-8">
            <div className="mb-4 flex items-end justify-between gap-4">
              <div>
                <h2 className="mtx-text-heading-h2">{t("popularCoursesTitle")}</h2>
                <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
                  {t("popularCoursesSubtitle")}
                </p>
              </div>
              <Link href="/explore" className="mtx-btn mtx-btn-text" style={{ whiteSpace: "nowrap" }}>
                {t("seeAllCourses")}
                <Icon name="arrowForward" size={20} className="mtx-icon-mirror-rtl" />
              </Link>
            </div>
            <div className="mtx-course-grid">
              {courses.map((course) => (
                <CourseCard
                  key={course.id}
                  course={course}
                  categoryName={categoryNameById.get(course.categoryId)}
                  levelLabel={explore(LEVEL_LABEL_KEYS[course.level])}
                  locale={locale}
                  viewLabel={explore("viewCourse")}
                  contentLanguageLabel={
                    course.contentLanguage !== locale
                      ? explore("contentLanguageBadge", {
                          language: explore(CONTENT_LANGUAGE_LABEL_KEYS[course.contentLanguage as "en" | "ar"]),
                        })
                      : undefined
                  }
                />
              ))}
            </div>
          </section>
        )}

        {learningPaths.length > 0 && (
          <section className="py-8">
            <h2 className="mtx-text-heading-h2">{paths("title")}</h2>
            <p className="mtx-text-body-small mb-4" style={{ color: "var(--color-text-secondary)" }}>
              {t("learningPathsSubtitle")}
            </p>
            <div className="mtx-course-grid">
              {learningPaths.slice(0, 3).map((path) => (
                <LearningPathCard
                  key={path.id}
                  path={path}
                  courseCountLabel={paths("courseCount", { count: path.courseCount })}
                  viewLabel={paths("viewPath")}
                />
              ))}
            </div>
          </section>
        )}
      </main>
    </>
  );
}
