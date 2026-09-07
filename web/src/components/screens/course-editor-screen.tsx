"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/client";
import {
  CourseResponse,
  CourseWriteRequest,
  SectionResponse,
  updateCourse,
  useAddSection,
  useCourse,
  useCreateCourse,
  useDeleteSection,
  usePublishCourse,
  useReorderLessons,
  useReorderSections,
  useUnpublishCourse,
  useUpdateCourse,
} from "@/lib/api/courses";
import { useCategories } from "@/lib/api/categories";
import { uploadMedia } from "@/lib/api/media";
import { useUnsavedChanges } from "@/lib/hooks/use-unsaved-changes";
import { Badge, Button, CourseThumbnail, ErrorState, FileUpload, Icon, ReorderableList, Select, Tabs, TextField, Toggle, type SelectOption, type TabOption } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";

type EditorTab = "overview" | "curriculum";
type Level = CourseWriteRequest["level"];
type ContentLanguage = CourseWriteRequest["contentLanguage"];

interface OverviewForm {
  title: string;
  description: string;
  categoryId: string;
  level: Level;
  contentLanguage: ContentLanguage;
  amount: string;
  currency: string;
}

const EMPTY_FORM: OverviewForm = {
  title: "", description: "", categoryId: "", level: "beginner", contentLanguage: "en", amount: "", currency: "USD",
};

function formFromCourse(course: CourseResponse): OverviewForm {
  return {
    title: course.title,
    description: course.description,
    categoryId: course.categoryId,
    level: course.level,
    contentLanguage: course.contentLanguage as ContentLanguage,
    amount: String(course.priceDisplay.amount),
    currency: course.priceDisplay.currency,
  };
}

function writeRequest(form: OverviewForm): CourseWriteRequest {
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    categoryId: form.categoryId,
    level: form.level,
    contentLanguage: form.contentLanguage,
    priceDisplay: { amount: Number(form.amount), currency: form.currency.trim().toUpperCase() },
  };
}

function guardLabels(t: ReturnType<typeof useTranslations>) {
  return {
    title: t("unsaved.title"), description: t("unsaved.description"), discard: t("unsaved.discard"), keepEditing: t("unsaved.keepEditing"),
  };
}

function readinessItems(course: CourseResponse, fields?: Record<string, string>) {
  const missing = new Set(Object.keys(fields ?? {}));
  const entries = [
    { key: "title", ready: Boolean(course.title.trim()) },
    { key: "description", ready: Boolean(course.description.trim()) },
    { key: "categoryId", ready: Boolean(course.categoryId) },
    { key: "priceDisplay", ready: Boolean(course.priceDisplay) },
    { key: "thumbnail", ready: Boolean(course.thumbnailMediaId) },
    { key: "curriculum", ready: course.sections.length > 0 },
  ];
  course.sections.forEach((section) => entries.push({ key: `section.${section.sectionId}.lessons`, ready: section.lessons.length > 0 }));
  course.sections.flatMap((section) => section.lessons).forEach((lesson) => entries.push({ key: `lesson.${lesson.lessonId}.videoMediaId`, ready: Boolean(lesson.videoMediaId) }));
  return entries.map((entry) => ({ ...entry, ready: entry.ready && !missing.has(entry.key) }));
}

function readinessText(course: CourseResponse, key: string, t: ReturnType<typeof useTranslations>): string {
  if (key.startsWith("section.")) {
    const sectionId = key.split(".")[1];
    const section = course.sections.find((candidate) => candidate.sectionId === sectionId);
    return t("overview.requirements.sectionLessons", { title: section?.title ?? "" });
  }
  if (key.startsWith("lesson.")) {
    const lessonId = key.split(".")[1];
    const lesson = course.sections.flatMap((section) => section.lessons).find((candidate) => candidate.lessonId === lessonId);
    return t("overview.requirements.lessonVideo", { title: lesson?.title ?? "" });
  }
  return t(`overview.requirements.${key}`);
}

