import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, apiFetch } from "./client";

export interface StudentQuizOption {
  optionId: string;
  text: string;
}

export interface StudentQuizQuestion {
  questionId: string;
  prompt: string;
  order: number;
  options: StudentQuizOption[];
}

export interface StudentQuizResponse {
  courseId: string;
  questions: StudentQuizQuestion[];
}

export interface AttemptAnswerRequest {
  questionId: string;
  selectedOptionId: string;
}

export interface AttemptBreakdown {
  questionId: string;
  selectedOptionId?: string;
  correctOptionId: string;
  isCorrect: boolean;
}

export interface AttemptResponse {
  score: number;
  passed: boolean;
  breakdown: AttemptBreakdown[];
}

async function getQuiz(courseId: string): Promise<StudentQuizResponse | null> {
  try {
    return await apiFetch<StudentQuizResponse>(`/courses/${courseId}/quiz`);
  } catch (error) {
    if (error instanceof ApiError && error.code === "QUIZ_NOT_FOUND") return null;
    throw error;
  }
}

async function getLatestAttempt(courseId: string): Promise<AttemptResponse | null> {
  try {
    return await apiFetch<AttemptResponse>(`/courses/${courseId}/quiz/attempts/latest`);
  } catch (error) {
    if (error instanceof ApiError && error.code === "ATTEMPT_NOT_FOUND") return null;
    throw error;
  }
}

export function useQuiz(courseId: string) {
  return useQuery({
    queryKey: ["quiz", courseId],
    queryFn: () => getQuiz(courseId),
    enabled: Boolean(courseId),
  });
}

export function useSubmitQuizAttempt(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (answers: AttemptAnswerRequest[]) =>
      apiFetch<AttemptResponse>(`/courses/${courseId}/quiz/attempts`, {
        method: "POST",
        body: JSON.stringify({ answers }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["quiz-attempt", courseId] });
      queryClient.invalidateQueries({ queryKey: ["progress", courseId] });
    },
  });
}

export function useLatestAttempt(courseId: string) {
  return useQuery({
    queryKey: ["quiz-attempt", courseId],
    queryFn: () => getLatestAttempt(courseId),
    enabled: Boolean(courseId),
  });
}
