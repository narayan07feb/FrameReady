package com.frameready

import android.content.Context
import android.content.SharedPreferences

/**
 * A [FrameReadyStorage] implementation backed by [SharedPreferences].
 *
 * Drop this in to enable metrics tracking without writing any boilerplate:
 *
 * ```kotlin
 * // Application.onCreate()
 * FrameReady.storage = SharedPreferencesFrameReadyStorage(this)
 * ```
 *
 * Data is stored in a private file named `"frameready_metrics"`. If you need
 * to share storage across processes or migrate from a custom implementation,
 * pass a different [SharedPreferences] instance via the secondary constructor:
 *
 * ```kotlin
 * FrameReady.storage = SharedPreferencesFrameReadyStorage(
 *     context.getSharedPreferences("my_custom_prefs", Context.MODE_PRIVATE)
 * )
 * ```
 *
 * TTFF history is serialized as a comma-separated string of `Long` values.
 * The list is capped at 100 entries to bound storage growth.
 */
class SharedPreferencesFrameReadyStorage(prefs: SharedPreferences) : FrameReadyStorage {

    constructor(context: Context) : this(
        context.getSharedPreferences("frameready_metrics", Context.MODE_PRIVATE)
    )

    private val p = prefs

    override fun getStableLaunchCount(): Int = p.getInt(KEY_STABLE, 0)
    override fun setStableLaunchCount(count: Int) { p.edit().putInt(KEY_STABLE, count).apply() }

    override fun getTotalLaunchCount(): Int = p.getInt(KEY_TOTAL, 0)
    override fun setTotalLaunchCount(count: Int) { p.edit().putInt(KEY_TOTAL, count).apply() }

    override fun getColdLaunchCount(): Int = p.getInt(KEY_COLD, 0)
    override fun setColdLaunchCount(count: Int) { p.edit().putInt(KEY_COLD, count).apply() }

    override fun getTtffHistory(): List<Long> {
        val raw = p.getString(KEY_TTFF, null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    override fun setTtffHistory(history: List<Long>) {
        val capped = if (history.size > MAX_HISTORY) history.takeLast(MAX_HISTORY) else history
        p.edit().putString(KEY_TTFF, capped.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_STABLE = "stable_count"
        const val KEY_TOTAL  = "total_count"
        const val KEY_COLD   = "cold_count"
        const val KEY_TTFF   = "ttff_history"
        const val MAX_HISTORY = 100
    }
}
