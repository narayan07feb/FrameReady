package com.example.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameready.FrameReady
import com.frameready.StartupMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

data class UiState(
    val earlyAwaitStatus: String = "Not Started",
    val earlyAwaitTimeMs: Long = 0,
    val lateAwaitStatus: String = "Not Started",
    val lateAwaitTimeMs: Long = 0,
    val startupMetrics: StartupMetrics? = null,
    val metricsCallbackFired: Boolean = false,
    val resetMessage: String = "",

    // Interactive Benchmark Sim states
    val isSimulating: Boolean = false,
    val simProgress: Float = 0f,
    val simCurrentStep: String = "",
    val simAppClassTtff: Long = 0,
    val simAndroidXTtff: Long = 0,
    val simFrameReadyTtff: Long = 0,
    val activeApproachIndex: Int = -1 // -1: Not simulated, 0: App Class, 1: AndroidX Startup, 2: FrameReady
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            FrameReady.metricsFlow.collect { metrics ->
                _uiState.update { it.copy(startupMetrics = metrics, metricsCallbackFired = true) }
            }
        }
    }

    fun testEarlyAwait() {
        viewModelScope.launch {
            _uiState.update { it.copy(earlyAwaitStatus = "Waiting for A (Suspended)...") }
            val startTime = TimeSource.Monotonic.markNow()

            try {
                // This will suspend until A completes
                val aResult = FrameReady.await(AInitializer::class)
                val duration = startTime.elapsedNow().inWholeMilliseconds
                _uiState.update {
                    it.copy(
                        earlyAwaitStatus = "Resumed! Result: \"$aResult\"",
                        earlyAwaitTimeMs = duration
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        earlyAwaitStatus = "Failed: ${e.message}",
                        earlyAwaitTimeMs = 0
                    )
                }
            }
        }
    }

    /**
     * 3. LATE AWAIT DEMO - Triggers await long after it has completed. Runs synchronously & returns instantly!
     */
    fun testLateAwait() {
        viewModelScope.launch {
            _uiState.update { it.copy(lateAwaitStatus = "Querying C...") }
            val startTime = TimeSource.Monotonic.markNow()

            try {
                // This will return immediately because C is already done
                val cResult = FrameReady.await(CInitializer::class)
                val duration = startTime.elapsedNow().inWholeMilliseconds
                _uiState.update {
                    it.copy(
                        lateAwaitStatus = "Completed Instantly! Result: \"$cResult\"",
                        lateAwaitTimeMs = duration
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        lateAwaitStatus = "Query failed: ${e.message}",
                        lateAwaitTimeMs = 0
                    )
                }
            }
        }
    }

    /**
     * 4. ADVANCED BENCHMARK SIMULATION RUNNER
     * Animates and simulates live thread behaviors for the three approaches side by side.
     */
    fun runBenchmarkSimulation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSimulating = true,
                    simProgress = 0f,
                    simCurrentStep = "Preparing comparison sandbox configuration...",
                    activeApproachIndex = 0,
                    simAppClassTtff = 0,
                    simAndroidXTtff = 0,
                    simFrameReadyTtff = 0
                )
            }

            // App class delay
            kotlinx.coroutines.delay(800)
            _uiState.update {
                it.copy(
                    simCurrentStep = "Application.onCreate() started. Invoking heavy synchronized initializers on the Main thread...",
                    simProgress = 0.1f
                )
            }

            // Simulating a 3 second frozen Main Thread
            for (i in 1..20) {
                kotlinx.coroutines.delay(100)
                _uiState.update {
                    it.copy(
                        simProgress = 0.1f + (i * 0.015f)
                    )
                }
            }

            _uiState.update {
                it.copy(
                    simCurrentStep = "Main thread finally escapes Thread.sleep(). First layout painted to screen.",
                    simAppClassTtff = 3120,
                    simProgress = 0.45f,
                    activeApproachIndex = 1
                )
            }

            // App Start delay
            kotlinx.coroutines.delay(1200)
            _uiState.update {
                it.copy(
                    simCurrentStep = "AndroidX App Startup Initializer.create() loaded. Holding visual rendering queue...",
                    simProgress = 0.55f
                )
            }

            for (i in 1..20) {
                kotlinx.coroutines.delay(100)
                _uiState.update {
                    it.copy(
                        simProgress = 0.55f + (i * 0.015f)
                    )
                }
            }

            _uiState.update {
                it.copy(
                    simCurrentStep = "Initializer completes. Visual content provider installs completed.",
                    simAndroidXTtff = 3050,
                    simProgress = 0.85f,
                    activeApproachIndex = 2
                )
            }

            // FrameReady delay
            kotlinx.coroutines.delay(1200)
            _uiState.update {
                it.copy(
                    simCurrentStep = "FrameReady installs. Renders first visual frame instantly, then schedules background pool...",
                    simProgress = 0.92f
                )
            }
            kotlinx.coroutines.delay(500)
            _uiState.update {
                it.copy(
                    simCurrentStep = "Choreographer rasterization completes in 180ms! Fluid UI available immediately.",
                    simFrameReadyTtff = 182,
                    simProgress = 1.0f,
                    isSimulating = false
                )
            }
        }
    }

    /**
     * Resets the shared engine's consecutive-stable-launch counter. Portable across platforms —
     * the original Android-only version reached into `SharedPreferences` directly, but
     * `FrameReady.storage` was never wired up in this demo app, so that direct clear was already a
     * no-op on real metrics history. This calls the equivalent shared-engine API instead.
     */
    fun resetStabilityRecords() {
        FrameReady.resetStability()
        _uiState.update { it.copy(resetMessage = "Stability records reset. Relaunch app to start count over.") }
    }

    override fun onCleared() {
        super.onCleared()
        // SharedFlow safely prevents memory leaks, no cleanup necessary
    }
}
