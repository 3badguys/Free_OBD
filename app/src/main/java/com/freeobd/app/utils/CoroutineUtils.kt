package com.freeobd.app.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlin.math.pow

/**
 * Coroutine utility extensions for common OBD-II async patterns.
 */

/**
 * Execute a suspend block with a timeout, returning a default value on timeout or failure.
 *
 * Useful for OBD commands that may hang due to adapter buffer issues.
 *
 * @param timeoutMs Timeout in milliseconds.
 * @param defaultValue Value to return if the block times out or throws.
 * @param block The operation to execute.
 */
suspend fun <T> withTimeoutOrDefault(
    timeoutMs: Long,
    defaultValue: T,
    block: suspend () -> T
): T {
    return try {
        withTimeout(timeoutMs) { block() }
    } catch (_: TimeoutCancellationException) {
        defaultValue
    } catch (_: Exception) {
        defaultValue
    }
}

/**
 * Retry a suspend operation with exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts.
 * @param initialDelayMs Initial delay before the first retry.
 * @param maxDelayMs Maximum delay cap.
 * @param factor Backoff multiplier per retry (e.g. 2.0 for exponential).
 * @param block The operation to retry.
 * @return The result if any attempt succeeds.
 * @throws Exception The last failure if all attempts are exhausted.
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 5000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var lastException: Throwable? = null

    for (attempt in 0..maxRetries) {
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries) {
                val delay = (initialDelayMs * factor.pow(attempt.toDouble()))
                    .toLong()
                    .coerceAtMost(maxDelayMs)
                delay(delay)
            }
        }
    }

    throw lastException ?: IllegalStateException("Retry failed with no exception")
}

/**
 * Extension: sample a flow at the specified interval, skipping intermediate values.
 * This is a re-export of Flow.sample() with a more descriptive name for OBD polling contexts.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
fun <T> Flow<T>.throttle(periodMs: Long): Flow<T> = this.sample(periodMs)

/**
 * Extension: collect a flow with exception handling that logs and continues.
 */
fun <T> Flow<T>.collectSafely(
    scope: CoroutineScope,
    onError: (Throwable) -> Unit = { android.util.Log.e("FlowCollect", "Error collecting flow", it) },
    collector: suspend (T) -> Unit
): Job {
    return scope.launch {
        try {
            collect { value -> collector(value) }
        } catch (e: CancellationException) {
            // Normal cancellation — don't log
        } catch (e: Exception) {
            onError(e)
        }
    }
}
