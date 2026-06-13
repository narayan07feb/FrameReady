package com.example.samplestandard

import android.app.Application
import android.content.Context
import android.util.Log
import com.frameready.FrameReady
import com.frameready.FrameReadyStorage

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("SampleApplication", "Application onCreate: Initializing FrameReady BYOS")

        FrameReady.storage = object : FrameReadyStorage {
            val prefs = getSharedPreferences("my_app_telemetry", Context.MODE_PRIVATE)

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
    }
}