function OverviewEditor({ course, tabs, onTabChange }: { course?: CourseResponse; tabs: readonly TabOption<EditorTab>[]; onTabChange: (tab: EditorTab) => void }) {
  const t = useTranslations("instructor");
  const router = useRouter();
  const queryClient = useQueryClient();
  const categories = useCategories();
  const createCourse = useCreateCourse();
  const updateCourseMutation = useUpdateCourse(course?.id ?? "");
  const publishCourse = usePublishCourse(course?.id ?? "");
  const unpublishCourse = useUnpublishCourse(course?.id ?? "");
  const [form, setForm] = useState<OverviewForm>(() => course ? formFromCourse(course) : EMPTY_FORM);
  const [savedForm, setSavedForm] = useState<OverviewForm>(() => course ? formFromCourse(course) : EMPTY_FORM);
  const [thumbnail, setThumbnail] = useState<File>();
  const [fileError, setFileError] = useState<string>();
  const [formError, setFormError] = useState<string>();
  const [publishFields, setPublishFields] = useState<Record<string, string>>();
  const [uploading, setUploading] = useState(false);
  const dirty = JSON.stringify(form) !== JSON.stringify(savedForm) || Boolean(thumbnail);
  const guard = useUnsavedChanges(dirty, guardLabels(t));
  const saving = createCourse.isPending || updateCourseMutation.isPending || uploading;

  useEffect(() => {
    if (!course) return;
    const nextForm = formFromCourse(course);
    setForm(nextForm);
    setSavedForm(nextForm);
  }, [course]);

  const categoryOptions = (categories.data ?? []).map((category) => ({ value: category.id, label: category.name }));
  const levelOptions: readonly SelectOption<Level>[] = ["beginner", "intermediate", "advanced"].map((level) => ({ value: level as Level, label: t(`overview.levels.${level}`) }));
  const languageOptions: readonly SelectOption<ContentLanguage>[] = [{ value: "en", label: t("overview.languages.en") }, { value: "ar", label: t("overview.languages.ar") }];

  function setField<K extends keyof OverviewForm>(field: K, value: OverviewForm[K]) {
    setForm((current) => ({ ...current, [field]: value }));
    setFormError(undefined);
  }

  function validate(): boolean {
    if (!form.title.trim() || !form.description.trim() || !form.categoryId || !form.amount) {
      setFormError(t("overview.requiredError"));
      return false;
    }
    if (!Number.isFinite(Number(form.amount)) || Number(form.amount) < 0 || !/^[A-Z]{3}$/.test(form.currency)) {
      setFormError(t("overview.priceError"));
      return false;
    }
    return true;
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validate()) return;
    setFormError(undefined);
    let savedCourse: CourseResponse;
    try {
      savedCourse = course
        ? await updateCourseMutation.mutateAsync(writeRequest(form))
        : await createCourse.mutateAsync(writeRequest(form));
    } catch {
      setFormError(t("overview.saveError"));
      return;
    }
    if (thumbnail) {
      setUploading(true);
      try {
        const uploaded = await uploadMedia({ kind: "courseThumbnail", ownerRefId: savedCourse.id, contentType: thumbnail.type, file: thumbnail });
        await updateCourse(savedCourse.id, { thumbnailMediaId: uploaded.mediaId });
        await queryClient.invalidateQueries({ queryKey: ["courses", savedCourse.id] });
      } catch {
        setFileError(t("upload.uploadError"));
        if (!course) router.replace(`/instructor/courses/${savedCourse.id}`);
        return;
      } finally {
        setUploading(false);
      }
    }
    setThumbnail(undefined);
    setSavedForm(form);
    if (!course) router.replace(`/instructor/courses/${savedCourse.id}`);
  }

  function selectThumbnail(file: File) {
    const allowed = ["image/jpeg", "image/png", "image/webp"].includes(file.type) && file.size <= 5 * 1024 * 1024;
    if (!allowed) { setFileError(t("overview.thumbnailError")); return; }
    setFileError(undefined);
    setThumbnail(file);
  }

  function togglePublished() {
    if (!course) return;
    setPublishFields(undefined);
    const mutation = course.status === "published" ? unpublishCourse : publishCourse;
    mutation.mutate(undefined, {
      onError: (error) => {
        if (error instanceof ApiError && error.fields) setPublishFields(error.fields);
        else setFormError(t("overview.publishError"));
      },
    });
  }

  return (
    <>
      {course && <Tabs options={tabs} active="overview" label={t("tabs.label")} onChange={(tab) => guard.requestAction(() => onTabChange(tab))} />}
      <form className="mtx-editor-panel" onSubmit={save}>
        <div className="mtx-editor-actions mtx-editor-actions-top">
          <Button type="submit" loading={saving}>{t("overview.save")}</Button>
          {course && <Toggle checked={course.status === "published"} label={t(`status.${course.status}`)} disabled={publishCourse.isPending || unpublishCourse.isPending || dirty} onChange={togglePublished} />}
        </div>
        {formError && <p className="mtx-editor-error" role="alert">{formError}</p>}
        <TextField label={t("overview.titleLabel")} value={form.title} onChange={(event) => setField("title", event.target.value)} disabled={saving} />
        <TextField label={t("overview.descriptionLabel")} value={form.description} onChange={(event) => setField("description", event.target.value)} disabled={saving} />
        <div className="mtx-editor-grid">
          <Select label={t("overview.categoryLabel")} value={form.categoryId || undefined} options={categoryOptions} placeholder={t("overview.categoryPlaceholder")} error={categories.isError ? t("overview.categoryError") : undefined} disabled={saving || categories.isLoading} onChange={(value) => setField("categoryId", value)} />
          <Select label={t("overview.levelLabel")} value={form.level} options={levelOptions} disabled={saving} onChange={(value) => setField("level", value)} />
          <Select label={t("overview.languageLabel")} value={form.contentLanguage} options={languageOptions} disabled={saving} onChange={(value) => setField("contentLanguage", value)} />
          <TextField label={t("overview.amountLabel")} type="number" min="0" step="0.01" value={form.amount} onChange={(event) => setField("amount", event.target.value)} disabled={saving} />
          <TextField label={t("overview.currencyLabel")} maxLength={3} value={form.currency} onChange={(event) => setField("currency", event.target.value.toUpperCase())} disabled={saving} />
        </div>
        <FileUpload
          label={t("overview.thumbnailLabel")}
          instruction={t("upload.imageInstruction")}
          browseLabel={t("upload.browse")}
          retryLabel={t("common.retry")}
          removeLabel={t("upload.remove")}
          accept="image/jpeg,image/png,image/webp"
          file={thumbnail}
          existingName={course?.thumbnailMediaId ? t("upload.attachedThumbnail") : undefined}
          uploading={uploading}
          success={Boolean(course?.thumbnailMediaId) && !thumbnail}
          error={fileError}
          disabled={saving}
          onFile={selectThumbnail}
          onRemove={() => { setThumbnail(undefined); setFileError(undefined); }}
          preview={
            course?.thumbnailMediaId && !thumbnail ? (
              <CourseThumbnail
                mediaId={course.thumbnailMediaId}
                className="mtx-editor-thumbnail-preview"
                seed={course.id}
                categoryId={course.categoryId}
              />
            ) : undefined
          }
        />
        {course && (
          <section aria-labelledby="readiness-heading">
            <h2 id="readiness-heading" className="mtx-text-heading-h3">{t("overview.readiness")}</h2>
            <ul className="mtx-readiness-list">
              {readinessItems(course, publishFields).map((entry) => <li key={entry.key}>{entry.ready ? t("overview.ready") : t("overview.missing")} — {readinessText(course, entry.key, t)}</li>)}
            </ul>
          </section>
        )}
      </form>
      {guard.dialog}
    </>
  );
}

