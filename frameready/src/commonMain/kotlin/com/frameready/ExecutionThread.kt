package com.frameready

/** Declares the execution thread environment for a post-frame initializer. */
enum class ExecutionThread {
    /** Executes on the platform's main (UI) thread. */
    MAIN,
    /** Executes on a background thread (IO on Android, Default on iOS). */
    BACKGROUND
}

/** Thrown when a circular dependency is detected during topological sorting. */
class CircularDependencyException(message: String) : RuntimeException(message)

/** Thrown when an initializer does not finish within its allocated timeout. */
class InitializerTimeoutException(message: String) : RuntimeException(message)

/**
 * Thrown when an initializer was explicitly disabled via [FrameReady.disable] and a caller
 * attempts to await its result. Dependents of a disabled initializer receive this via cascade.
 */
class DisabledInitializerException(message: String) : RuntimeException(message)
