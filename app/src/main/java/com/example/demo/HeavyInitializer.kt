package com.example.demo

import android.content.Context
import android.util.Log

/**
 * A representative heavyweight third-party SDK or database initializer.
 * In a real application, this could represent Firebase, AdMob, database migrations,
 * dependency injection graph setup, or analytical processors that require substantial execution budget.
 */
object HeavyInitializer {
    private const val TAG = "HeavyInitializer"
    
    // Status tracking for interactive rendering
    var isInitialized = false
        private set
        
    var initializationTimeMs: Long = 0
        private set

    /**
     * Executes the heavy synchronized initializations.
     * @param context App context
     * @param blockDurationMs How long we simulate standard main-thread block (defaults to 3000ms / 3 seconds of load)
     */
    fun initialize(context: Context, blockDurationMs: Long = 3000L) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, ">>>>>>>> STARTING HEAVY INITIALIZATION ON THE THREAD: ${Thread.currentThread().name} <<<<<<<<")
        
        try {
            // Strictly blocks the thread representing intensive synchronous computations or file I/O
            Thread.sleep(blockDurationMs)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Initialization interrupted!", e)
        }
        
        isInitialized = true
        initializationTimeMs = System.currentTimeMillis() - startTime
        Log.i(TAG, ">>>>>>>> COMPLETED HEAVY INITIALIZATION IN $initializationTimeMs ms ON THREAD: ${Thread.currentThread().name} <<<<<<<<")
    }

    /**
     * Reset state for successive demo run testing
     */
    fun reset() {
        isInitialized = false
        initializationTimeMs = 0
    }
}
