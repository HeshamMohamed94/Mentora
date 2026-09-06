"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import clsx from "clsx";

export type AvatarSize = "small" | "medium" | "large" | "xlarge";

export interface AvatarProps {
  name: string;
  src?: string;
  size?: AvatarSize;
  statusLabel?: string;
  className?: string;
}

const AVATAR_DIMENSIONS: Record<AvatarSize, number> = {
  small: 24,
  medium: 40,
  large: 64,
  xlarge: 96,
};

function initials(name: string): string {
  const nameParts = name.trim().split(/\s+/).filter(Boolean);
  if (nameParts.length === 0) return "?";
  return [nameParts[0], nameParts.at(-1)].map((part) => Array.from(part ?? "")[0]).join("").toUpperCase();
}

/** design-system/COMPONENTS.md Avatar, including image failure fallback and labeled status. */
export function Avatar({ name, src, size = "medium", statusLabel, className }: AvatarProps) {
  const [imageFailed, setImageFailed] = useState(false);
  const dimension = AVATAR_DIMENSIONS[size];

  useEffect(() => setImageFailed(false), [src]);

  return (
    <span className={clsx("mtx-avatar", `mtx-avatar-${size}`, className)}>
      {src && !imageFailed ? (
        <Image
          src={src}
          alt={name}
          width={dimension}
          height={dimension}
          unoptimized
          onError={() => setImageFailed(true)}
        />
      ) : (
        <span className="mtx-avatar-fallback" role="img" aria-label={name}>
          {initials(name)}
        </span>
      )}
      {statusLabel && (
        <span className="mtx-avatar-status" title={statusLabel}>
          <span className="sr-only">{statusLabel}</span>
        </span>
      )}
    </span>
  );
}
