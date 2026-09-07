"use client";

import { useEffect, useRef, useState } from "react";
import clsx from "clsx";
import { Icon, type IconName } from "./icon";

/**
 * The governed Mentora course-artwork system (design-system's locked visual reference, "one
 * family, five recognisable subjects"): a fixed set of dark purple/indigo gradient motifs, each
 * with one centered geometric icon, never photography and never a per-course invented palette.
 * These gradients are an artwork system, not application UI colours, so — per that governance —
 * they are deliberately literal values here rather than semantic design tokens.
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
interface ArtworkMotif {
  gradient: string;
  icon: IconName;
}

const ARTWORK_MOTIFS: ArtworkMotif[] = [
  {
    gradient:
      "radial-gradient(circle at 84% 16%, rgba(255,255,255,0.22), transparent 48%), repeating-linear-gradient(90deg, transparent 0 6%, rgba(255,255,255,0.15) 6% 10%), linear-gradient(135deg, #241C5C 0%, #4A3EB0 58%, #4A62F0 100%)",
    icon: "courseAnalytics",
  },
  {
    gradient:
      "repeating-linear-gradient(45deg, rgba(255,255,255,0.14) 0 1px, transparent 1px 9%), radial-gradient(circle at 76% 78%, rgba(255,255,255,0.20), transparent 42%), linear-gradient(135deg, #35257F 0%, #6558D3 60%, #7C4DFF 100%)",
    icon: "courseDesign",
  },
  {
    gradient:
      "repeating-radial-gradient(circle at 72% 50%, rgba(255,255,255,0.16) 0 1.5px, transparent 1.5px 13px), radial-gradient(circle at 72% 50%, rgba(255,255,255,0.22), transparent 30%), linear-gradient(135deg, #1C2470 0%, #3B4278 50%, #4A62F0 100%)",
    icon: "courseCode",
  },
  {
    gradient:
      "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.20), transparent 46%), repeating-linear-gradient(0deg, rgba(255,255,255,0.13) 0 1px, transparent 1px 18%), repeating-linear-gradient(90deg, rgba(255,255,255,0.13) 0 1px, transparent 1px 14%), linear-gradient(135deg, #3B2A7A 0%, #6558D3 55%, #7C4DFF 100%)",
    icon: "courseGrid",
  },
  {
    gradient:
      "repeating-linear-gradient(180deg, rgba(255,255,255,0.14) 0 2px, transparent 2px 22%), radial-gradient(circle at 18% 82%, rgba(255,255,255,0.18), transparent 44%), linear-gradient(135deg, #191A20 0%, #2B2170 55%, #3B4278 100%)",
    icon: "courseLayers",
  },
];

function motifFor(key: string): ArtworkMotif {
  let hash = 0;
  for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
  return ARTWORK_MOTIFS[hash % ARTWORK_MOTIFS.length] ?? ARTWORK_MOTIFS[0]!;
}

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
    const motif = motifFor(categoryId || seed);
    return (
      <div className={clsx(className, "mtx-thumbnail-fallback")} style={{ backgroundImage: motif.gradient }}>
        <Icon name={motif.icon} size={iconSize} className="mtx-thumbnail-fallback-icon" />
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