function CurriculumSection({ courseId, section, deleting, onDelete }: { courseId: string; section: SectionResponse; deleting: boolean; onDelete: () => void }) {
  const t = useTranslations("instructor");
  const reorderLessons = useReorderLessons(courseId, section.sectionId);
  const lessonEntries = section.lessons.map((lesson) => ({
    id: lesson.lessonId,
    content: <Link href={`/instructor/courses/${courseId}/sections/${section.sectionId}/lessons/${lesson.lessonId}`} className="mtx-btn mtx-btn-text">{lesson.title} <Badge variant={lesson.videoMediaId ? "success" : "warning"}>{lesson.videoMediaId ? t("curriculum.videoAttached") : t("curriculum.videoMissing")}</Badge></Link>,
  }));
  return (
    <details className="mtx-section-block" open>
      <summary className="mtx-section-summary">
        <span>{section.title}</span>
      </summary>
      <div className="mtx-editor-actions"><Button type="button" variant="text" disabled={deleting} onClick={onDelete}><Icon name="delete" size={20} />{t("curriculum.deleteSection")}</Button></div>
      {lessonEntries.length > 0 && <ReorderableList entries={lessonEntries} disabled={reorderLessons.isPending} onReorder={(lessonIds) => reorderLessons.mutate(lessonIds)} moveUpLabel={t("reorder.moveUp")} moveDownLabel={t("reorder.moveDown")} dragLabel={t("reorder.drag")} />}
      {reorderLessons.isError && <p className="mtx-editor-error" role="alert">{t("curriculum.actionError")}</p>}
      <Link href={`/instructor/courses/${courseId}/sections/${section.sectionId}/lessons/new`} className="mtx-btn mtx-btn-tonal"><Icon name="add" size={20} />{t("curriculum.addLesson")}</Link>
    </details>
  );
}

