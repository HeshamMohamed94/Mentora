"use client";

import { useEffect, useRef, useState } from "react";
import clsx from "clsx";
import { Icon, type IconName } from "./icon";
import { artworkMotifFor } from "@/lib/design-to-code.generated";

/**
 * The governed Mentora course-artwork system (design-system's locked visual reference, "one
 * family, five recognisable subjects"): a fixed set of dark purple/indigo gradient motifs, each
 * with one centered geometric icon, never photography and never a per-course invented palette.
 * These gradients are an artwork system, not application UI colours, so — per that governance —
 * they are deliberately literal values, sourced from design-to-code/shared/artwork.json (the
 * single source of truth for this system, see design-to-code/README.md) rather than hardcoded
 * here — see design-to-code/validation/MAPPING_REPORT.md for the migration record.
 *
 * Seed-data course/lesson media is undecoded placeholder bytes
 * (backend/src/main/kotlin/com/mentora/backend/SeedData.kt), not real decodable images — the
 * browser renders a broken-image icon for every course thumbnail in the local demo. Rather than
 * pointing `<img>` at that and letting it fail visibly, this renders the governed artwork
 * whenever there's no media id or the image fails to decode.
 *
 * Motif choice is deterministic — hashed from `categoryId` when known (so every course in a
 * category reads as the same "subject"), else from `seed` — never per-render random.
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
  seed,
  categoryId,
  badge,
}: {
  mediaId?: string | null;
  className: string;
  iconSize?: number;
  /** Stable per-course key (e.g. course id) used to pick an artwork motif when `categoryId`
   * isn't available to this call site. */
  seed: string;
  categoryId?: string;
  /** Rendered pinned to the artwork's logical start corner over a scrim — e.g. a category chip. */
  badge?: React.ReactNode;
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
    const motif = artworkMotifFor(categoryId || seed);
    return (
      <div className={clsx(className, "mtx-thumbnail-fallback")} style={{ backgroundImage: motif.gradient }}>
        <Icon name={motif.icon as IconName} size={iconSize} className="mtx-thumbnail-fallback-icon" />
        {badge}
      </div>
    );
  }

  return (
    <div className={clsx(className, "mtx-thumbnail-media")}>
      <img
        ref={imgRef}
        src={`/api/v1/media/${mediaId}/file`}
        alt=""
        className="mtx-thumbnail-media-img"
        onError={() => setFailed(true)}
        onLoad={(event) => {
          if (event.currentTarget.naturalWidth === 0) setFailed(true);
        }}
      />
      {badge}
    </div>
  );
}
