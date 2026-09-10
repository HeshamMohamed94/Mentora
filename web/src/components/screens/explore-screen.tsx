"use client";

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations, useLocale } from "next-intl";
import { useCourses } from "@/lib/api/courses";
import { useCategories } from "@/lib/api/categories";
import { useDebouncedValue } from "@/lib/hooks/use-debounced-value";
import { CourseCard, CategoryChip, SearchField, EmptyState, ErrorState, CourseGridSkeleton } from "@/components/ui";
import { LEVEL_LABEL_KEYS, CONTENT_LANGUAGE_LABEL_KEYS, type CourseLevel } from "@/lib/i18n/course-labels";
import { useCurrentUser } from "@/lib/auth/use-current-user";

const LEVELS: CourseLevel[] = ["beginner", "intermediate", "advanced"];

/**
 * product/SCREEN_INVENTORY.md § 2 (Explore). One shared screen for Guest (`/explore`) and
 * Student (`/app/explore`) per product/INFORMATION_ARCHITECTURE.md § 2's "same screens, not
 * duplicate screens" rule — thin page files under (public)/explore and app/explore both
 * render this component.
 */
export function ExploreScreen() {
  const t = useTranslations("explore");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const { data: user } = useCurrentUser();
  const basePath = user ? "/app" : "";
  const searchParams = useSearchParams();
  const [search, setSearch] = useState(() => searchParams.get("q") ?? "");
  const [category, setCategory] = useState<string | undefined>();
  const [level, setLevel] = useState<string | undefined>();
  const debouncedSearch = useDebouncedValue(search);

  const categoriesQuery = useCategories();
  const coursesQuery = useCourses({
    q: debouncedSearch || undefined,
    category,
    level,
    language: locale,
    limit: 24,
  });

  const categoryNameById = useMemo(() => {
    const map = new Map<string, string>();
    categoriesQuery.data?.forEach((c) => map.set(c.id, c.name));
    return map;
  }, [categoriesQuery.data]);

  const hasFilters = Boolean(search || category || level);

  function clearFilters() {
    setSearch("");
    setCategory(undefined);
    setLevel(undefined);
  }

  return (
    <div className="mx-auto max-w-[1440px] px-4 py-8 tablet:px-6 desktop:px-8 large-desktop:px-12">
      <h1 className="mtx-text-heading-h1 mb-6">{t("title")}</h1>
      <div className="mb-4">
        <SearchField
          value={search}
          onChange={setSearch}
          label={t("searchLabel")}
          placeholder={t("searchPlaceholder")}
          clearLabel={tCommon("clearSearch")}
        />
      </div>
      <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label={t("title")}>
        <CategoryChip label={t("allCategories")} selected={!category} onClick={() => setCategory(undefined)} />
        {categoriesQuery.data?.map((c) => (
          <CategoryChip key={c.id} label={c.name} selected={category === c.id} onClick={() => setCategory(c.id)} />
        ))}
        {LEVELS.map((lvl) => (
          <CategoryChip
            key={lvl}
            label={t(LEVEL_LABEL_KEYS[lvl])}
            selected={level === lvl}
            onClick={() => setLevel(level === lvl ? undefined : lvl)}
          />
        ))}
      </div>

      {coursesQuery.isLoading && <CourseGridSkeleton />}

      {coursesQuery.isError && (
        <ErrorState description={t("errorTitle")} retryLabel={tCommon("retry")} onRetry={() => coursesQuery.refetch()} />
      )}

      {coursesQuery.data && coursesQuery.data.items.length === 0 && (
        <EmptyState
          title={t("emptyTitle")}
          description={t("emptyDescription")}
          actionLabel={hasFilters ? t("clearFilters") : undefined}
          onAction={hasFilters ? clearFilters : undefined}
        />
      )}

      {coursesQuery.data && coursesQuery.data.items.length > 0 && (
        <>
          <p className="mtx-text-caption mb-3" style={{ color: "var(--color-text-secondary)" }}>
            {t("resultCount", { count: coursesQuery.data.items.length })}
          </p>
          <div className="mtx-course-grid">
            {coursesQuery.data.items.map((course) => (
              <CourseCard
                key={course.id}
                course={course}
                categoryName={categoryNameById.get(course.categoryId)}
                levelLabel={t(LEVEL_LABEL_KEYS[course.level])}
                locale={locale}
                viewLabel={t("viewCourse")}
                basePath={basePath}
                contentLanguageLabel={
                  course.contentLanguage !== locale
                    ? t("contentLanguageBadge", {
                        language: t(CONTENT_LANGUAGE_LABEL_KEYS[course.contentLanguage as "en" | "ar"]),
                      })
                    : undefined
                }
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
