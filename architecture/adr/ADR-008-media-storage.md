# ADR-008: Media & Video Storage

**Status:** Locked (amended for local-demo scope, see [ADR-012](./ADR-012-local-demo-scope.md))
**Date:** 2026-09-04

## Decision

Course thumbnails, lesson videos, and lesson resource files are stored on the **local filesystem**, behind a small backend-owned `MediaStorage` abstraction — this is the active MVP decision, not a local stand-in for a cloud target. **MongoDB stores only metadata** (storage path/key, content type, size, duration, dimensions, processing status, uploader) — never binary media content, and never large video binaries. Uploads and downloads are served through **controlled Ktor HTTP endpoints**, never a raw static file mount with no auth in front of it. S3-compatible object storage (Cloudflare R2 or AWS S3) is **not used in the MVP** and is retained only as a documented, optional future migration path (§ Migration Path), per [ADR-012](./ADR-012-local-demo-scope.md).

## Context

The brief is explicit: "Do NOT store large videos directly in MongoDB documents," and asks for "a practical portfolio architecture that can later evolve," not "Netflix-grade streaming infrastructure." [ADR-012](./ADR-012-local-demo-scope.md) further locks the MVP as a local-only system — no cloud object storage, no CDN — so this ADR's job is to satisfy "never binary-in-MongoDB" and "safe, permission-checked access" using only the local filesystem and the local Ktor process.

## Options Considered

### Option A — Local filesystem storage behind a `MediaStorage` abstraction, served through Ktor (chosen)

**Upload flow:** Instructor selects a file in `FileUpload` → client uploads the file **to the Ktor backend** (a standard multipart request; the backend is in the data path in this local design, unlike the presigned-direct-to-storage flow a cloud deployment would use) → backend verifies the requesting user owns/may edit the target course/lesson, validates content-type/size, writes the file to a storage-root-relative path via the `MediaStorage` interface's local-filesystem implementation, and records metadata (path/key, content type, size, status) in the `media` collection.

**Why this shape:** the local filesystem has no equivalent to a presigned URL, so the direct-to-storage upload pattern a cloud deployment would use doesn't apply locally — routing the (comparatively small, local-network) upload through the backend is simple, requires no additional local service, and is entirely appropriate at local-demo scale (a single developer/demo machine, not concurrent multi-tenant traffic). The `MediaStorage` interface (`store`, `read`, `delete`, `exists`, keyed by a stable storage key) is defined narrowly enough that a future S3-compatible implementation is a drop-in replacement — see § Migration Path — so the abstraction, not any specific cloud SDK, is what keeps this architecture "practical... that can later evolve."

### Option B — Store video/image binaries directly in MongoDB (GridFS or raw BSON binary fields)

**Why not:** explicitly excluded by the brief, and independently excluded by this local-demo decision (§ 8 of the LOCAL-DEMO constraints: "MongoDB must NOT store large video binaries"). Even MongoDB's own GridFS (designed for exactly this) still routes every byte of every video through the database's own storage layer, bloating backup/restore size and working against MongoDB document-size limits in spirit. There is no scenario in this architecture where this is the right call, local or otherwise.

### Option C — S3-compatible object storage (Cloudflare R2/AWS S3) + presigned URLs, with MinIO as a local stand-in

**Why not for the current MVP:** this was the original direction before [ADR-012](./ADR-012-local-demo-scope.md) locked the MVP as local-only. It remains a legitimate, low-regret **future** option (§ Migration Path) — the reasoning that made it attractive (zero-ops direct-to-storage upload, CDN edge caching, no Ktor process buffering large files) is real, but it requires a cloud account, billing, and either a local stand-in service (MinIO) or the storage-abstraction indirection this ADR now builds anyway. Given the MVP has no multi-tenant traffic and no public reachability requirement, the local filesystem satisfies every functional requirement (never-binary-in-Mongo, permission-checked playback, safe path handling) without that additional operational surface. Not adopted now; the `MediaStorage` abstraction in Option A is specifically designed so adopting it later costs a new implementation, not a redesign.

## Local Storage Layout & Safe Path Handling

