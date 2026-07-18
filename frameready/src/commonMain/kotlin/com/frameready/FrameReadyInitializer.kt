package com.frameready

import kotlin.reflect.KClass

/**
 * Implement this interface for each SDK or component you want to initialize post-first-frame.
 *
 * ```kotlin
 * class AnalyticsInitializer : FrameReadyInitializer<Analytics> {
 *     override suspend fun create(context: PlatformContext): Analytics {
 *         delay(800)
 *         return Analytics.init(context)
 *     }
 *     override fun dependencies() = listOf(CrashReporterInitializer::class)
 * }
 * ```
 *
 * **iOS**: Kotlin/Native cannot instantiate classes via reflection. Register a factory for every
 * initializer with [FrameReady.registerFactory] before calling [FrameReady.install].
 */
interface FrameReadyInitializer<T> {

    /** Initializes the component and returns the initialized instance. */
    suspend fun create(context: PlatformContext): T

    /**
     * Declares the list of initializers that must complete before this one starts.
     * Use `::class` references (e.g. `listOf(OtherInit::class)`).
     */
    fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()

    /** Declares the execution thread (defaults to [ExecutionThread.BACKGROUND]). */
    fun executionThread(): ExecutionThread = ExecutionThread.BACKGROUND

    /** Timeout in milliseconds. Defaults to no timeout ([Long.MAX_VALUE]). */
    fun timeoutMs(): Long = Long.MAX_VALUE
}
