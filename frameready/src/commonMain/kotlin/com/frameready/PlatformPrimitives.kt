package com.frameready

import kotlin.reflect.KClass

/**
 * Runs [block] on the dispatcher appropriate for [thread].
 *
 * - **Android**: `BACKGROUND` uses `runInterruptible(Dispatchers.IO)` wrapping `runBlocking` so a
 *   genuinely blocking call inside an initializer (e.g. `Thread.sleep`) is actually interrupted
 *   when the surrounding `withTimeout` cancels — plain cooperative cancellation would otherwise
 *   let the blocking call run to completion regardless of the timeout.
 * - **iOS**: `BACKGROUND` uses `Dispatchers.Default` with ordinary cooperative cancellation.
 *   Kotlin/Native has no equivalent of JVM thread interruption; a genuinely blocking (non-suspending)
 *   native call inside an initializer will not be preemptible even after `withTimeout` fires.
 */
internal expect suspend fun <T> executeOnDispatcher(thread: ExecutionThread, block: suspend () -> T): T

/**
 * The small set of platform primitives the shared [FrameReady] engine depends on.
 * Every actual implementation MUST be genuinely thread-safe — the engine runs initializers
 * concurrently via [kotlinx.coroutines.Dispatchers.Default].
 */

/** Monotonic milliseconds since device/process boot. */
internal expect fun currentTimeMs(): Long

/** True when called from the platform's main/UI thread. */
internal expect fun isMainThread(): Boolean

/** `'I'` info, `'W'` warn, `'E'` error. */
internal expect fun platformLog(tag: String, message: String, level: Char, throwable: Throwable? = null)

/**
 * Instantiates [clazz] via its zero-arg constructor.
 * - **Android**: JVM reflection.
 * - **iOS**: always throws — Kotlin/Native has no reflective no-arg instantiation. Use
 *   [FrameReady.registerFactory] instead.
 */
internal expect fun <T : Any> instantiateNoArg(clazz: KClass<T>): T

/** A platform mutual-exclusion lock. Reentrant is not required. */
internal expect class PlatformLock() {
    fun lock()
    fun unlock()
}

internal inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
