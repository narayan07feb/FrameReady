package com.example.samplemetricsonly

import android.app.Application
import android.content.Context
import android.util.Log
import com.frameready.FrameReady
import com.frameready.FrameReadyStorage

/**
 * FrameReady is installed with 0 initializers — only cold-start rate tracking is active.
 * The 1,500ms blocking sleep matches sample-baseline so the benchmark delta isolates
 * the library's own overhead (ContentProvider + lifecycle callbacks).
 */
class MetricsOnlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Wire up persistent cold-start tracking via FrameReady's BYOS interface.
        // No initializers are registered — only launch counters and TTFF are recorded.
        FrameReady.storage = object : FrameReadyStorage {
            val prefs = getSharedPreferences("metrics_only_telemetry", Context.MODE_PRIVATE)

            override fun getStableLaunchCount() = prefs.getInt("stable", 0)
            override fun setStableLaunchCount(count: Int) {
                prefs.edit().putInt("stable", count).apply()
            }

            override fun getTotalLaunchCount() = prefs.getInt("total", 0)
            override fun setTotalLaunchCount(count: Int) {
                prefs.edit().putInt("total", count).apply()
            }

            override fun getColdLaunchCount() = prefs.getInt("cold", 0)
            override fun setColdLaunchCount(count: Int) {
                prefs.edit().putInt("cold", count).apply()
            }

            override fun getTtffHistory(): List<Long> {
                val str = prefs.getString("history", "") ?: ""
                if (str.isEmpty()) return emptyList()
                return str.split(",").mapNotNull { it.toLongOrNull() }
            }

            override fun setTtffHistory(history: List<Long>) {
                prefs.edit().putString("history", history.joinToString(",")).apply()
            }
        }

        // Same 1,500ms blocking sleep as sample-baseline so the benchmark compares
        // equal app workloads. Only the library presence differs.
        val start = System.currentTimeMillis()
        Thread.sleep(1_500)
        Log.i("MetricsOnlyApp", "Blocking init complete in ${System.currentTimeMillis() - start}ms")
    }
}
