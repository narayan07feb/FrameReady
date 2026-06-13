package com.example.samplestandard

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import com.frameready.StartupMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DatabaseInitializer : FrameReadyInitializer<String> {
    override suspend fun create(context: android.content.Context): String {
        delay(1200)
        return "SQLite_Local_V1_OK"
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
}

class ConfigInitializer : FrameReadyInitializer<Map<String, String>> {
    override suspend fun create(context: android.content.Context): Map<String, String> {
        delay(600)
        return mapOf("env" to "production", "logLevel" to "DEBUG")
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
}

class NetworkCacheInitializer : FrameReadyInitializer<String> {
    override suspend fun create(context: android.content.Context): String {
        val config = FrameReady.getOrNull(ConfigInitializer::class.java)
        delay(500)
        return "Cache Ready [Env: ${config?.get("env")}]"
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = listOf(
        DatabaseInitializer::class.java,
        ConfigInitializer::class.java
    )
}

class StandardMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FrameReady.stableThreshold = 1

        val initMode = intent.getStringExtra("INIT_MODE") ?: "frameready"

        // --- MACROBENCHMARK SIMULATION ---
        // If the macrobenchmark runner passes traditional or appstartup mode,
        // we deliberately block the main thread to simulate those architectures.
        if (initMode == "traditional" || initMode == "appstartup") {
            try {
                // Simulate 1.5 seconds of heavy synchronous initialization
                Thread.sleep(1500)
            } catch (e: Exception) {
                // Ignore
            }
        }
        lifecycleScope.launch {
            FrameReady.metricsFlow.collect {
                println("FrameReady Metrics: $it")
            }
        }
        val processStart = android.os.Process.getStartUptimeMillis()
        val current = android.os.SystemClock.uptimeMillis()
        val osOverhead = current - processStart

        Log.e(
            "Validation",
            "The OS spent ${osOverhead}ms booting up before Application.onCreate() even ran!"
        )
        setContent{
            StandardSampleScreen(initMode)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardSampleScreen(initMode: String) {
    var dbStatus by remember { mutableStateOf("Initializing Database... (Post-Frame)") }
    var configStatus by remember { mutableStateOf("Loading Config... (Post-Frame)") }
    var networkStatus by remember { mutableStateOf("Waiting for Dependencies... (Topological Sort)") }

    var isDbReady by remember { mutableStateOf(false) }
    var isConfigReady by remember { mutableStateOf(false) }
    var isNetworkReady by remember { mutableStateOf(false) }

    var benchmarkMetrics by remember { mutableStateOf<StartupMetrics?>(null) }

    LaunchedEffect(Unit) {
        launch {
            FrameReady.metricsFlow.collect { metrics ->
                benchmarkMetrics = metrics
            }
        }

        val startTime = System.currentTimeMillis()

        try {
            val dbResult = FrameReady.await(DatabaseInitializer::class.java)
            val dbDelay = System.currentTimeMillis() - startTime
            dbStatus = "Database: $dbResult (Complete: ${dbDelay}ms)"
            isDbReady = true
        } catch (e: Exception) {
            dbStatus = "Database Initializer Failed"
        }

        try {
            val configResult = FrameReady.await(ConfigInitializer::class.java)
            val configDelay = System.currentTimeMillis() - startTime
            configStatus = "Config: $configResult (Complete: ${configDelay}ms)"
            isConfigReady = true
        } catch (e: Exception) {
            configStatus = "Config Initializer Failed"
        }

        try {
            val netResult = FrameReady.await(NetworkCacheInitializer::class.java)
            val netDelay = System.currentTimeMillis() - startTime
            networkStatus = "Network: $netResult (Complete: ${netDelay}ms)"
            isNetworkReady = true
        } catch (e: Exception) {
            networkStatus = "Network Initializer Failed"
        }
    }

    val modeTitle = when (initMode) {
        "traditional" -> "Traditional (Blocked Main Thread)"
        "appstartup" -> "App Startup (Blocked Main Thread)"
        else -> "FrameReady (Deferred Concurrent)"
    }

    val modeColor = when (initMode) {
        "frameready" -> Color(0xFF81C784)
        else -> Color(0xFFE57373)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64B5F6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Comparative Benchmark", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF121212)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Active Mode Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = modeColor.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (initMode == "frameready") Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Mode Status",
                            tint = modeColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Active Benchmark Mode:",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Text(
                                modeTitle,
                                color = modeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Benchmark Telemetry Panel
                Text(
                    text = "Benchmark Telemetry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (benchmarkMetrics != null) {
                    val m = benchmarkMetrics!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF311B92)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "⚡ Cold Start Improvement: +${
                                    String.format(
                                        "%.1f",
                                        m.netImprovementRate
                                    )
                                }%",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB39DDB),
                                fontSize = 16.sp
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                            MetricRow(
                                "TTFF (Time to First Frame):",
                                "${m.ttffMs} ms",
                                Color(0xFF81C784)
                            )
                            MetricRow("P50 TTFF:", "${m.ttffP50} ms", Color.White)
                            MetricRow(
                                "Total Initializers Resolved:",
                                "${m.initializerCount}",
                                Color.White
                            )
                            MetricRow(
                                "Concurrent Background Work:",
                                "${m.initCompleteMs} ms",
                                Color(0xFFFFB74D)
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Calculating benchmarks...", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Initializer Status Panel
                Text(
                    text = "Topological Dependencies",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )

                // Status Cards
                InitializerCard("Database Status", isDbReady, dbStatus)
                InitializerCard("Config Status", isConfigReady, configStatus)
                InitializerCard("Network Cache", isNetworkReady, networkStatus)
            }
        }
    }
}

@Composable
fun InitializerCard(title: String, isReady: Boolean, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFF1B5E20) else Color(0xFF2E2E2E)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressOrCheck(isReady = isReady)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = status, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun CircularProgressOrCheck(isReady: Boolean) {
    if (isReady) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Ready",
            tint = Color(0xFF81C784),
            modifier = Modifier.size(24.dp)
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF64B5F6)
        )
    }
}
