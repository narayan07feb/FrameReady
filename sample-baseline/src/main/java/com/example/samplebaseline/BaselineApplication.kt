package com.example.samplebaseline

import android.app.Application
import android.util.Log

/**
 * True baseline: no FrameReady. Blocks Application.onCreate() for 1,500ms to simulate
 * a typical app loading a DB, config, and network cache synchronously on the main thread.
 */
class BaselineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val start = System.currentTimeMillis()
        // Simulate heavy synchronous initialization (DB migration + config + network cache)
        Thread.sleep(1_500)
        Log.i("BaselineApp", "Blocking init complete in ${System.currentTimeMillis() - start}ms")
    }
}
