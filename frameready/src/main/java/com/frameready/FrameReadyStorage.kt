package com.frameready

/**
 * Interface that allows consumers to define how FrameReady telemetry should be persisted.
 * Because FrameReady intercepts metrics across multiple threads (Main thread and Background IO),
 * the implementation of this storage must be thread-safe.
 */
interface FrameReadyStorage {
    fun getStableLaunchCount(): Int
    fun setStableLaunchCount(count: Int)

    fun getTotalLaunchCount(): Int
    fun setTotalLaunchCount(count: Int)

    fun getColdLaunchCount(): Int
    fun setColdLaunchCount(count: Int)

    fun getTtffHistory(): List<Long>
    fun setTtffHistory(history: List<Long>)
}
