package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.CourseProgress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * `POST /api/v1/courses/{id}/lessons/{lessonId}/position` — the player-driven playback-position
 * heartbeat.
 *
 * **Throttling policy (the one, single mechanism `shared` uses for this — Task 9 AC #6):** never
 * send more than one heartbeat per [throttleWindow] of REAL elapsed time (default 5 seconds),
 * per use-case instance. The very first call after construction always goes through immediately
 * (there is nothing to throttle against yet); every subsequent call within [throttleWindow] of the
 * last one actually SENT is silently dropped — returning `null` rather than an [ApiResult] — and a
 * call spaced [throttleWindow] or more after the last sent one always goes through. A dropped
 * heartbeat is never retried and never queued: the next player tick will naturally call this again
 * with a fresher position, which is strictly better than replaying a stale one.
 *
 * Testable by construction: real elapsed time is never read via a hardcoded `Clock.System.now()`
 * or platform clock call inline in the throttle check. Instead, the current time is obtained
 * exclusively through the injected [timeSource] (a [TimeSource], defaulting to
 * [TimeSource.Monotonic] in production) — tests inject a [kotlin.time.TestTimeSource] and advance
 * it manually to deterministically land calls inside/outside the window without any real `sleep`.
 *
 * Returns the server's fresh [CourseProgress] when a heartbeat was actually sent (`null` when it
 * was throttled) so a caller CAN reconcile a transient optimistic position display against the
 * server response, per Task 9 AC #7 — but is never required to.
 */
class ReportPlaybackPositionUseCase(
    private val repository: ProgressRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val throttleWindow: Duration = 5.seconds,
) {
    private var lastSentAt: TimeMark? = null

    suspend operator fun invoke(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress>? {
        val last = lastSentAt
        if (last != null && last.elapsedNow() < throttleWindow) return null

        lastSentAt = timeSource.markNow()
        return repository.reportPosition(courseId, lessonId, positionSeconds)
    }
}
