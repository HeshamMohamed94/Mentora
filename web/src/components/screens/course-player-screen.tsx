"use client";

import { useContext, useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { SidebarForceCollapseContext } from "@/components/navigation/app-shell";
import { Badge, Button, EmptyState, ErrorState, Icon, ProgressBar, SuccessState, Tabs, VideoPlayer, type TabOption } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useCourse, type LessonResponse, type SectionResponse } from "@/lib/api/courses";
import { usePlaybackUrl } from "@/lib/api/media";
import { useCompleteLesson, useProgress, useUpdatePosition } from "@/lib/api/progress";
import { useQuiz } from "@/lib/api/quiz";
import { formatCount } from "@/lib/i18n/format";

type PlayerTab = "overview" | "resources";

function orderedLessons(sections: SectionResponse[]): LessonResponse[] {
  return [...sections]
    .sort((first, second) => first.order - second.order)
    .flatMap((section) => [...section.lessons].sort((first, second) => first.order - second.order));
}

export function CoursePlayerScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("coursePlayer");
  const locale = useLocale();
  const router = useRouter();
  const forceSidebarCollapse = useContext(SidebarForceCollapseContext);
  const courseQuery = useCourse(courseId);
  const progressQuery = useProgress(courseId);
  const quizQuery = useQuiz(courseId);
  const completeLesson = useCompleteLesson(courseId);
  const updatePosition = useUpdatePosition(courseId);
  const [selectedLessonId, setSelectedLessonId] = useState<string>();
  const [activeTab, setActiveTab] = useState<PlayerTab>("overview");
  const loadedProgress = progressQuery.data;
  const tabs: readonly TabOption<PlayerTab>[] = useMemo(
    () => [{ id: "overview", label: t("overview") }, { id: "resources", label: t("resources") }],
    [t],
  );

  useEffect(() => {
    forceSidebarCollapse(true);
    return () => forceSidebarCollapse(false);
  }, [forceSidebarCollapse]);

  const lessons = useMemo(
    () => courseQuery.data ? orderedLessons(courseQuery.data.sections) : [],
    [courseQuery.data]
  );

  useEffect(() => {
    if (lessons.length === 0 || selectedLessonId || !loadedProgress) return;
    const resumableLesson = lessons.find((lesson) => lesson.lessonId === loadedProgress.currentLessonId);
    setSelectedLessonId(resumableLesson?.lessonId ?? lessons[0]?.lessonId);
  }, [lessons, loadedProgress, selectedLessonId]);

  useEffect(() => {
    setActiveTab("overview");
  }, [selectedLessonId]);

  const selectedLessonIndex = lessons.findIndex((lesson) => lesson.lessonId === selectedLessonId);
  const selectedLesson = lessons.find((lesson) => lesson.lessonId === selectedLessonId);
  const previousLesson = selectedLessonIndex > 0 ? lessons[selectedLessonIndex - 1] : undefined;
  const nextLessonInOrder = selectedLessonIndex >= 0 ? lessons[selectedLessonIndex + 1] : undefined;
  const playbackQuery = usePlaybackUrl(selectedLesson?.videoMediaId ?? undefined, Boolean(selectedLesson?.videoMediaId));

  function markComplete(autoAdvance = false) {
    if (!selectedLesson) return;
    completeLesson.mutate(selectedLesson.lessonId, {
      onSuccess: () => {
        if (!autoAdvance) return;
        const currentIndex = lessons.findIndex((lesson) => lesson.lessonId === selectedLesson.lessonId);
        const nextLesson = lessons[currentIndex + 1];
        if (nextLesson) setSelectedLessonId(nextLesson.lessonId);
      },
    });
  }

  function savePosition(positionSeconds: number) {
    if (!selectedLesson || positionSeconds < 0) return;
    updatePosition.mutate({ lessonId: selectedLesson.lessonId, positionSeconds });
  }

  if (courseQuery.isLoading || progressQuery.isLoading || quizQuery.isLoading) {
    return (
      <div className="mtx-player-page">
        <div className="mtx-skeleton mtx-video-frame" />
        <div className="mtx-skeleton mtx-player-curriculum-skeleton" />
      </div>
    );
  }

  if (courseQuery.isError || progressQuery.isError || quizQuery.isError || !courseQuery.data || !loadedProgress) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={t("loadError")} retryLabel={t("retry")} onRetry={() => {
          courseQuery.refetch();
          progressQuery.refetch();
          quizQuery.refetch();
        }} />
      </div>
    );
  }

  const progress = loadedProgress;
  const completed = new Set(progress.completedLessonIds);
  const courseCompleted = progress.courseCompletedAt !== undefined && (quizQuery.data === null || progress.quizPassed === true);

  if (courseCompleted) {
    return (
      <div className="px-4 py-16">
        <SuccessState
          title={t("completionTitle")}
          description={t("completionDescription")}
          actionLabel={t("viewCertificate")}
          onAction={() => router.push("/app/certificates")}
          secondaryLabel={t("backToLearning")}
          onSecondary={() => router.push("/app/my-learning")}
        />
      </div>
    );
  }

  if (!selectedLesson) {
    return <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />;
  }

  const initialPosition = progress.currentLessonId === selectedLesson.lessonId
    ? progress.currentPositionSeconds
    : undefined;

  return (
    <div className="mtx-player-shell">
      <header className="mtx-player-topbar">
        <Link href="/app/my-learning" className="mtx-player-back" aria-label={t("backToLearning")}>
          <Icon name="arrowBack" size={20} />
        </Link>
        <div className="mtx-player-topbar-meta">
          <p className="mtx-text-label-large mtx-player-topbar-title">{courseQuery.data.title}</p>
          <p className="mtx-text-caption" style={{ color: "var(--color-text-secondary)" }}>
            {t("lessonOf", { current: formatCount(selectedLessonIndex + 1, locale), total: formatCount(lessons.length, locale) })}
            {" · "}
            {t("percentComplete", { percent: formatCount(progress.completionPercent, locale) })}
          </p>
        </div>
        <Link href="/app/ai-tutor" className="mtx-btn mtx-btn-tonal">
          <Icon name="aiTutor" size={20} />
          {t("askTutor")}
        </Link>
      </header>

      <div className="mtx-player-page">
        <section className="mtx-player-content">
          {selectedLesson.videoMediaId && playbackQuery.isLoading && <div className="mtx-skeleton mtx-video-frame" />}
          {selectedLesson.videoMediaId && playbackQuery.isError && (
            <div className="mtx-video-frame mtx-video-error">
              <Icon name="cancel" />
              <p className="mtx-text-heading-h4">{t("videoUrlError")}</p>
              <Button variant="tonal" onClick={() => playbackQuery.refetch()}>{t("retry")}</Button>
            </div>
          )}
          {selectedLesson.videoMediaId && playbackQuery.data && (
            <VideoPlayer
              key={selectedLesson.lessonId}
              src={playbackQuery.data.url}
              title={selectedLesson.title}
              initialPositionSeconds={initialPosition}
              onPositionChange={savePosition}
              onEnded={() => markComplete(true)}
              labels={{
                play: t("video.play"), pause: t("video.pause"), mute: t("video.mute"),
                unmute: t("video.unmute"), volume: t("video.volume"), fullscreen: t("video.fullscreen"),
                exitFullscreen: t("video.exitFullscreen"), speed: t("video.speed"), loading: t("video.loading"),
                speedValue: (playbackSpeed) => t("video.speedValue", { speed: playbackSpeed }),
                errorTitle: t("video.errorTitle"), retry: t("retry"),
              }}
            />
          )}

          <article className="mtx-lesson-content">
            <h2 className="mtx-text-heading-h2">{selectedLesson.title}</h2>

            <Tabs options={tabs} active={activeTab} onChange={setActiveTab} label={selectedLesson.title} />

            {activeTab === "overview" ? (
              <p className="mtx-text-body-medium">{selectedLesson.description}</p>
            ) : selectedLesson.resources.length > 0 ? (
              <ul className="flex flex-col gap-1">
                {selectedLesson.resources.map((resource) => (
                  <li key={`${resource.label}-${resource.url}`}>
                    <a className="mtx-resource-link mtx-text-body-small" href={resource.url} target="_blank" rel="noreferrer">{resource.label}</a>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>{t("noResources")}</p>
            )}

            {completeLesson.isError && <p className="mtx-inline-error mtx-text-caption">{t("completeError")}</p>}

            <div className="mtx-player-nav-row">
              <Button variant="tonal" disabled={!previousLesson} onClick={() => previousLesson && setSelectedLessonId(previousLesson.lessonId)}>
                <Icon name="arrowBack" size={20} className="mtx-icon-mirror-rtl" />
                {t("previousLesson")}
              </Button>
              <Button variant="primary" loading={completeLesson.isPending} disabled={completed.has(selectedLesson.lessonId)} onClick={() => markComplete()}>
                {completed.has(selectedLesson.lessonId) ? t("completed") : t("markComplete")}
              </Button>
              <Button variant="tonal" disabled={!nextLessonInOrder} onClick={() => nextLessonInOrder && setSelectedLessonId(nextLessonInOrder.lessonId)}>
                {t("nextLesson")}
                <Icon name="arrowForward" size={20} className="mtx-icon-mirror-rtl" />
              </Button>
            </div>
            {progress.completionPercent >= 100 && quizQuery.data !== null && progress.quizPassed !== true && (
              <Link href={`/app/learn/${courseId}/quiz`} className="mtx-btn mtx-btn-primary self-start">{t("takeQuiz")}</Link>
            )}
          </article>
        </section>

        <aside className="mtx-player-curriculum" aria-label={t("curriculum")}>
          <div className="flex flex-col gap-2">
            <h2 className="mtx-text-label-large mtx-player-curriculum-eyebrow">{t("yourProgress")}</h2>
            <ProgressBar percent={progress.completionPercent} label={t("courseProgress")} />
          </div>
          <div className="flex flex-col gap-4">
            {[...courseQuery.data.sections].sort((first, second) => first.order - second.order).map((section) => (
              <section key={section.sectionId} className="flex flex-col gap-2">
                <h3 className="mtx-text-label-large">{section.title}</h3>
                <div className="flex flex-col gap-1">
                  {[...section.lessons].sort((first, second) => first.order - second.order).map((lesson) => (
                    <button
                      type="button"
                      key={lesson.lessonId}
                      className="mtx-lesson-row"
                      data-selected={lesson.lessonId === selectedLesson.lessonId || undefined}
                      onClick={() => setSelectedLessonId(lesson.lessonId)}
                    >
                      <span className="mtx-lesson-title mtx-text-body-small">{lesson.title}</span>
                      {completed.has(lesson.lessonId) && (
                        <Badge variant="success"><Icon name="checkCircle" size={16} /> {t("completed")}</Badge>
                      )}
                    </button>
                  ))}
                </div>
              </section>
            ))}
          </div>
        </aside>
      </div>
    </div>
  );
}
