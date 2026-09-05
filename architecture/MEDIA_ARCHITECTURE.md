# Mentora — Media & Video Architecture

Storage decision: [ADR-008](./adr/ADR-008-media-storage.md) (local filesystem for the MVP, per [ADR-012](./adr/ADR-012-local-demo-scope.md)). This file details the upload flow, permission model, and playback architecture for course thumbnails, lesson videos, and lesson resources.

**CURRENT MVP ARCHITECTURE: local filesystem storage.** Course thumbnails, lesson resources, and demo lesson videos are stored on the developer's local machine, under a single storage root, and served by the Ktor backend through controlled HTTP endpoints. There is no cloud object storage, no CDN, and no MinIO/S3-compatible service in the MVP stack. Cloud object storage is described only in § 8 as optional future evolution.

---

## 1. The `MediaStorage` Abstraction

All storage access — local today, potentially cloud tomorrow — goes through one narrow backend interface, never called directly by route handlers:

```
interface MediaStorage {
    suspend fun store(key: String, contentType: String, bytes: ByteReadChannel): StoredMediaInfo
    suspend fun read(key: String): MediaContent   // supports HTTP range requests for video seeking
    suspend fun exists(key: String): Boolean
    suspend fun delete(key: String)
}
```

**Why this exists:** the domain/service layer (`media` module — validation, ownership checks, metadata persistence) never knows whether bytes end up on disk or in a bucket. This is what lets storage be replaced later (§ 8) "without redesigning the domain architecture," per the locked local-demo constraint — a new `MediaStorage` implementation is a bounded, additive change, not a rewrite of the upload flow, the permission model, or any client code.

**MVP implementation:** `LocalFileSystemMediaStorage` — writes/reads files under a configured storage root (`MEDIA_STORAGE_ROOT`, see [`DEPLOYMENT.md § 4`](./DEPLOYMENT.md)), outside of anything served as a bare static/public directory. See § 6 for safe path handling.

## 2. What's Stored Where

| Content | Local filesystem (via `MediaStorage`) | MongoDB (`media` collection, § [`DATABASE_MODEL.md § 15`](./DATABASE_MODEL.md)) |
|---|---|---|
| Course thumbnail (image) | The image bytes | `kind: courseThumbnail`, `ownerRefId: courseId`, `storageKey`, `contentType`, `sizeBytes`, `status` |
| Lesson video | The video bytes | `kind: lessonVideo`, `ownerRefId: lessonId`, `storageKey`, `contentType`, `sizeBytes`, `durationSeconds`, `status` |
| Lesson resources | **Not file storage** — resources are `{ label, url }` links embedded directly in the lesson document ([`DATABASE_MODEL.md § 4`](./DATABASE_MODEL.md)), per the product's own definition ("optional resource links" — external URLs, not uploaded files) | — |
| Student avatar | The image bytes | `kind: avatar`, `ownerRefId: userId`, ... |

**MongoDB never holds a byte of image/video content, and never stores a large video binary** — only the metadata above (path/key, content type, size, duration, status). This is a hard MVP requirement, not a preference, restated from [ADR-008](./adr/ADR-008-media-storage.md).

## 3. Upload Flow (Instructor thumbnail/lesson video, Student avatar)

Unlike a cloud object-storage design, there is no presigned-URL indirection here — the local filesystem has no equivalent, so the upload is a direct, backend-mediated request. This is intentionally simple, appropriate to a single-developer/demo-machine scale, not a scaled-down version of a cloud flow:

```
1. Client: user selects a file in FileUpload (per ../design-system/COMPONENTS.md § File & Media Upload).
2. Client → Backend: POST /api/v1/media/uploads  (multipart: file bytes + { kind, ownerRefId, contentType })
3. Backend:
   a. Authorization check — does this principal own ownerRefId? (e.g. is this Instructor the
      course's owner; is this the student's own userId for an avatar)
   b. Content-type/size validation against the allowlist (AUTH_SECURITY.md § 8) — reject before
      any storage write if invalid.
   c. Generate a server-side, opaque storageKey (never derived from client-supplied filenames/paths —
      see § 6) and write the bytes via MediaStorage.store(...).
   d. Insert a `media` document with the resulting storageKey, contentType, sizeBytes, status: ready.
   e. Return { mediaId, storageKey } to the client.
4. Client: attaches mediaId to the owning course/lesson/profile field (e.g. PATCH the course's
   thumbnailMediaId) — a separate, ordinary API call, not implicit in step 3.
```

**Failure handling:** if step 3 fails part-way (write error, validation failure), no `media` document is created — there is no orphaned-record cleanup problem here the way a two-phase presigned-upload flow has, because the backend controls the entire write in one request.

## 4. Client-Side Upload UX

Maps onto [`../design-system/COMPONENTS.md § File & Media Upload`](../design-system/COMPONENTS.md)'s five states: Idle → Drag-over → Uploading (progress reported from the client's own upload request to the backend, since the backend is directly in the data path in this local design) → Success / Error. A failed upload (network interruption) is retried by resubmitting the same request, per [`../design-system/CONTENT_RESILIENCE.md § 4`](../design-system/CONTENT_RESILIENCE.md).

