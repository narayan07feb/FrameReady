package com.frameready

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

/**
 * Reified Kotlin extensions for [FrameReady] — one implementation shared by every platform.
 *
 * ```kotlin
 * val analytics = FrameReady.await<AnalyticsInitializer>()
 * val flow = FrameReady.asStateFlow<AnalyticsInitializer>()
 * ```
 */

/** Suspends until the result of [C] is available, then returns it. */
suspend inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.await(
    timeoutMs: Long = 5000L
): T = await(C::class, timeoutMs)

/** Returns the result of [C] if already complete, or `null` if pending or failed. */
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.getOrNull(): T? =
    getOrNull(C::class)

/** Returns a read-only [Deferred] that completes with the result of [C]. */
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.asDeferred(): Deferred<T> =
    asDeferred(C::class)

/** Returns a [StateFlow] that emits `null` while [C] is pending, then the result once done. */
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.asStateFlow(): StateFlow<T?> =
    asStateFlow(C::class)

/** Marks initializer [C] as disabled so it is skipped on the next launch. */
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.disable() =
    disable(C::class)

/** Returns `true` if initializer [C] has been disabled via [FrameReady.disable]. */
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.isDisabled(): Boolean =
    isDisabled(C::class)

/** Re-runs a failed or completed initializer [C], replacing its previous result. */
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.retry() =
    retry(C::class)
