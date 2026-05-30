package com.example.demo

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.frameready.ExecutionThread
import com.frameready.FrameReadyInitializer
import kotlinx.coroutines.delay

// =========================================================================
// APPROACH 1: CLASSICAL APPLICATION CLASS ONCREATE (BLOCKING)
// =========================================================================
/**
 * Classical blocking initialization inside custom Application onCreate.
 * This runs directly on the Main thread before the first activity launch,
 * creating a 3-second black/white screen and significantly increasing Cold-Start TTFF.
 */
class SampleApplicationClassApproach : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("Benchmark", "Application.onCreate starts on ${Thread.currentThread().name}")
        
        // ❌ BLOCKS MAIN THREAD FOR 3 SECONDS
        // This stops the system from drawing any visual frames or installing Activity classes
        HeavyInitializer.initialize(this)
        
        Log.i("Benchmark", "Application.onCreate completes after blocking work.")
    }
}

// =========================================================================
// APPROACH 2: ANDROIDX APP STARTUP (SYNCHRONOUS COMPONENT)
// =========================================================================
/**
 * Standard AndroidX Startup Initializer.
 * While it provides a structured graph, App Startup runs synchronously during content provider
 * creation (onCreate), meaning any Main-Thread initializer blocks the visual render loop.
 */
class SampleAndroidXStartupApproach : Initializer<String> {
    
    override fun create(context: Context): String {
        Log.i("Benchmark", "AndroidX Startup begins on main thread...")
        
        // ❌ RUNS BEFORE FIRST FRAME -> BLOCKS MAIN RENDERING FOR 3 SECONDS
        HeavyInitializer.initialize(context)
        
        Log.i("Benchmark", "AndroidX Startup completed!")
        return "Heavy SDK Loaded"
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

// =========================================================================
// APPROACH 3: FRAMEREADY DYNAMIC RE-SCHEDULER (NON-BLOCKING)
// =========================================================================
/**
 * Post-First-Frame Deferred Initializer.
 * Renders the application's first layout instantly. Once Choreographer rasterizes 
 * the initial view, it hands computing threads over to complete background workloads safely.
 */
class SampleFrameReadyApproach : FrameReadyInitializer<String> {
    
    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> {
        return emptyList()
    }

    override fun executionThread(): ExecutionThread {
        // Run safely on background threads
        return ExecutionThread.BACKGROUND
    }

    override suspend fun create(context: Context): String {
        Log.i("Benchmark", "FrameReady starting 3-second non-blocking initialization on BACKGROUND thread...")
        
        // ✅ NON-BLOCKING BACKGROUND WORK (Keeps rendering thread fully fluid!)
        // Since we are running on backgrounds context, we execute heavy work asynchronously
        HeavyInitializer.initialize(context)
        
        Log.i("Benchmark", "FrameReady completed asynchronously after first frame!")
        return "Heavy SDK Asynchronously Initialized"
    }
}

// =========================================================================
// APPROACH 4: COOPERATIVE TIMEOUT & RUNINTERRUPTIBLE FOR BLOCKING SDKs
// =========================================================================
/**
 * Legacy/Third-Party SDK Initializer with Synchronous Thread Block.
 * If a dependency internally invokes non-suspendable blocking threads (e.g. Thread.sleep(5000))
 * or heavy synchronous network calls, FrameReady wraps executing coroutines inside
 * 'runInterruptible' paired with a custom timeout limit.
 * 
 * If execution exceeds the specified timeout, FrameReady safely interrupts the blocked thread,
 * avoiding background resource starvation and protecting the application from freezing.
 */
class SampleBlockingInterruptOnTimeoutApproach : FrameReadyInitializer<String> {
    
    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> {
        return emptyList()
    }

    override fun executionThread(): ExecutionThread {
        return ExecutionThread.BACKGROUND
    }

    // Declares an individual timeout limit of 2000ms (2 seconds)
    override fun timeoutMs(): Long {
        return 2000L
    }

    override suspend fun create(context: Context): String {
        Log.i("Benchmark", "FrameReady initiating legacy SDK known to block for 5 seconds...")
        
        // 🛡 EVEN THOUGH THIS SLEEP IS NON-SUSPENDABLE, FrameReady's runInterruptible
        // integration will safely interrupt this Thread.sleep at 2000ms, fail fast,
        // and keep the execution engine clean and unblocked!
        try {
            Thread.sleep(5000)
            Log.i("Benchmark", "Legacy SDK finished block on background thread (didn't time out).")
        } catch (e: InterruptedException) {
            Log.w("Benchmark", "Legacy SDK initialization successfully interrupted on timeout!")
            throw e
        }
        
        return "Legacy SDK Initialized"
    }
}
