"use client";

import { useLocale, useTranslations } from "next-intl";
import { useCourse } from "@/lib/api/courses";
import { useCategories } from "@/lib/api/categories";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { useIsEnrolled } from "@/lib/api/enrollment";
import { Button, InstructorCard, ErrorState, CourseThumbnail } from "@/components/ui";
import { Link } from "@/i18n/navigation";
import { formatPrice } from "@/lib/i18n/format";
import { LEVEL_LABEL_KEYS, CONTENT_LANGUAGE_LABEL_KEYS } from "@/lib/i18n/course-labels";

/**
 * product/SCREEN_INVENTORY.md § 3 (Course Details). Shared between Guest (`/courses/:id`) and
 * Student (`/app/courses/:id`) per INFORMATION_ARCHITECTURE.md § 2.
 */
export function CourseDetailsScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("courseDetails");
  const tExplore = useTranslations("explore");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const courseQuery = useCourse(courseId, locale);
  const categoriesQuery = useCategories();
  const { data: user } = useCurrentUser();
  const isEnrolled = useIsEnrolled(courseId);

  if (courseQuery.isLoading) {
    return (
      <div className="mx-auto max-w-[900px] px-4 py-8">
        <div className="mtx-skeleton mtx-card-thumbnail" style={{ borderRadius: "var(--radius-large)" }} />
      </div>
    );
  }

  if (courseQuery.isError || !courseQuery.data) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={t("errorTitle")} retryLabel={tCommon("retry")} onRetry={() => courseQuery.refetch()} />
      </div>
    );
  }

  const course = courseQuery.data;
  const categoryName = categoriesQuery.data?.find((c) => c.id === course.categoryId)?.name;
  const totalLessons = course.sections.reduce((sum, s) => sum + s.lessons.length, 0);

  let cta: React.ReactNode;
  if (!user) {
    cta = (
      <Link href={{ pathname: "/login", query: { redirect: `/app/checkout/${courseId}` } }}>
        <Button variant="primary">{t("loginToEnroll")}</Button>
      </Link>
    );
  } else if (isEnrolled) {
    cta = (
      <Link href={`/app/learn/${courseId}`}>
        <Button variant="primary">{t("continueLearning")}</Button>
      </Link>
    );
  } else {
    cta = (
      <Link href={`/app/checkout/${courseId}`}>
        <Button variant="primary">{t("enroll")}</Button>
      </Link>
    );
  }

  return (
    <div className="mx-auto max-w-[900px] px-4 py-8 tablet:px-6 desktop:px-8">
      <CourseThumbnail
        mediaId={course.thumbnailMediaId}
        className="mtx-course-hero-thumbnail"
        iconSize={48}
        seed={course.id}
        categoryId={course.categoryId}
        badge={categoryName && <span className="mtx-thumbnail-badge">{categoryName}</span>}
      />

      <div className="mt-4 flex flex-col gap-3">
        <h1 className="mtx-text-heading-h1">{course.title}</h1>
        <p className="mtx-text-body-medium" style={{ color: "var(--color-text-secondary)" }}>
          {course.instructorName} · ★ {course.ratingSeed.toFixed(1)} · {tExplore(LEVEL_LABEL_KEYS[course.level])}
        </p>
        {course.contentLanguage !== locale && (
          <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
            {tExplore("contentLanguageBadge", {
              language: tExplore(CONTENT_LANGUAGE_LABEL_KEYS[course.contentLanguage as "en" | "ar"]),
            })}
          </p>
        )}
        <p className="mtx-text-heading-h3">{formatPrice(course.priceDisplay.amount, course.priceDisplay.currency, locale)}</p>
        <div>{cta}</div>
        <p className="mtx-text-body-medium mt-4">{course.description}</p>

        <h2 className="mtx-text-heading-h3 mt-4">
          {t("curriculum")} — {t("lessonsCount", { count: totalLessons })}
        </h2>
        <ol className="mtx-course-details-curriculum">
          {course.sections.map((section) => (
            <li key={section.sectionId} className="mtx-course-details-curriculum-section">
              <p className="mtx-text-label-large">{section.title}</p>
              <ul>
                {section.lessons.map((lesson) => (
                  <li key={lesson.lessonId} className="mtx-text-body-small mtx-course-details-lesson-row">
                    {lesson.title}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>

        <h2 className="mtx-text-heading-h3 mt-4">{t("aboutInstructor")}</h2>
        <InstructorCard name={course.instructorName} />
      </div>
    </div>
  );
}
