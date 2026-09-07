"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { ApiError } from "@/lib/api/client";
import { EditorQuizQuestion, useEditorQuiz, useReplaceQuiz } from "@/lib/api/quiz";
import { useUnsavedChanges } from "@/lib/hooks/use-unsaved-changes";
import { Button, ErrorState, Icon, ReorderableList, TextField } from "@/components/ui";

interface LocalOption {
  clientId: string;
  optionId?: string;
  text: string;
  isCorrect: boolean;
}

interface LocalQuestion {
  clientId: string;
  questionId?: string;
  prompt: string;
  options: LocalOption[];
}

function clientId() { return crypto.randomUUID(); }

function localQuestions(questions: EditorQuizQuestion[]): LocalQuestion[] {
  return questions.map((question) => ({
    clientId: question.questionId ?? clientId(),
    questionId: question.questionId,
    prompt: question.prompt,
    options: question.options.map((option) => ({ clientId: option.optionId ?? clientId(), ...option })),
  }));
}

function replacementQuestions(questions: LocalQuestion[]): EditorQuizQuestion[] {
  return questions.map((question, index) => ({
    questionId: question.questionId,
    prompt: question.prompt.trim(),
    order: index,
    options: question.options.map(({ optionId, text, isCorrect }) => ({ optionId, text: text.trim(), isCorrect })),
  }));
}

function emptyQuestion(): LocalQuestion {
  return {
    clientId: clientId(), prompt: "", options: [
      { clientId: clientId(), text: "", isCorrect: true },
      { clientId: clientId(), text: "", isCorrect: false },
    ],
  };
}