## 5. Permissions & Playback

- **Thumbnails/avatars:** served through a Ktor route (`GET /api/v1/media/{mediaId}/file`) that streams the file from local storage — public-read (no per-request permission check), matching Course Details being visible to Guests.
- **Lesson videos are never publicly addressable.** Playback works as:
  ```
  Client (in Course Player) → GET /api/v1/media/{mediaId}/playback-url
  Backend: verify principal is enrolled in the course owning this lesson
           (or is the owning Instructor/an Admin previewing) → 403 FORBIDDEN_NOT_ENROLLED otherwise
  Backend: issue a short-lived (minutes-scale) signed reference — a time-limited token bound to
           this mediaId, verified by the backend on every subsequent request to it
  Client: passes the resulting URL directly to the platform's native video element/player,
          which the backend then streams from local storage (supporting HTTP range requests
          for scrubbing/seeking)
  ```
  The signed reference is re-requested each time a lesson's video is opened (or refreshed if the player session outlives the token's expiry) — never cached long-term client-side or persisted anywhere, so a leaked URL has a short, bounded window of validity. The access-control property is the same one a cloud presigned-URL design would provide; only the signing/verification mechanism differs (the backend checks its own token on each request, rather than an object-storage service validating a cryptographic signature).

## 6. Safe Path Handling

- All files live under a single **storage root** directory (`MEDIA_STORAGE_ROOT`), never mixed with application code or served as a bare static directory with directory listing enabled.
- The `storageKey` is always **server-generated** (e.g. a UUID plus a validated extension derived from the checked `contentType` — never the client's raw filename), so a request can never smuggle a path-traversal sequence (`../../`) into the on-disk path.
- Every `MediaStorage.read`/`store`/`delete` call resolves the key against the storage root and rejects any result that would resolve outside it — a defensive check made explicit here because, unlike a cloud object-storage bucket's own key namespacing, the local filesystem does not enforce this on its own.
- Files are served only through the Ktor routes in § 5 — the storage root itself is never registered as a static-file mount reachable by path, so there is no route that bypasses the ownership/enrollment checks above.

## 7. Video Playback (Client-Side)

No custom video engine is built. Each platform uses its native/standard player against the same backend-issued playback URL:

| Platform | Player |
|---|---|
| Web | HTML5 `<video>`, custom controls skinned to `../design-system/COMPONENTS.md § Media & Playback`'s `VideoPlayer`/`PlaybackControls` spec (play/pause, scrubber, volume, speed, fullscreen — scrubber stays LTR always, per `../design-system/LOCALIZATION.md § 3`) |
| Android | Media3/ExoPlayer, wrapped in a Compose `AndroidView`, same custom control skin |
| iOS | `AVPlayer`, wrapped in a SwiftUI `UIViewControllerRepresentable`, same custom control skin |

The `shared` KMP module defines a *platform-agnostic playback interface* (`play`/`pause`/`seek`/`currentPosition: Flow<Duration>`) that each mobile platform's ViewModel/ObservableObject implements by wrapping its native player — this is the one narrow place mobile playback logic is described in a shared contract, without sharing the actual player implementation (per [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md)).

**Adaptive bitrate (HLS) is explicitly not built in MVP.** Progressive MP4, streamed locally with range-request support, is more than sufficient for a local portfolio demo and dramatically simpler than a transcoding pipeline — see [ADR-008](./adr/ADR-008-media-storage.md)'s Migration Path for how HLS could be added later without changing the permission model above it.

## 8. Video Metadata (Duration)

Duration is probed once, at upload time (§ 3 step 3) — either via a lightweight server-side probe (reading the file's container metadata locally) or, more simply for MVP, accepted from the client's own `HTMLVideoElement`/platform metadata reading at selection time and verified loosely server-side. Exact mechanism is an implementation-time detail; either approach keeps `media.durationSeconds` populated for lesson-list duration display.

## 9. OPTIONAL FUTURE EVOLUTION — NOT PART OF CURRENT MVP IMPLEMENTATION

If Mentora ever moves beyond local-demo scope ([ADR-012](./adr/ADR-012-local-demo-scope.md)), an S3-compatible `MediaStorage` implementation (Cloudflare R2 or AWS S3, MinIO as a local stand-in during that future work) can be added and bound in place of `LocalFileSystemMediaStorage` — the upload flow would shift back to the presigned-URL pattern (client uploads directly to object storage, backend never buffers the bytes), the playback flow would shift from a backend-verified token to a signed GET URL, and a CDN could sit in front of thumbnails/avatars. None of this changes the `media` collection's schema, the ownership/enrollment authorization checks, or any client-side player code beyond the URL it's given — see [ADR-008](./adr/ADR-008-media-storage.md)'s Migration Path for the full detail. This is not scheduled or required by the current MVP roadmap.
