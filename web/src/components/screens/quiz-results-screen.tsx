"use client";

import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { AnswerOption, Badge, Button, EmptyState, ErrorState, QuestionCard, StatCard, SuccessState } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useProgress } from "@/lib/api/progress";
import { useLatestAttempt, useQuiz } from "@/lib/api/quiz";
import { formatCount } from "@/lib/i18n/format";

export function QuizResultsScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("quizResults");
  const locale = useLocale();
  const router = useRouter();
  const attemptQuery = useLatestAttempt(courseId);
  const quizQuery = useQuiz(courseId);
  const progressQuery = useProgress(courseId);
  const [showCompletion, setShowCompletion] = useState(false);

  const questionsById = useMemo(
    () => new Map(quizQuery.data?.questions.map((question) => [question.questionId, question]) ?? []),
    [quizQuery.data]
  );

  if (attemptQuery.isLoading || attemptQuery.isFetching || quizQuery.isLoading || progressQuery.isLoading || progressQuery.isFetching) {
    return <div className="mtx-quiz-page px-4 py-8"><div className="mtx-skeleton mtx-results-skeleton" /></div>;
  }

  if (attemptQuery.isError || quizQuery.isError || progressQuery.isError || quizQuery.data === null) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={t("loadError")} retryLabel={t("retry")} onRetry={() => {
          attemptQuery.refetch();
          quizQuery.refetch();
          progressQuery.refetch();
        }} />
      </div>
    );
  }

  if (!attemptQuery.data) {
    return (
      <div className="px-4 py-8">
        <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} actionLabel={t("takeQuiz")} onAction={() => router.push(`/app/learn/${courseId}/quiz`)} />
      </div>
    );
  }

  if (showCompletion && progressQuery.data?.courseCompletedAt !== undefined) {
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

  const attempt = attemptQuery.data;
  const breakdownCount = attempt.breakdown.length;

  return (
    <div className="mtx-quiz-page flex flex-col gap-6 px-4 py-8 tablet:px-6">
      <h1 className="mtx-text-heading-h2">{t("title")}</h1>
      <section className="mtx-result-score">
        <StatCard value={t("scoreValue", { score: formatCount(attempt.score, locale) })} label={t("score")} />
        <Badge variant={attempt.passed ? "success" : "error"}>{attempt.passed ? t("passed") : t("failed")}</Badge>
      </section>

      <div className="flex flex-col gap-4">
        <h2 className="mtx-text-heading-h3">{t("breakdown")}</h2>
        {attempt.breakdown.map((breakdown, index) => {
          const question = questionsById.get(breakdown.questionId);
          if (!question) return null;
          return (
            <QuestionCard
              key={breakdown.questionId}
              progressText={t("questionProgress", { current: formatCount(index + 1, locale), total: formatCount(breakdownCount, locale) })}
              progressPercent={((index + 1) / breakdownCount) * 100}
              progressLabel={t("breakdownProgress")}
              prompt={question.prompt}
            >
              {question.options.map((option) => {
                const isCorrect = option.optionId === breakdown.correctOptionId;
                const isIncorrectSelection = option.optionId === breakdown.selectedOptionId && !breakdown.isCorrect;
                const state = isCorrect ? "correct" : isIncorrectSelection ? "incorrect" : "disabled";
                return (
                  <AnswerOption
                    key={option.optionId}
                    text={option.text}
                    state={state}
                    stateLabel={isCorrect ? t("correct") : isIncorrectSelection ? t("incorrect") : undefined}
                  />
                );
              })}
              <span className="self-start">
                <Badge variant={breakdown.isCorrect ? "success" : "error"}>{breakdown.isCorrect ? t("correct") : t("incorrect")}</Badge>
              </span>
            </QuestionCard>
          );
        })}
      </div>

      <div>
        {attempt.passed ? (
          <Button variant="primary" onClick={() => {
            if (progressQuery.data?.courseCompletedAt !== undefined) setShowCompletion(true);
            else router.push(`/app/learn/${courseId}`);
          }}>{t("continue")}</Button>
        ) : (
          <Link href={`/app/learn/${courseId}/quiz`} className="mtx-btn mtx-btn-tonal">{t("retryQuiz")}</Link>
        )}
      </div>
    </div>
  );
}
