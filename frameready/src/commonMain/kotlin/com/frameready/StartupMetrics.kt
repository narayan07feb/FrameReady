package com.frameready

/** Startup metrics emitted by [FrameReady.metricsFlow] once all initializers complete. */
data class StartupMetrics(
    val ttffMs: Long,                       // Application.onCreate → first frame callback
    val initStartMs: Long,                  // first frame → first initializer started
    val initCompleteMs: Long,               // first frame → all initializers completed
    val trampolineSkipped: Boolean,         // whether a trampoline Activity was skipped (Android)
    val trampolineSkipCount: Int,           // number of skipped activities (Android)
    val initializerCount: Int,              // total count of executed initializers
    val stableLaunchCount: Int,             // consecutive stable launches achieved
    val ttffP50: Long,                      // P50 percentile for stable launches
    val ttffP90: Long,                      // P90 percentile for stable launches
    val ttffP99: Long,                      // P99 percentile for stable launches
    val netImprovementRate: Double,         // % cold-start improvement over baseline
    val coldStartRate: Double = 100.0,      // % of total launches that are cold starts
    val displayedMs: Long = 0L,             // process start → first draw (Android)
    val activityName: String = ""           // activity/screen that completed drawing
)