function CurriculumEditor({ course, tabs, onTabChange }: { course: CourseResponse; tabs: readonly TabOption<EditorTab>[]; onTabChange: (tab: EditorTab) => void }) {
  const t = useTranslations("instructor");
  const addSection = useAddSection(course.id);
  const deleteSection = useDeleteSection(course.id);
  const reorderSections = useReorderSections(course.id);
  const [newTitle, setNewTitle] = useState("");
  const guard = useUnsavedChanges(Boolean(newTitle), guardLabels(t));
  const entries = course.sections.map((section) => ({
    id: section.sectionId,
    content: <CurriculumSection courseId={course.id} section={section} deleting={deleteSection.isPending} onDelete={() => deleteSection.mutate(section.sectionId)} />,
  }));
  return (
    <>
    <Tabs options={tabs} active="curriculum" label={t("tabs.label")} onChange={(tab) => guard.requestAction(() => onTabChange(tab))} />
    <div className="mtx-editor-panel">
      <div className="mtx-editor-actions">
        <TextField label={t("curriculum.newSectionTitle")} value={newTitle} disabled={addSection.isPending} onChange={(event) => setNewTitle(event.target.value)} />
        <Button type="button" variant="tonal" loading={addSection.isPending} disabled={!newTitle.trim()} onClick={() => addSection.mutate(newTitle.trim(), { onSuccess: () => setNewTitle("") })}><Icon name="add" size={20} />{t("curriculum.addSection")}</Button>
        <Link href={`/instructor/courses/${course.id}/quiz`} className="mtx-btn mtx-btn-tonal"><Icon name="add" size={20} />{t("curriculum.addQuiz")}</Link>
      </div>
      {entries.length > 0 && <ReorderableList entries={entries} disabled={reorderSections.isPending} onReorder={(sectionIds) => reorderSections.mutate(sectionIds)} moveUpLabel={t("reorder.moveUp")} moveDownLabel={t("reorder.moveDown")} dragLabel={t("reorder.drag")} />}
      {(addSection.isError || deleteSection.isError || reorderSections.isError) && <p className="mtx-editor-error" role="alert">{t("curriculum.actionError")}</p>}
    </div>
    {guard.dialog}
    </>
  );
}

export function CourseEditorScreen({ courseId, initialTab = "overview" }: { courseId?: string; initialTab?: EditorTab }) {
  const t = useTranslations("instructor");
  const courseQuery = useCourse(courseId ?? "");
  const [activeTab, setActiveTab] = useState<EditorTab>(initialTab);
  const course = courseQuery.data;
  const tabs = useMemo(() => [{ id: "overview" as const, label: t("tabs.overview") }, { id: "curriculum" as const, label: t("tabs.curriculum") }], [t]);

  if (courseId && courseQuery.isError) return <div className="mtx-instructor-page"><ErrorState description={t("overview.loadError")} retryLabel={t("common.retry")} onRetry={() => courseQuery.refetch()} /></div>;
  if (courseId && !course) return <div className="mtx-instructor-page"><div className="mtx-skeleton mtx-account-skeleton" /></div>;

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header"><h1 className="mtx-text-heading-h1">{course ? course.title : t("overview.createTitle")}</h1>{course && <Badge variant={course.status === "published" ? "success" : "warning"}>{t(`status.${course.status}`)}</Badge>}</div>
      {activeTab === "overview" ? <OverviewEditor course={course} tabs={tabs} onTabChange={setActiveTab} /> : course && <CurriculumEditor course={course} tabs={tabs} onTabChange={setActiveTab} />}
    </div>
  );
}
