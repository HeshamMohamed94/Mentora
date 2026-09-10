"use client";

import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import {
  useLearningPath,
  useFollowLearningPath,
  useUnfollowLearningPath,
} from "@/lib/api/learning-paths";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { Button, ProgressBar, ErrorState, CourseThumbnail } from "@/components/ui";

export function LearningPathDetailsScreen({ pathId }: { pathId: string }) {
  const t = useTranslations("learningPaths");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const query = useLearningPath(pathId, locale);
  const { data: user } = useCurrentUser();
  const follow = useFollowLearningPath(pathId);
  const unfollow = useUnfollowLearningPath(pathId);

  if (query.isLoading) {
    return (
      <div className="mx-auto max-w-[900px] px-4 py-8">
        <div className="mtx-skeleton" style={{ height: 200, borderRadius: "var(--radius-large)" }} />
      </div>
    );
  }

  if (query.isError || !query.data) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={t("errorTitle")} retryLabel={tCommon("retry")} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const path = query.data;
  const basePath = user ? "/app" : "";

  let followAction: React.ReactNode;
  if (!user) {
    followAction = (
      <Link href={{ pathname: "/login", query: { redirect: `/paths/${pathId}` } }}>
        <Button variant="secondary">{t("loginToFollow")}</Button>
      </Link>
    );
  } else if (path.isFollowing) {
    followAction = (
      <Button variant="secondary" onClick={() => unfollow.mutate()} loading={unfollow.isPending}>
        {t("unfollow")}
      </Button>
    );
  } else {
    followAction = (
      <Button variant="primary" onClick={() => follow.mutate()} loading={follow.isPending}>
        {t("follow")}
      </Button>
    );
  }

  return (
    <div className="mx-auto max-w-[900px] px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-2">{path.title}</h1>
      <p className="mtx-text-body-medium mb-2" style={{ color: "var(--color-text-secondary)" }}>
        {path.description}
      </p>
      <p className="mtx-text-caption mb-4">{t("courseCount", { count: path.courses.length })}</p>
      {path.progressPercent !== undefined && (
        <div className="mb-4">
          <ProgressBar percent={path.progressPercent} label={path.title} />
        </div>
      )}
      <div className="mb-6">{followAction}</div>

      <ol className="flex flex-col gap-3">
        {path.courses.map((course, index) => (
          <li key={course.id}>
            <Link href={`${basePath}/courses/${course.id}`} className="mtx-card mtx-path-course-row">
              <span className="mtx-text-heading-h4 mtx-path-course-index">{index + 1}</span>
              <CourseThumbnail mediaId={course.thumbnailMediaId} className="mtx-checkout-thumbnail" iconSize={20} seed={course.id} />
              <span className="mtx-text-label-large">{course.title}</span>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
