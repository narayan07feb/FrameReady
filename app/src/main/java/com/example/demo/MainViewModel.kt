package com.example.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frameready.FrameReady
import com.frameready.StartupMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.os.SystemClock

data class UiState(
    val earlyAwaitStatus: String = "Not Started",
    val earlyAwaitTimeMs: Long = 0,
    val lateAwaitStatus: String = "Not Started",
    val lateAwaitTimeMs: Long = 0,
    val startupMetrics: StartupMetrics? = null,
    val metricsCallbackFired: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // 1. EARLY AWAIT DEMO (Rule 2) - Called before init has run
        testEarlyAwait()
        
        // 2. Metrics Hook Setup
        FrameReady.setMetricsListener { metrics ->
            _uiState.update { it.copy(startupMetrics = metrics, metricsCallbackFired = true) }
        }
    }

    private fun testEarlyAwait() {
        viewModelScope.launch {
            _uiState.update { it.copy(earlyAwaitStatus = "Waiting for A (Suspended)...") }
            val startTime = SystemClock.elapsedRealtime()
            
            // This will suspend until A completes
            val aResult = FrameReady.await(AInitializer::class.java)
            
            val duration = SystemClock.elapsedRealtime() - startTime
            _uiState.update { 
                it.copy(
                    earlyAwaitStatus = "Resumed! Result: \"$aResult\"",
                    earlyAwaitTimeMs = duration
                )
            }
        }
    }

    /**
     * 3. LATE AWAIT DEMO - Triggers await long after it has completed. Runs synchronously & returns instantly!
     */
    fun testLateAwait() {
        viewModelScope.launch {
            _uiState.update { it.copy(lateAwaitStatus = "Querying C...") }
            val startTime = SystemClock.elapsedRealtime()
            
            // This will return immediately because C is already done
            val cResult = FrameReady.await(CInitializer::class.java)
            
            val duration = SystemClock.elapsedRealtime() - startTime
            _uiState.update {
                it.copy(
                    lateAwaitStatus = "Completed Instantly! Result: \"$cResult\"",
                    lateAwaitTimeMs = duration
                )
            }
        }
    }
}
