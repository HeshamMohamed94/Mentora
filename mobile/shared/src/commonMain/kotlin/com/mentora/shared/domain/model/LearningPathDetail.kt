package com.mentora.shared.domain.model

/**
 * `GET /api/v1/learning-paths/{id}?language=`'s response — mirrors the backend's
 * `LearningPathResponse` (`LearningPathService.kt:19-26`) field-for-field: `id, title, description,
 * courses, progressPercent, isFollowing`.
 *
 * The route (`LearningPathRoutes.kt:21-28`) uses `authenticate("jwt-auth", optional = true)` — guest
 * requests (no/invalid bearer) reach the handler with a `null` principal, never a 401. Verified from
 * `LearningPathService.get()`:
 * - [progressPercent] is `principal?.let { ... }` — genuinely `null` for a guest, never `0` or any
 *   other placeholder. For an authenticated student it is server-computed from the fraction of
 *   [courses] with `courseCompletedAt != null` (via `ProgressService.snapshotForCompletion()`),
 *   `shared` never recomputes or caches this value independently across reads — every read reflects
 *   whatever the server just computed for that request.
 * - [isFollowing] is `principal?.let { repository.isFollowing(...) } ?: false` — `false` for a guest
 *   (not `null`; this field is non-nullable on the wire and here), and the real per-user follow state
 *   for an authenticated student.
 *
 * [courses] preserves the curated order of the path's stored `courseIds` (`LearningPathDocument.courseIds`,
 * a plain ordered `List<ObjectId>` — not alphabetical/id-sorted) exactly as returned by
 * `LearningPathService.get()`'s `path.courseIds.mapNotNull { ... }` — `shared` applies no client-side
 * re-sort here (unlike Task 7's course sections/lessons, which the backend does NOT guarantee sorted).
 */
data class LearningPathDetail(
    val id: String,
    val title: String,
    val description: String,
    val courses: List<LearningPathCourse>,
    val progressPercent: Int?,
    val isFollowing: Boolean,
)
