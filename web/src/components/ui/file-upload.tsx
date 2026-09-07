"use client";

import { DragEvent, useId, useState } from "react";
import { Button } from "./button";
import { Icon } from "./icon";

export interface FileUploadProps {
  label: string;
  instruction: string;
  browseLabel: string;
  retryLabel: string;
  removeLabel: string;
  accept: string;
  file?: File;
  existingName?: string;
  uploading?: boolean;
  success?: boolean;
  error?: string;
  disabled?: boolean;
  /** COMPONENTS.md § FileUpload Success state: "Thumbnail preview (image/video)... " — an
   * optional rich visual preview (e.g. a CourseThumbnail) rendered above the filename row when
   * the upload target is an image. Lesson video/resource uploads omit this and keep the plain
   * filename + status-icon treatment. */
  preview?: React.ReactNode;
  onFile: (file: File) => void;
  onRemove: () => void;
}

function middleTruncate(filename: string): string {
  if (filename.length <= 28) return filename;
  const extensionAt = filename.lastIndexOf(".");
  const extension = extensionAt > 0 ? filename.slice(extensionAt) : "";
  return `${filename.slice(0, Math.max(10, 24 - extension.length))}…${extension}`;
}

export function FileUpload(props: FileUploadProps) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);
  const filename = props.file?.name ?? props.existingName;

  function acceptDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file && !props.disabled) props.onFile(file);
  }

  return (
    <div className="mtx-field">
      <span className="mtx-field-label">{props.label}</span>
      <div
        className="mtx-file-upload"
        data-dragging={dragging || undefined}
        data-error={Boolean(props.error) || undefined}
        data-success={props.success || undefined}
        data-disabled={props.disabled || undefined}
        onDragEnter={(event) => { event.preventDefault(); if (!props.disabled) setDragging(true); }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={() => setDragging(false)}
        onDrop={acceptDrop}
      >
        {filename ? (
          <>
            {props.preview && <div className="mtx-file-upload-preview">{props.preview}</div>}
            <div className="mtx-file-upload-file">
              <Icon name={props.error ? "cancel" : props.success ? "checkCircle" : "upload"} size={20} />
              <span className="mtx-file-upload-name" title={filename}>{middleTruncate(filename)}</span>
              {props.file && <span className="mtx-file-upload-size">{Math.ceil(props.file.size / 1024)} KB</span>}
              {props.file && (
                <button type="button" className="mtx-icon-button" aria-label={props.removeLabel} disabled={props.disabled || props.uploading} onClick={props.onRemove}>
                  <Icon name="close" size={20} />
                </button>
              )}
            </div>
          </>
        ) : (
          <>
            <Icon name="upload" size={32} />
            <p>{props.instruction}</p>
            <Button type="button" variant="tonal" disabled={props.disabled} onClick={() => document.getElementById(inputId)?.click()}>{props.browseLabel}</Button>
          </>
        )}
        {props.uploading && <div className="mtx-file-upload-progress" role="status"><span className="mtx-progress-indeterminate" /></div>}
        {props.error && <p className="mtx-file-upload-error" role="alert">{props.error}</p>}
        {props.error && props.file && <Button type="button" variant="text" onClick={() => props.onFile(props.file as File)}>{props.retryLabel}</Button>}
        <input
          id={inputId}
          className="sr-only"
          type="file"
          aria-label={props.label}
          accept={props.accept}
          disabled={props.disabled}
          onChange={(event) => { const file = event.target.files?.[0]; if (file) props.onFile(file); }}
        />
      </div>
    </div>
  );
}
