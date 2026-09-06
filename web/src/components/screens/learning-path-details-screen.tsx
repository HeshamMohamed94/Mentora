"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import {
  useLearningPath,
  useFollowLearningPath,
  useUnfollowLearningPath,
} from "@/lib/api/learning-paths";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { Button, ProgressBar, ErrorState } from "@/components/ui";

export function LearningPathDetailsScreen({ pathId }: { pathId: string }) {
  const t = useTranslations("learningPaths");
  const query = useLearningPath(pathId);
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
        <ErrorState description={t("errorTitle")} retryLabel={t("errorTitle")} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const path = query.data;

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
            <Link
              href={`/courses/${course.id}`}
              className="mtx-card"
              style={{ display: "flex", alignItems: "center", gap: "var(--space-4)", padding: "var(--space-4)" }}
            >
              <span className="mtx-text-heading-h4" style={{ color: "var(--color-text-secondary)" }}>
                {index + 1}
              </span>
              {course.thumbnailMediaId ? (
                <img
                  src={`/api/v1/media/${course.thumbnailMediaId}/file`}
                  alt=""
                  style={{ width: 96, aspectRatio: "16/9", borderRadius: "var(--radius-medium)", objectFit: "cover" }}
                />
              ) : (
                <div
                  style={{
                    width: 96,
                    aspectRatio: "16/9",
                    borderRadius: "var(--radius-medium)",
                    backgroundColor: "var(--color-surface-variant)",
                  }}
                  aria-hidden="true"
                />
              )}
              <span className="mtx-text-label-large">{course.title}</span>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
