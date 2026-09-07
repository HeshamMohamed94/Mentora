"use client";

import { FormEvent, useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { ResourceLink, updateLesson, useAddLesson, useCourse, useDeleteLesson, useUpdateLesson } from "@/lib/api/courses";
import { uploadMedia } from "@/lib/api/media";
import { useUnsavedChanges } from "@/lib/hooks/use-unsaved-changes";
import { AppDialog, Button, ErrorState, FileUpload, Icon, TextField } from "@/components/ui";
import { useRouter } from "@/i18n/navigation";

interface LessonForm {
  title: string;
  description: string;
  resources: ResourceLink[];
}

const EMPTY_LESSON: LessonForm = { title: "", description: "", resources: [] };

export function LessonEditorScreen({ courseId, sectionId, lessonId }: { courseId: string; sectionId: string; lessonId?: string }) {
  const t = useTranslations("instructor");
  const router = useRouter();
  const queryClient = useQueryClient();
  const courseQuery = useCourse(courseId);
  const section = courseQuery.data?.sections.find((candidate) => candidate.sectionId === sectionId);
  const lesson = section?.lessons.find((candidate) => candidate.lessonId === lessonId);
  const addLesson = useAddLesson(courseId, sectionId);
  const updateLessonMutation = useUpdateLesson(courseId, sectionId, lessonId ?? "");
  const deleteLesson = useDeleteLesson(courseId, sectionId);
  const [form, setForm] = useState<LessonForm>(EMPTY_LESSON);
  const [savedForm, setSavedForm] = useState<LessonForm>(EMPTY_LESSON);
  const [video, setVideo] = useState<File>();
  const [videoError, setVideoError] = useState<string>();
  const [saveError, setSaveError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [uploading, setUploading] = useState(false);
  const dirty = JSON.stringify(form) !== JSON.stringify(savedForm) || Boolean(video);
  const guard = useUnsavedChanges(dirty, {
    title: t("unsaved.title"), description: t("unsaved.description"), discard: t("unsaved.discard"), keepEditing: t("unsaved.keepEditing"),
  });
  const returnHref = `/instructor/courses/${courseId}?tab=curriculum`;
  const saving = addLesson.isPending || updateLessonMutation.isPending || uploading;

  useEffect(() => {
    if (!lesson) return;
    const next = { title: lesson.title, description: lesson.description, resources: lesson.resources };
    setForm(next);
    setSavedForm(next);
  }, [lesson]);

  function setResource(index: number, field: keyof ResourceLink, value: string) {
    setForm((current) => ({ ...current, resources: current.resources.map((resource, resourceIndex) => resourceIndex === index ? { ...resource, [field]: value } : resource) }));
  }

  function chooseVideo(file: File) {
    const allowed = ["video/mp4", "video/webm"].includes(file.type) && file.size <= 500 * 1024 * 1024;
    if (!allowed) { setVideoError(t("lesson.videoError")); return; }
    setVideoError(undefined);
    setVideo(file);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.title.trim() || !form.description.trim()) { setSaveError(t("lesson.requiredError")); return; }
    setSaveError(undefined);
    let savedLessonId: string;
    const existingLessonIds = new Set(courseQuery.data?.sections.find((section) => section.sectionId === sectionId)?.lessons.map((candidate) => candidate.lessonId) ?? []);
    try {
      const savedCourse = lessonId
        ? await updateLessonMutation.mutateAsync({ title: form.title.trim(), description: form.description.trim(), resources: form.resources })
        : await addLesson.mutateAsync({ title: form.title.trim(), description: form.description.trim(), resources: form.resources });
      const savedLesson = lessonId
        ? savedCourse.sections.flatMap((section) => section.lessons).find((candidate) => candidate.lessonId === lessonId)
        : savedCourse.sections.find((section) => section.sectionId === sectionId)?.lessons.find((candidate) => !existingLessonIds.has(candidate.lessonId));
      if (!savedLesson) throw new Error("Saved lesson missing from course response");
      savedLessonId = savedLesson.lessonId;
    } catch {
      setSaveError(t("lesson.saveError"));
      return;
    }
    if (video) {
      setUploading(true);
      try {
        const uploaded = await uploadMedia({ kind: "lessonVideo", ownerRefId: savedLessonId, courseId, contentType: video.type, file: video });
        await updateLesson(courseId, sectionId, savedLessonId, { videoMediaId: uploaded.mediaId });
        await queryClient.invalidateQueries({ queryKey: ["courses", courseId] });
      } catch {
        setVideoError(t("upload.uploadError"));
        if (!lessonId) router.replace(`/instructor/courses/${courseId}/sections/${sectionId}/lessons/${savedLessonId}`);
        return;
      } finally {
        setUploading(false);
      }
    }
    setVideo(undefined);
    setSavedForm(form);
    router.replace(returnHref);
  }

  function removeResource(index: number) {
    setForm((current) => ({ ...current, resources: current.resources.filter((_, resourceIndex) => resourceIndex !== index) }));
  }

  if (courseQuery.isError) return <div className="mtx-instructor-page"><ErrorState description={t("lesson.loadError")} retryLabel={t("common.retry")} onRetry={() => courseQuery.refetch()} /></div>;
  if (!courseQuery.data) return <div className="mtx-instructor-page"><div className="mtx-skeleton mtx-account-skeleton" /></div>;
  if (!section || (lessonId && !lesson)) return <div className="mtx-instructor-page"><ErrorState description={t("lesson.loadError")} retryLabel={t("common.retry")} onRetry={() => courseQuery.refetch()} /></div>;

  return (
    <div className="mtx-instructor-page">
      <h1 className="mtx-text-heading-h1">{lessonId ? t("lesson.editTitle") : t("lesson.createTitle")}</h1>
      <form className="mtx-editor-panel" onSubmit={save}>
        <TextField label={t("lesson.titleLabel")} value={form.title} disabled={saving} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
        <TextField label={t("lesson.descriptionLabel")} value={form.description} disabled={saving} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
        <FileUpload label={t("lesson.videoLabel")} instruction={t("upload.videoInstruction")} browseLabel={t("upload.browse")} retryLabel={t("common.retry")} removeLabel={t("upload.remove")} accept="video/mp4,video/webm" file={video} existingName={lesson?.videoMediaId ? t("upload.attachedVideo") : undefined} uploading={uploading} success={Boolean(lesson?.videoMediaId) && !video} error={videoError} disabled={saving} onFile={chooseVideo} onRemove={() => { setVideo(undefined); setVideoError(undefined); }} />
        <section aria-labelledby="resources-heading">
          <div className="mtx-instructor-header"><h2 id="resources-heading" className="mtx-text-heading-h3">{t("lesson.resources")}</h2><Button type="button" variant="tonal" onClick={() => setForm((current) => ({ ...current, resources: [...current.resources, { label: "", url: "" }] }))}><Icon name="add" size={20} />{t("lesson.addResource")}</Button></div>
          <div className="mtx-instructor-list">
            {form.resources.map((resource, index) => (
              <div className="mtx-resource-row" key={index}>
                <TextField label={t("lesson.resourceLabel")} value={resource.label} onChange={(event) => setResource(index, "label", event.target.value)} />
                <TextField label={t("lesson.resourceUrl")} type="url" value={resource.url} onChange={(event) => setResource(index, "url", event.target.value)} />
                <button type="button" className="mtx-icon-button" aria-label={t("lesson.removeResource")} onClick={() => removeResource(index)}><Icon name="delete" size={20} /></button>
              </div>
            ))}
          </div>
        </section>
        {saveError && <p className="mtx-editor-error" role="alert">{saveError}</p>}
        {deleteLesson.isError && <p className="mtx-editor-error" role="alert">{t("lesson.deleteError")}</p>}
        <div className="mtx-editor-actions">
          <Button type="submit" loading={saving}>{t("lesson.save")}</Button>
          <Button type="button" variant="text" onClick={() => guard.navigate(returnHref)}>{t("common.cancel")}</Button>
          {lessonId && <Button type="button" variant="text" disabled={deleteLesson.isPending} onClick={() => setConfirmDelete(true)}>{t("lesson.delete")}</Button>}
        </div>
      </form>
      {guard.dialog}
      <AppDialog open={confirmDelete} title={t("lesson.deleteTitle")} description={t("lesson.deleteDescription")} confirmLabel={t("lesson.delete")} cancelLabel={t("common.cancel")} onCancel={() => setConfirmDelete(false)} onConfirm={() => lessonId && deleteLesson.mutate(lessonId, { onSuccess: () => router.replace(returnHref) })} />
    </div>
  );
}
