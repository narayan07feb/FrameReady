package com.example.benchmark

import android.content.Intent
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun benchmarkTraditionalStartup() = runBenchmarkWithMode("traditional")

    @Test
    fun benchmarkAppStartupLibrary() = runBenchmarkWithMode("appstartup")

    @Test
    fun benchmarkFrameReady() = runBenchmarkWithMode("frameready")

    private fun runBenchmarkWithMode(mode: String) {
        benchmarkRule.measureRepeated(
            packageName = "com.aistudio.frsample.standard.pndmsd",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            
            val intent = Intent()
            intent.setPackage("com.aistudio.frsample.standard.pndmsd")
            intent.action = "android.intent.action.MAIN"
            intent.addCategory("android.intent.category.LAUNCHER")
            intent.putExtra("INIT_MODE", mode)
            
            startActivityAndWait(intent)
        }
    }
}
