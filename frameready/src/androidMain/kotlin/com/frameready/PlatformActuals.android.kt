package com.frameready

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass

internal actual fun currentTimeMs(): Long = SystemClock.elapsedRealtime()

internal actual fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

internal actual fun platformLog(tag: String, message: String, level: Char, throwable: Throwable?) {
    when (level) {
        'E' -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        'W' -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        else -> Log.i(tag, message)
    }
}

internal actual fun <T : Any> instantiateNoArg(clazz: KClass<T>): T =
    clazz.java.getDeclaredConstructor().newInstance()

internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}

/**
 * Preserves the pre-KMP behavior: a `BACKGROUND` initializer runs via `runInterruptible` on
 * `Dispatchers.IO` wrapping `runBlocking`, so a genuinely blocking call (`Thread.sleep`, blocking
 * I/O) is actually interrupted when the surrounding `withTimeout` in the shared engine cancels.
 */
internal actual suspend fun <T> executeOnDispatcher(thread: ExecutionThread, block: suspend () -> T): T {
    return when (thread) {
        ExecutionThread.MAIN -> withContext(Dispatchers.Main) { block() }
        ExecutionThread.BACKGROUND -> runInterruptible(Dispatchers.IO) { runBlocking { block() } }
    }
}
