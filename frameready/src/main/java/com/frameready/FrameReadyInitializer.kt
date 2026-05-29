package com.frameready

import android.content.Context

/**
 * Declares the execution thread environment for a post-frame initializer.
 */
enum class ExecutionThread {
    /**
     * Executes on the Android Main (UI) thread.
     */
    MAIN,

    /**
     * Executes on a background thread (Dispatchers.IO).
     */
    BACKGROUND
}

/**
 * Interface representing a component that should be initialized after the first frame is drawn.
 */
interface FrameReadyInitializer<T> {
    /**
     * Initializes the component and returns the initialized instance.
     */
    fun create(context: Context): T

    /**
     * Declares the list of dependencies that must complete initialization before this initializer starts.
     */
    fun dependencies(): List<Class<out FrameReadyInitializer<*>>>

    /**
     * Declares the execution thread (defaults to BACKGROUND).
     */
    fun executionThread(): ExecutionThread = ExecutionThread.BACKGROUND
}

/**
 * Exception thrown when a circular dependency is detected during topological sorting.
 */
class CircularDependencyException(message: String) : RuntimeException(message)

/**
 * Exception thrown when an initializer does not finish within its allocated timeout limit.
 */
class InitializerTimeoutException(message: String) : RuntimeException(message)