export function QuizEditorScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("instructor");
  const quizQuery = useEditorQuiz(courseId);
  const replaceQuiz = useReplaceQuiz(courseId);
  const [questions, setQuestions] = useState<LocalQuestion[]>([]);
  const [savedQuestions, setSavedQuestions] = useState<LocalQuestion[]>([]);
  const [validationFields, setValidationFields] = useState<Record<string, string>>();
  const dirty = JSON.stringify(questions) !== JSON.stringify(savedQuestions);
  const guard = useUnsavedChanges(dirty, {
    title: t("unsaved.title"), description: t("unsaved.description"), discard: t("unsaved.discard"), keepEditing: t("unsaved.keepEditing"),
  });
  const returnHref = `/instructor/courses/${courseId}?tab=curriculum`;

  useEffect(() => {
    if (!quizQuery.data) return;
    const authoredQuestions = localQuestions(quizQuery.data.questions);
    setQuestions(authoredQuestions);
    setSavedQuestions(authoredQuestions);
  }, [quizQuery.data]);

  function updateQuestion(index: number, update: Partial<LocalQuestion>) {
    setQuestions((current) => current.map((question, questionIndex) => questionIndex === index ? { ...question, ...update } : question));
    setValidationFields(undefined);
  }

  function updateOption(questionIndex: number, optionIndex: number, update: Partial<LocalOption>) {
    const question = questions[questionIndex];
    if (!question) return;
    updateQuestion(questionIndex, { options: question.options.map((option, currentIndex) => currentIndex === optionIndex ? { ...option, ...update } : option) });
  }

  function markCorrect(questionIndex: number, optionIndex: number) {
    const question = questions[questionIndex];
    if (!question) return;
    updateQuestion(questionIndex, { options: question.options.map((option, currentIndex) => ({ ...option, isCorrect: currentIndex === optionIndex })) });
  }

  function questionError(question: LocalQuestion, index: number): string | undefined {
    const key = `questions[${question.questionId ?? index}]`;
    const code = validationFields?.[key];
    return code ? t(`quiz.validation.${code}`) : undefined;
  }

  function saveQuiz() {
    setValidationFields(undefined);
    replaceQuiz.mutate(replacementQuestions(questions), {
      onSuccess: (saved) => {
        const next = localQuestions(saved.questions);
        setQuestions(next);
        setSavedQuestions(next);
      },
      onError: (error) => {
        if (error instanceof ApiError && error.fields) setValidationFields(error.fields);
        else setValidationFields({ quiz: "SAVE_FAILED" });
      },
    });
  }

  function reorderQuestions(ids: string[]) {
    setQuestions((current) => ids.flatMap((id) => {
      const question = current.find((candidate) => candidate.clientId === id);
      return question ? [question] : [];
    }));
  }

  if (quizQuery.isError) return <div className="mtx-instructor-page"><ErrorState description={t("quiz.loadError")} retryLabel={t("common.retry")} onRetry={() => quizQuery.refetch()} /></div>;
  if (!quizQuery.data) return <div className="mtx-instructor-page"><div className="mtx-skeleton mtx-account-skeleton" /></div>;

  const entries = questions.map((question, questionIndex) => ({
    id: question.clientId,
    content: (
      <section className="mtx-question-editor" aria-labelledby={`question-${question.clientId}`}>
        <div className="mtx-question-heading">
          <h2 id={`question-${question.clientId}`} className="mtx-text-heading-h3">{t("quiz.questionNumber", { number: questionIndex + 1 })}</h2>
          <button type="button" className="mtx-icon-button" aria-label={t("quiz.deleteQuestion")} onClick={() => setQuestions((current) => current.filter((_, index) => index !== questionIndex))}><Icon name="delete" size={20} /></button>
        </div>
        <TextField label={t("quiz.promptLabel")} value={question.prompt} onChange={(event) => updateQuestion(questionIndex, { prompt: event.target.value })} />
        <div className="mtx-instructor-list">
          {question.options.map((option, optionIndex) => (
            <div className="mtx-option-row" key={option.clientId}>
              <label className="mtx-radio-label">
                <input type="radio" name={`correct-${question.clientId}`} checked={option.isCorrect} onChange={() => markCorrect(questionIndex, optionIndex)} />
                <span className="sr-only">{t("quiz.correctOption")}</span>
              </label>
              <TextField label={t("quiz.optionLabel", { number: optionIndex + 1 })} value={option.text} onChange={(event) => updateOption(questionIndex, optionIndex, { text: event.target.value })} />
              <button type="button" className="mtx-icon-button" aria-label={t("quiz.removeOption")} onClick={() => updateQuestion(questionIndex, { options: question.options.filter((_, index) => index !== optionIndex) })}><Icon name="delete" size={20} /></button>
            </div>
          ))}
        </div>
        <Button type="button" variant="text" onClick={() => updateQuestion(questionIndex, { options: [...question.options, { clientId: clientId(), text: "", isCorrect: false }] })}><Icon name="add" size={20} />{t("quiz.addOption")}</Button>
        {questionError(question, questionIndex) && <p className="mtx-editor-error" role="alert">{questionError(question, questionIndex)}</p>}
      </section>
    ),
  }));

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header"><h1 className="mtx-text-heading-h1">{t("quiz.title")}</h1><Button type="button" variant="tonal" onClick={() => setQuestions((current) => [...current, emptyQuestion()])}><Icon name="add" size={20} />{t("quiz.addQuestion")}</Button></div>
      {entries.length > 0 && <ReorderableList entries={entries} onReorder={reorderQuestions} moveUpLabel={t("reorder.moveUp")} moveDownLabel={t("reorder.moveDown")} dragLabel={t("reorder.drag")} />}
      {validationFields?.questions && <p className="mtx-editor-error" role="alert">{t("quiz.validation.REQUIRED")}</p>}
      {validationFields?.quiz && <p className="mtx-editor-error" role="alert">{t("quiz.saveError")}</p>}
      <div className="mtx-editor-actions mt-6">
        <Button type="button" loading={replaceQuiz.isPending} onClick={saveQuiz}>{t("quiz.save")}</Button>
        <Button type="button" variant="text" onClick={() => guard.navigate(returnHref)}>{t("common.cancel")}</Button>
      </div>
      {guard.dialog}
    </div>
  );
}
