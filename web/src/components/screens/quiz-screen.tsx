"use client";

import { useContext, useEffect, useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { SidebarForceCollapseContext } from "@/components/navigation/app-shell";
import { AnswerOption, Button, ErrorState, QuestionCard } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useQuiz, useSubmitQuizAttempt } from "@/lib/api/quiz";
import { formatCount } from "@/lib/i18n/format";

export function QuizScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("quiz");
  const locale = useLocale();
  const router = useRouter();
  const forceSidebarCollapse = useContext(SidebarForceCollapseContext);
  const quizQuery = useQuiz(courseId);
  const submitAttempt = useSubmitQuizAttempt(courseId);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [incomplete, setIncomplete] = useState(false);

  useEffect(() => {
    forceSidebarCollapse(true);
    return () => forceSidebarCollapse(false);
  }, [forceSidebarCollapse]);

  const questions = useMemo(
    () => quizQuery.data ? [...quizQuery.data.questions].sort((first, second) => first.order - second.order) : [],
    [quizQuery.data]
  );
  const question = questions[currentIndex];

  function submitQuiz() {
    if (questions.some((quizQuestion) => answers[quizQuestion.questionId] === undefined)) {
      setIncomplete(true);
      return;
    }
    submitAttempt.mutate(
      questions.map((quizQuestion) => ({ questionId: quizQuestion.questionId, selectedOptionId: answers[quizQuestion.questionId]! })),
      { onSuccess: () => router.push(`/app/learn/${courseId}/quiz/results`) }
    );
  }

  if (quizQuery.isLoading) {
    return <div className="mtx-quiz-page px-4 py-8"><div className="mtx-skeleton mtx-quiz-skeleton" /></div>;
  }

  if (quizQuery.isError || quizQuery.data === null || !question) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={quizQuery.data === null ? t("notFound") : t("loadError")} retryLabel={t("retry")} onRetry={() => quizQuery.refetch()} />
        <div className="text-center"><Link href={`/app/learn/${courseId}`} className="mtx-btn mtx-btn-text">{t("backToCourse")}</Link></div>
      </div>
    );
  }

  const selectedOptionId = answers[question.questionId];
  const isFinalQuestion = currentIndex === questions.length - 1;
  const questionNumber = formatCount(currentIndex + 1, locale);
  const questionCount = formatCount(questions.length, locale);

  return (
    <div className="mtx-quiz-page flex flex-col gap-5 px-4 py-8 tablet:px-6">
      <h1 className="mtx-text-heading-h2">{t("title")}</h1>
      <QuestionCard
        progressText={t("questionProgress", { current: questionNumber, total: questionCount })}
        progressPercent={((currentIndex + 1) / questions.length) * 100}
        progressLabel={t("progressLabel")}
        prompt={question.prompt}
      >
        {question.options.map((option) => (
          <AnswerOption
            key={option.optionId}
            text={option.text}
            state={selectedOptionId === option.optionId ? "selected" : "default"}
            onSelect={() => {
              setAnswers((current) => ({ ...current, [question.questionId]: option.optionId }));
              setIncomplete(false);
            }}
          />
        ))}
      </QuestionCard>
      {(incomplete || submitAttempt.isError) && (
        <p className="mtx-inline-error mtx-text-caption" role="alert">{incomplete ? t("completeRemaining") : t("submitError")}</p>
      )}
      <div className="flex justify-end">
        <Button
          variant="primary"
          disabled={selectedOptionId === undefined}
          loading={submitAttempt.isPending}
          onClick={isFinalQuestion ? submitQuiz : () => setCurrentIndex((index) => index + 1)}
        >
          {isFinalQuestion ? t("submit") : t("next")}
        </Button>
      </div>
    </div>
  );
}
