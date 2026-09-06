"use client";

import { useEffect, useRef, useState } from "react";
import clsx from "clsx";
import { Icon } from "./icon";

/**
 * Seed-data course/lesson artwork is literal placeholder bytes served with an image
 * content-type (backend/src/main/kotlin/com/mentora/backend/SeedData.kt), not real decodable
 * images — the browser renders a broken-image icon for every course thumbnail in the local
 * demo. Rather than pointing `<img>` at that and letting it fail visibly, render a branded
 * placeholder (same token-driven pattern as Avatar's initials fallback / Certificate's
 * placeholder document) whenever there's no media id or the image fails to decode.
 *
 * Detection needs more than `onError` — confirmed live that Chrome treats this as a successful
 * fetch (the HTTP response is a normal 200 with an `image/*` content-type) and marks the `<img>`
 * `complete` with 0x0 dimensions instead of firing `error`, including on a cached repeat visit
 * where `load` doesn't reliably fire again either. `onLoad`/`onError` cover the normal path; a
 * `requestAnimationFrame` check after mount catches the already-cached case.
 *
 * `className` carries the full sizing/radius treatment for the context this renders in (e.g.
 * `mtx-card-thumbnail` for a CourseCard, `mtx-checkout-thumbnail` for a Checkout line item) —
 * passed through verbatim so this component never hardcodes one context's dimensions.
 */
export function CourseThumbnail({
  mediaId,
  className,
  iconSize = 32,
}: {
  mediaId?: string | null;
  className: string;
  iconSize?: number;
}) {
  const [failed, setFailed] = useState(false);
  const imgRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    if (!mediaId || failed) return;
    const frame = requestAnimationFrame(() => {
      const img = imgRef.current;
      if (img?.complete && img.naturalWidth === 0) setFailed(true);
    });
    return () => cancelAnimationFrame(frame);
  }, [mediaId, failed]);

  if (!mediaId || failed) {
    return (
      <div className={clsx(className, "mtx-thumbnail-fallback")} aria-hidden="true">
        <Icon name="myLearning" size={iconSize} />
      </div>
    );
  }

  return (
    <img
      ref={imgRef}
      src={`/api/v1/media/${mediaId}/file`}
      alt=""
      className={className}
      onError={() => setFailed(true)}
      onLoad={(event) => {
        if (event.currentTarget.naturalWidth === 0) setFailed(true);
      }}
    />
  );
}
