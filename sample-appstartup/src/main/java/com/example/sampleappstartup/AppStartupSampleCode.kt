package com.example.sampleappstartup

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.startup.Initializer
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Singleton to record Startup activities
object AppStartupNotifier {
    data class StartupToken(val sdkName: String, val timestamp: Long)

    val startupLogs = mutableStateListOf<String>()
    var token: StartupToken? = null

    fun log(msg: String) {
        startupLogs.add(msg)
    }
}

// 2. Direct Android Jetpack App Startup Initializer
class AppStartupInitializer : Initializer<AppStartupNotifier.StartupToken> {
    
    override fun create(context: Context): AppStartupNotifier.StartupToken {
        AppStartupNotifier.log("[App Startup] create() called on Main Thread.")
        val token = AppStartupNotifier.StartupToken("LocalConfigSdk", System.currentTimeMillis())
        AppStartupNotifier.token = token
        AppStartupNotifier.log("[App Startup] Micro-config loaded synchronously successfully!")
        return token
    }

    override fun dependencies(): List<Class<out Initializer<out Any>>> {
        // Enforce that FrameReady itself is initialized via App Startup first
        return listOf(FrameReadyAppInitializer::class.java)
    }
}

// 3. Post-Frame Initializer powered by FrameReady
class PostFrameTaskInitializer : FrameReadyInitializer<Unit> {
    
    override suspend fun create(context: Context) {
        AppStartupNotifier.log("[FrameReady] Post-Frame task initialized on Background Thread.")
        // Perform 2-second background network/cache warmups post draw
        delay(2000)
        AppStartupNotifier.log("[FrameReady] Post-Frame asynchronous services are fully warm!")
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
}

// 4. View Model coordinating statuses
class StartupViewModel : ViewModel() {
    private val _statusText = MutableStateFlow("Initializing screen state...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _postFrameComplete = MutableStateFlow(false)
    val postFrameComplete: StateFlow<Boolean> = _postFrameComplete.asStateFlow()

    init {
        viewModelScope.launch {
            // Wait for App Startup token to be ready (which is synchronous on launch anyway)
            if (AppStartupNotifier.token == null) {
                _statusText.value = "Awaiting Jetpack App Startup Token..."
                delay(100)
            }
            
            _statusText.value = "App Startup completed synchronously! App displayed immediately. Now waiting for Post-Frame pipeline..."
            
            // Wait for FrameReady initializer to complete
            FrameReady.await(PostFrameTaskInitializer::class.java)
            
            _statusText.value = "Post-Frame loading is completely finished! App is fully alive and responsive."
            _postFrameComplete.value = true
        }
    }
}

// 5. Activity to host UI
class StartupMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StartupSampleScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartupSampleScreen(viewModel: StartupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val statusText by viewModel.statusText.collectAsState()
    val postFrameComplete by viewModel.postFrameComplete.collectAsState()
    val logs = remember { AppStartupNotifier.startupLogs }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF80DEEA),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("App Startup & FrameReady", fontWeight = FontWeight.Bold) },
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00363A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Startup info",
                            tint = Color(0xFFB2EBF2),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Jetpack Startup Interop",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Jetpack App Startup is designed for synchronous, critical early components on the main thread. FrameReady steps in post-render to carry out asynchronous operations deferred from first frame, keeping the UI visually interactive instantly.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Current Token Status
                Text(
                    text = "Sync Handshake Token",
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Token ready",
                            tint = Color(0xFF80DEEA),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Sync SDK Loaded by Jetpack App Startup:",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "SdkName: ${AppStartupNotifier.token?.sdkName ?: "Loading..."}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Loaded At: ${AppStartupNotifier.token?.timestamp ?: "N/A"} ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                // Logs tracker
                Text(
                    text = "Phased Execution Audit Log",
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        logs.forEach { logLine ->
                            Text(
                                text = "• $logLine",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (logLine.contains("App Startup")) Color(0xFF81C784) else Color(0xFF64B5F6)
                            )
                        }
                    }
                }

                // Loader / Finished indicator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (postFrameComplete) Color(0xFF1B5E20) else Color(0xFF332F00)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!postFrameComplete) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF80DEEA),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Finished",
                                tint = Color(0xFF81C784)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
