import { useQuery } from "@tanstack/react-query";
import { apiFetch, type ApiEnvelope } from "./client";

export interface AiMessageResponse {
  id: string;
  role: "user" | "assistant";
  content: string;
  lessonContextId?: string;
  createdAt: string;
}

export interface AiConversationResponse {
  conversationId: string;
  messages: AiMessageResponse[];
  nextCursor?: string;
}

export class AiTutorStreamError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fields?: Record<string, string>;

  constructor(status: number, code?: string, fields?: Record<string, string>) {
    super("AI Tutor stream request failed.");
    this.name = "AiTutorStreamError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

async function getAiConversation(): Promise<AiConversationResponse> {
  return apiFetch<AiConversationResponse>("/ai-tutor/conversation?limit=100");
}

export function useAiConversation() {
  return useQuery({
    queryKey: ["ai-tutor", "conversation"],
    queryFn: getAiConversation,
  });
}

export async function streamAiMessage(
  content: string,
  onToken: (token: string) => void,
  signal: AbortSignal
): Promise<void> {
  const response = await fetch("/api/v1/ai-tutor/conversation/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Requested-With": "mentora-web",
    },
    body: JSON.stringify({ content }),
    credentials: "same-origin",
    signal,
  });

  if (!response.ok) {
    const envelope = (await response
      .clone()
      .json()
      .catch(() => null)) as ApiEnvelope<never> | null;
    throw new AiTutorStreamError(response.status, envelope?.error?.code, envelope?.error?.fields);
  }

  if (!response.body) {
    throw new AiTutorStreamError(response.status);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const token = decoder.decode(value, { stream: true });
    if (token) onToken(token);
  }

  const finalToken = decoder.decode();
  if (finalToken) onToken(finalToken);
}
