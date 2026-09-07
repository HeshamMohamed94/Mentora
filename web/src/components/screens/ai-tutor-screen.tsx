"use client";

import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { AiTutorBubble, AiTutorQuickAction, Button, ErrorState, Icon } from "@/components/ui";
import {
  AiTutorStreamError,
  streamAiMessage,
  useAiConversation,
  type AiMessageResponse,
} from "@/lib/api/ai-tutor";

const MAX_MESSAGE_LENGTH = 4000;
const QUICK_ACTION_KEYS = ["explainLesson", "summarize", "example", "quizMe", "learnNext"] as const;

type MessageState = "complete" | "streaming" | "error";

interface ChatMessage {
  id: string;
  role: AiMessageResponse["role"];
  content: string;
  state: MessageState;
  retryContent?: string;
}

function createLocalId(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

function appendReplyToken(messages: ChatMessage[] | null, assistantId: string, token: string) {
  return messages?.map((message) =>
    message.id === assistantId ? { ...message, content: message.content + token } : message
  ) ?? null;
}

function completeReply(messages: ChatMessage[] | null, assistantId: string) {
  return messages?.map((message) =>
    message.id === assistantId ? { ...message, state: "complete" as const } : message
  ) ?? null;
}

function recordReplyFailure(messages: ChatMessage[] | null, assistantId: string, failure: ChatMessage) {
  if (!messages) return [failure];
  const partialReply = messages.find((message) => message.id === assistantId)?.content;
  if (partialReply) return [...completeReply(messages, assistantId)!, failure];
  return messages.map((message) => (message.id === assistantId ? failure : message));
}

export function AiTutorScreen() {
  const t = useTranslations("aiTutor");
  const conversationQuery = useAiConversation();
  const [messages, setMessages] = useState<ChatMessage[] | null>(null);
  const [draft, setDraft] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const threadEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!conversationQuery.data || messages !== null) return;

    const history = conversationQuery.data.messages.map<ChatMessage>((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      state: "complete",
    }));
    setMessages(
      history.length > 0
        ? history
        : [{ id: "welcome", role: "assistant", content: t("welcome"), state: "complete" }]
    );
  }, [conversationQuery.data, messages, t]);

  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ block: "end" });
  }, [messages]);

  useEffect(() => () => {
    const activeController = abortControllerRef.current;
    abortControllerRef.current = null;
    activeController?.abort();
  }, []);

  function errorMessage(error: unknown): string {
    if (error instanceof AiTutorStreamError) {
      if (error.status === 429) return t("rateLimitError");
      if (error.status === 401) return t("sessionExpiredError");
      if (error.status === 400 && (error.code || error.fields?.content)) return t("messageInvalidError");
    }
    return t("sendError");
  }

  async function receiveReply(content: string, assistantId: string) {
    const controller = new AbortController();
    abortControllerRef.current = controller;
    setIsStreaming(true);

    try {
      await streamAiMessage(
        content,
        (token) => setMessages((current) => appendReplyToken(current, assistantId, token)),
        controller.signal
      );
      setMessages((current) => completeReply(current, assistantId));
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;

      const failure: ChatMessage = {
        id: createLocalId("error"),
        role: "assistant",
        content: errorMessage(error),
        state: "error",
        retryContent: content,
      };
      setMessages((current) => recordReplyFailure(current, assistantId, failure));
    } finally {
      if (abortControllerRef.current === controller) {
        abortControllerRef.current = null;
        setIsStreaming(false);
      }
    }
  }

  function sendMessage(content: string) {
    const normalizedContent = content.trim();
    if (!normalizedContent || normalizedContent.length > MAX_MESSAGE_LENGTH || abortControllerRef.current) return;

    const assistantId = createLocalId("assistant");
    setDraft("");
    setMessages((current) => [
      ...(current ?? []),
      { id: createLocalId("user"), role: "user", content: normalizedContent, state: "complete" },
      { id: assistantId, role: "assistant", content: "", state: "streaming" },
    ]);
    void receiveReply(normalizedContent, assistantId);
  }

  function submitDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void sendMessage(draft);
  }

  function handleDraftKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  function retryMessage(message: ChatMessage) {
    if (!message.retryContent || abortControllerRef.current) return;
    const assistantId = createLocalId("assistant");
    setMessages((current) => [
      ...(current?.filter((chatMessage) => chatMessage.id !== message.id) ?? []),
      { id: assistantId, role: "assistant", content: "", state: "streaming" },
    ]);
    void receiveReply(message.retryContent, assistantId);
  }

  if (messages === null && conversationQuery.isError) {
    return (
      <div className="mtx-ai-tutor-page mtx-ai-tutor-load-error">
        <ErrorState
          description={t("loadError")}
          retryLabel={t("retry")}
          onRetry={() => conversationQuery.refetch()}
        />
      </div>
    );
  }

  if (messages === null) {
    return (
      <div className="mtx-ai-tutor-page" role="status" aria-label={t("loading")}>
        <div className="mtx-ai-tutor-loading" aria-hidden="true">
          <div className="mtx-skeleton mtx-ai-tutor-loading-title" />
          <div className="mtx-skeleton mtx-ai-tutor-loading-bubble" />
        </div>
      </div>
    );
  }

  const latestMessage = messages.at(-1);

  return (
    <div className="mtx-ai-tutor-page">
      <section className="mtx-ai-tutor-panel" aria-labelledby="ai-tutor-title">
        <div className="mtx-ai-tutor-title">
          <div className="mtx-ai-tutor-identity">
            <span className="mtx-ai-tutor-avatar" aria-hidden="true">
              <Icon name="aiTutor" size={20} />
            </span>
            <h1 id="ai-tutor-title" className="mtx-text-heading-h2">
              {t("title")}
            </h1>
          </div>
        </div>

        <div className="mtx-ai-tutor-thread" role="log" aria-live="polite" aria-relevant="additions">
          {messages.map((message) => (
            <AiTutorBubble
              key={message.id}
              variant={message.role === "assistant" ? "ai" : "user"}
              content={message.content}
              label={message.role === "assistant" ? t("assistantLabel") : t("userLabel")}
              streaming={message.state === "streaming"}
              thinkingLabel={t("thinking")}
              error={message.state === "error"}
              retryLabel={message.state === "error" ? t("retry") : undefined}
              retryDisabled={isStreaming}
              onRetry={message.state === "error" ? () => retryMessage(message) : undefined}
            />
          ))}
          <div ref={threadEndRef} />
        </div>
        <p className="sr-only" aria-live="polite" aria-atomic="true">
          {latestMessage?.role === "assistant" && latestMessage.state !== "streaming" ? latestMessage.content : ""}
        </p>

        <div className="mtx-ai-tutor-composer">
          <div className="mtx-ai-tutor-quick-actions" aria-label={t("quickActionsLabel")}>
            {QUICK_ACTION_KEYS.map((key) => (
              <AiTutorQuickAction key={key} disabled={isStreaming} onClick={() => sendMessage(t(`quickActions.${key}`))}>
                {t(`quickActions.${key}`)}
              </AiTutorQuickAction>
            ))}
          </div>
          <form className="mtx-ai-tutor-input-row" onSubmit={submitDraft}>
            <label htmlFor="ai-tutor-message" className="sr-only">
              {t("inputLabel")}
            </label>
            <textarea
              id="ai-tutor-message"
              className="mtx-ai-tutor-input"
              value={draft}
              maxLength={MAX_MESSAGE_LENGTH}
              rows={1}
              placeholder={t("inputPlaceholder")}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleDraftKeyDown}
            />
            <Button
              type="submit"
              variant="primary"
              className="mtx-ai-tutor-send"
              disabled={isStreaming || !draft.trim()}
              aria-label={t("send")}
            >
              <Icon name="arrowForward" size={20} className="mtx-icon-mirror-rtl" />
            </Button>
          </form>
        </div>
      </section>
    </div>
  );
}
