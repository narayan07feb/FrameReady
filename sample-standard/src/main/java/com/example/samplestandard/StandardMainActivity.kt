package com.example.samplestandard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import kotlinx.coroutines.delay

class DatabaseInitializer : FrameReadyInitializer<String> {
    override suspend fun create(context: android.content.Context): String {
        // Simulate heavy SQLite index construction or migration
        delay(1200)
        return "SQLite_Local_V1_OK"
    }

    override fun dependencies(): List<String> = emptyList()
}

class ConfigInitializer : FrameReadyInitializer<Map<String, String>> {
    override suspend fun create(context: android.content.Context): Map<String, String> {
        // Simulate configuration parsing from local asset or encrypted keystore
        delay(600)
        return mapOf(
            "env" to "production",
            "logLevel" to "DEBUG"
        )
    }

    override fun dependencies(): List<String> = emptyList()
}

class StandardMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            StandardSampleScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardSampleScreen() {
    var dbStatus by remember { mutableStateOf("Initializing Database... (Post-Frame)") }
    var configStatus by remember { mutableStateOf("Loading Config... (Post-Frame)") }
    
    var isDbReady by remember { mutableStateOf(false) }
    var isConfigReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Measure exact execution delays
        val startTime = System.currentTimeMillis()
        
        try {
            val dbResult = FrameReady.await(DatabaseInitializer::class.java)
            val dbDelay = System.currentTimeMillis() - startTime
            dbStatus = "Database: $dbResult (Complete: ${dbDelay}ms)"
            isDbReady = true
        } catch (e: Exception) {
            dbStatus = "Database Initializer Failed: ${e.message}"
        }

        try {
            val configResult = FrameReady.await(ConfigInitializer::class.java)
            val configDelay = System.currentTimeMillis() - startTime
            configStatus = "Config: $configResult (Complete: ${configDelay}ms)"
            isConfigReady = true
        } catch (e: Exception) {
            configStatus = "Config Initializer Failed: ${e.message}"
        }
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
                    title = { Text("FrameReady: Standard Startup", fontWeight = FontWeight.Bold) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Immediate Draw Indicator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Standard Flow",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Immediate First Frame Draw",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Notice that this UI drawn instantly when the Activity launched, bypassing heavyweight initializer blocking on the main thread.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Initializer Status Panel
                Text(
                    text = "Asynchronous Post-Frame Initializers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Start)
                )

                // 1. Database Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDbReady) Color(0xFF1B5E20) else Color(0xFF2E2E2E)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressOrCheck(isReady = isDbReady)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = dbStatus,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // 2. Config Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConfigReady) Color(0xFF1B5E20) else Color(0xFF2E2E2E)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressOrCheck(isReady = isConfigReady)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = configStatus,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                // Core concepts description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How It Works Under the Hood",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81D4FA),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "1. App loads instantly presenting styled visual UI bones to user.\n2. Library waits for Choreographer post-frame callbacks.\n3. Upon drawing confirmation, Database and Config loading start concurrently on background coroutine contexts.\n4. Screen state transitions smoothly as tasks complete.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
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
