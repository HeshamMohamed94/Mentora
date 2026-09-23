package com.mentora.backend.common

import com.mongodb.MongoException
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Runs [block] inside a fresh MongoDB transaction, implementing both halves of the retry pattern
 * MongoDB's own docs prescribe for multi-document transactions (see "Transactions and Retryability"):
 * 1. If the transaction itself aborts with a `TransientTransactionError` label (e.g. a genuine
 *    write conflict between two truly concurrent transactions on the same document — MongoDB error
 *    112, `WriteConflict`), retry the WHOLE transaction from scratch, since none of its writes
 *    committed. [block] must therefore be safe to re-run (this is true for every current caller:
 *    each either creates a document guarded by a unique index, whose retry becomes a clean
 *    DUPLICATE_KEY the caller already handles, or performs a `findOneAndUpdate`/insert that is
 *    fine to redo against fresh state).
 * 2. If the COMMIT (not the transaction body) fails with an `UnknownTransactionCommitResult` label
 *    (e.g. a network blip after the server already applied it), retry only the commit — replaying
 *    it is safe because MongoDB transaction commits are idempotent at the server.
 *
 * Found necessary via Phase 8 C4's gap analysis: two truly concurrent writers hitting the same
 * document inside a transaction don't always fail as a clean, catchable `MongoWriteException` —
 * MongoDB's transaction concurrency control can abort the losing transaction immediately with a
 * `MongoCommandException` carrying this label instead, on ANY MongoDB-driver-thrown exception
 * (checked before any narrower per-call-site exception type, so a caller's own
 * `catch (error: MongoWriteException)` for a domain-specific case like DUPLICATE_KEY still sees
 * every non-transient failure exactly as before). Retries use exponential backoff with full jitter
 * so that concurrent losers don't all restart in lockstep and simply re-form the same contention.
 */
suspend fun <T> MongoClient.withRetryableTransaction(block: suspend (ClientSession) -> T): T {
    repeat(TRANSACTION_RETRY_LIMIT) { attempt ->
        val session = startSession()
        try {
            session.startTransaction()
            val result = try {
                block(session)
            } catch (error: Throwable) {
                if (session.hasActiveTransaction()) session.abortTransaction()
                throw error
            }
            while (true) {
                try {
                    session.commitTransaction()
                    return result
                } catch (error: MongoException) {
                    if (!error.hasErrorLabel("UnknownTransactionCommitResult")) throw error
                    // Replay just the commit — the transaction body already ran exactly once.
                }
            }
        } catch (error: MongoException) {
            val isLastAttempt = attempt == TRANSACTION_RETRY_LIMIT - 1
            if (!error.hasErrorLabel("TransientTransactionError") || isLastAttempt) throw error
        } finally {
            session.close()
        }
        // Only reached when the transaction body/commit aborted with a retryable
        // TransientTransactionError and attempts remain — back off before the next `repeat` pass.
        delay(backoffMillisWithFullJitter(attempt))
    }
    error("unreachable: retry loop always returns or throws")
}

/** Full jitter: a uniformly random delay between 0 and the exponential backoff ceiling for this
 * attempt (capped), so concurrent losers spread out instead of retrying in lockstep. */
private fun backoffMillisWithFullJitter(attempt: Int): Long {
    val ceiling = (BASE_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT)).coerceAtMost(MAX_BACKOFF_MILLIS)
    return Random.nextLong(ceiling + 1)
}

private const val TRANSACTION_RETRY_LIMIT = 10
private const val BASE_BACKOFF_MILLIS = 5L
private const val MAX_BACKOFF_MILLIS = 200L
private const val MAX_BACKOFF_SHIFT = 8
