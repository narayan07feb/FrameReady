package com.example.benchmark

import android.content.Intent
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Apples-to-apples comparison: traditional blocking Application.onCreate() init
 * vs FrameReady parallel post-frame init — using the EXACT same 8 SDKs with
 * identical delays in both apps.
 *
 * SDK work (shared):
 *   Analytics     800ms   no deps
 *   CrashReporter 400ms   no deps
 *   ImageLoader   300ms   no deps
 *   Database     1200ms   no deps
 *   Config        600ms   no deps
 *   FeatureFlags  600ms   → Analytics
 *   PushNotif     500ms   → CrashReporter
 *   NetworkCache  500ms   → Database + Config
 *
 * Traditional sequential sum  = 4900ms
 * FrameReady parallel critical = DB(1200ms) → NetworkCache(500ms) = 1700ms
 *
 * Expected benchmark output:
 * ┌──────────────────────────────┬────────────┬────────────┬──────────────────┐
 * │ Test                         │ TTID       │ TTFD       │ Immediate wait   │
 * ├──────────────────────────────┼────────────┼────────────┼──────────────────┤
 * │ appClassSequentialInit       │ ~4900ms    │ ~4900ms    │ 0ms (ready now)  │
 * │ frameReadyParallelInit       │ ~100ms     │ ~1800ms    │ ~1700ms max      │
 * ├──────────────────────────────┼────────────┼────────────┼──────────────────┤
 * │ FrameReady improvement       │ -4800ms    │ -3100ms    │                  │
 * └──────────────────────────────┴────────────┴────────────┴──────────────────┘
 *
 * Key insight: even counting the "access wait time" for a caller that hits await()
 * immediately at first frame, FrameReady's TTFD (1800ms) is still 3100ms faster
 * than traditional's TTID (4900ms). The user-perceived total time to a usable app
 * is 2.7× faster with FrameReady.
 *
 * Install before running:
 *   ./gradlew :sample-appcls-init:installDebug :sample-standard:installDebug
 *
 * NOTE: appClassSequentialInit takes ~5 seconds per cold-start iteration.
 * 3 iterations = ~15s for that test alone. This is expected and intentional —
 * the long duration IS the point: it's how long users wait with the traditional approach.
 */
@RunWith(AndroidJUnit4::class)
class SyncVsFrameReadyBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Traditional: 8 SDKs initialized sequentially in Application.onCreate().
     *
     * Metrics to watch:
     *   timeToInitialDisplayMs ≈ 4900ms  — user sees blank screen until this point
     *   timeToFullDisplayMs    ≈ 4900ms  — same as TTID; deps available immediately, 0ms wait
     *
     * The fact that TTID ≈ TTFD here is the problem: the user paid the full init cost
     * upfront, before seeing anything.
     */
    @Test
    fun appClassSequentialInit() = benchmarkRule.measureRepeated(
        packageName = "com.frameready.sample.appcls",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait(Intent().apply {
            setClassName(
                "com.frameready.sample.appcls",
                "com.example.sampleappcls.SyncInitActivity"
            )
        })
    }

    /**
     * FrameReady: same 8 SDKs run in parallel post-first-frame.
     *
     * Metrics to watch:
     *   timeToInitialDisplayMs ≈ 100ms   — user sees content almost instantly
     *   timeToFullDisplayMs    ≈ 1800ms  — parallel critical path: DB(1200ms)→NetworkCache(500ms)
     *
     * The gap (TTFD − TTID ≈ 1700ms) is the "immediate access wait" — the maximum
     * time a coroutine calling FrameReady.await() right at first frame will suspend.
     * If the user naturally takes >1700ms to reach the feature (e.g. reading the UI,
     * navigating), the wait is already 0ms.
     *
     * Total time to fully usable state: 1800ms vs 4900ms — 2.7× faster end-to-end.
     */
    @Test
    fun frameReadyParallelInit() = benchmarkRule.measureRepeated(
        packageName = "com.frameready.sample.standard",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait(Intent().apply {
            setClassName(
                "com.frameready.sample.standard",
                "com.example.samplestandard.StandardMainActivity"
            )
        })
    }
}
