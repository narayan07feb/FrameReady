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
 * True zero-library baseline benchmark.
 *
 * Targets :sample-baseline — a standalone app with NO FrameReady dependency that
 * simulates 1,500ms of blocking synchronous initialization in Application.onCreate().
 *
 * Install the baseline APK before running:
 *   ./gradlew :sample-baseline:installRelease
 *
 * Compare results against StartupBenchmark.benchmarkFrameReady to see the real
 * TTFF and peak-memory delta introduced by adopting FrameReady.
 */
@RunWith(AndroidJUnit4::class)
class BaselineBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

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
            intent.action = "android.intent.action.MAIN"
            intent.addCategory("android.intent.category.LAUNCHER")

            startActivityAndWait(intent)
        }
    }
}