- Files are written under a single **storage root** directory (e.g. `backend/storage/media/`, outside of anything served as static/public by default), organized by `kind`/`ownerRefId` (e.g. `storage/media/lesson-video/<lessonId>/<mediaId>.<ext>`).
- The `storageKey` recorded in MongoDB is a **server-generated, opaque identifier** (never derived from unsanitized client input) — the backend, not the client, decides the on-disk path. This closes the path-traversal risk (`../../etc/passwd`-style keys) that a naively client-supplied filename/path would open.
- Every read/write through `MediaStorage` resolves the key against the storage root and rejects any resolved path that escapes it (a defensive check, not just "trust the generated key") — the same discipline a cloud SDK's key-namespacing would give for free, made explicit here because the filesystem doesn't enforce it on its own.

## Playback & Permissions

- **Thumbnails/avatars:** served through a Ktor route that streams the file from local storage — public-read (no per-request permission check), matching Course Details being visible to Guests. No CDN in front of it in MVP; a local/LAN request has no meaningful latency problem this would solve.
- **Lesson videos are never publicly addressable.** The backend issues a **short-lived, signed reference** (e.g. a time-limited token embedded in the playback URL, verified by the backend on each request) only after verifying the requesting user is enrolled in the course (or is the owning Instructor/an Admin previewing) — the mechanism is simpler than cloud presigned-URL cryptographic signing (it's the same backend process checking its own token on every request, not a third-party storage service validating a signature), but the access-control property is identical: an unauthorized or expired request is rejected before any bytes are streamed.
- **Playback:** Ktor streams the local file (supporting HTTP range requests, so seeking/scrubbing works) as progressive MP4 — standard HTML5 `<video>` (Web), Media3/ExoPlayer (Android), `AVPlayer` (iOS), all pointed at the same backend-issued playback URL. No adaptive-bitrate (HLS) transcoding pipeline in MVP — a real, deliberate scope cut, not an oversight (see Migration Path).

## Consequences

- `Media` (metadata) is its own MongoDB collection, referenced from `Course`/`Lesson` by ID — never embedding a storage path string informally inline without the accompanying processing-state/permission metadata, and **never** the file's binary content (see [`DATABASE_MODEL.md § Media`](../DATABASE_MODEL.md)).
- Upload size/type limits are enforced **twice**: client-side for UX (immediate feedback before an upload starts) and server-side, authoritatively, before the backend writes anything to disk — a client-side-only check would be trivially bypassable.
- The Ktor process is in the data path for both upload and playback in this local design (unlike a cloud direct-to-storage/CDN flow) — an accepted, deliberate simplicity trade-off at local-demo scale, called out explicitly rather than silently inherited from the original cloud-first design.
- `MEDIA_ARCHITECTURE.md` documents the full upload/permission/playback flow this ADR summarizes, including the `MediaStorage` interface shape.

## Migration Path

**OPTIONAL FUTURE EVOLUTION — NOT PART OF CURRENT MVP IMPLEMENTATION.** If Mentora ever moves beyond local-demo scope ([ADR-012](./ADR-012-local-demo-scope.md)), two independent upgrades are available, neither of which requires a domain-model or MongoDB schema change (the `media` collection's shape — path/key, content type, size, status — is storage-backend-agnostic already):

1. **Object storage:** implement a second `MediaStorage` (Cloudflare R2 or AWS S3, S3-compatible SDK, presigned PUT/GET URLs replacing the local-filesystem read/write path) and switch the bound implementation via configuration/DI — no route, permission-check, or client code changes beyond the client uploading/playing against a presigned URL instead of a backend-streamed one. MinIO can serve as a local stand-in for this cloud SDK during that future migration's own development, exactly as originally planned in Option C above.
2. **Adaptive bitrate (HLS):** a transcoding step (`ffmpeg`, or a managed service like Cloudflare Stream/AWS MediaConvert) inserted between "upload confirmed" and "available for playback," changing only the `Media` document's `status`/`playbackUrl` fields — independent of which `MediaStorage` implementation is active.

Neither step is required, scheduled, or assumed by the current MVP roadmap.
