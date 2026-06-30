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
 * Library-overhead benchmarks comparing a zero-library baseline against FrameReady
 * installed with 0 initializers (cold-start tracking only).
 *
 * Both apps block Application.onCreate() for 1,500ms with identical workloads.
 * The only variable is whether FrameReady's ContentProvider is present.
 * The TTFF delta between the two tests = pure library overhead.
 *
 * Install both APKs before running:
 *   ./gradlew :sample-baseline:installDebug :sample-metrics-only:installDebug
 */
@RunWith(AndroidJUnit4::class)
class BaselineBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** No library. Cold-start TTFF with 1,500ms blocking Application.onCreate(). */
    @Test
    fun benchmarkNoLibraryTraditional() {
        benchmarkRule.measureRepeated(
            packageName = "com.frameready.sample.baseline",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            val intent = Intent()
            intent.setClassName(
                "com.frameready.sample.baseline",
                "com.example.samplebaseline.BaselineMainActivity"
            )
            startActivityAndWait(intent)
        }
    }

    /**
     * FrameReady with 0 initializers — cold-start rate tracking only.
     * Same 1,500ms blocking onCreate() as benchmarkNoLibraryTraditional.
     * TTFF delta vs above = ContentProvider + lifecycle-callback overhead only.
     */
    @Test
    fun benchmarkMetricsOnly() {
        benchmarkRule.measureRepeated(
            packageName = "com.frameready.sample.metricsonly",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            val intent = Intent()
            intent.setClassName(
                "com.frameready.sample.metricsonly",
                "com.example.samplemetricsonly.MetricsOnlyMainActivity"
            )
            startActivityAndWait(intent)
        }
    }
}
