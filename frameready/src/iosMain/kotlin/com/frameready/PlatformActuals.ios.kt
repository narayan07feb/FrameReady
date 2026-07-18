package com.frameready

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSLock
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import kotlin.reflect.KClass

/** Milliseconds since system boot — matches Android's `SystemClock.elapsedRealtime()` semantics. */
internal actual fun currentTimeMs(): Long = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

internal actual fun isMainThread(): Boolean = NSThread.isMainThread()

internal actual fun platformLog(tag: String, message: String, level: Char, throwable: Throwable?) {
    val prefix = when (level) {
        'E' -> "ERROR"
        'W' -> "WARN"
        else -> "INFO"
    }
    val suffix = throwable?.let { " — ${it.message ?: it}" } ?: ""
    NSLog("[$prefix/$tag] $message$suffix")
}

/**
 * Kotlin/Native cannot instantiate classes via reflection. Every initializer MUST be registered
 * with [FrameReady.registerFactory] before [FrameReady.install] runs.
 */
internal actual fun <T : Any> instantiateNoArg(clazz: KClass<T>): T {
    throw IllegalStateException(
        "${clazz.simpleName} cannot be instantiated via reflection on iOS. " +
        "Register a factory first: FrameReady.registerFactory(${clazz.simpleName}::class) { ${clazz.simpleName}() }"
    )
}

internal actual class PlatformLock actual constructor() {
    private val lock = NSLock()
    actual fun lock() { lock.lock() }
    actual fun unlock() { lock.unlock() }
}

/**
 * Kotlin/Native has no equivalent of JVM thread interruption, so unlike Android's actual, a
 * genuinely blocking (non-suspending) native call inside an initializer is not preemptible even
 * after the surrounding `withTimeout` fires. Ordinary suspend functions (delay, suspending I/O)
 * are cancelled cooperatively as usual.
 */
internal actual suspend fun <T> executeOnDispatcher(thread: ExecutionThread, block: suspend () -> T): T {
    val dispatcher = when (thread) {
        ExecutionThread.MAIN -> Dispatchers.Main
        ExecutionThread.BACKGROUND -> Dispatchers.Default
    }
    return withContext(dispatcher) { block() }
}
