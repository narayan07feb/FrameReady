package com.frameready

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

/**
 * Reified Kotlin extensions for [FrameReady].
 *
 * These replace the `::class.java` calling convention with idiomatic Kotlin generics:
 *
 * ```kotlin
 * // Before
 * FrameReady.await(AnalyticsInitializer::class.java)
 * FrameReady.asDeferred(AnalyticsInitializer::class.java)
 *
 * // After
 * FrameReady.await<AnalyticsInitializer>()
 * FrameReady.asDeferred<AnalyticsInitializer>()
 * ```
 *
 * The type parameter `T` (result type) is automatically inferred from the `C` bound.
 * Both parameters must be specified explicitly only when the compiler cannot infer `T`
 * (e.g. `FrameReady.await<AnalyticsInitializer, Analytics>()`).
 */

/** Suspends until the result of [C] is available, then returns it. */
@Suppress("UNCHECKED_CAST")
suspend inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.await(
    timeoutMs: Long = 5000L
): T = await(C::class.java, timeoutMs)

/**
 * Returns the result of [C] if already complete, or `null` if still pending or failed.
 * Never suspends.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.getOrNull(): T? =
    getOrNull(C::class.java)

/**
 * Returns a read-only [Deferred] that completes with the result of [C].
 * Safe to call before [FrameReady.install] — the same instance is returned every time.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.asDeferred(): Deferred<T> =
    asDeferred(C::class.java)

/**
 * Returns a [StateFlow] that emits `null` while [C] is pending, then the result once done.
 * The same instance is returned on every call for a given [C] (cached).
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<T>, T> FrameReady.asStateFlow(): StateFlow<T?> =
    asStateFlow(C::class.java)

/** Marks initializer [C] as disabled so it is skipped on the next launch. */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.disable() =
    disable(C::class.java)

/** Returns `true` if initializer [C] has been disabled via [FrameReady.disable]. */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.isDisabled(): Boolean =
    isDisabled(C::class.java)

/**
 * Re-runs a failed or completed initializer [C], replacing its previous result.
 * Calling this on an initializer that is still in progress is a no-op.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified C : FrameReadyInitializer<*>> FrameReady.retry() =
    retry(C::class.java)
