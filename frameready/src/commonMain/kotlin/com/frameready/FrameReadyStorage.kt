package com.frameready

/**
 * Persistence interface for FrameReady telemetry (TTFF history, cold-start rate, etc.).
 *
 * Implementations must be thread-safe. Provide a concrete class to [FrameReady.storage] before
 * the first frame is drawn to enable historical metric fields in [StartupMetrics].
 *
 * **Android**: use the built-in [SharedPreferencesFrameReadyStorage].
 * **iOS**: implement with `UserDefaults` or any key-value store.
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
