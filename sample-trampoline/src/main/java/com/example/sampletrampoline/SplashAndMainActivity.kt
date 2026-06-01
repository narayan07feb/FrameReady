package com.example.sampletrampoline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

// 1. Splash (Trampoline) Activity
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Render a simple loading screen for 600ms to mimic a real splash delay,
        // then launch the real target Activity and call finish().
        setContent {
            SplashUI()
        }

        // Standard Handler splash redirection
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, TrampolineMainActivity::class.java)
            startActivity(intent)
            finish() // This finishes Splash, triggering FrameReady's trampoline bypass system
        }, 600)
    }
}

@Composable
fun SplashUI() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF1E1E1E)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1F2937)), // Warm tailwind slate color
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MY COOL BRAND",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF10B981))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Splash Trampoline Activity (fading out...)",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// 2. Heavy Post-Frame Initializer
class DelayedFeatureInitializer : FrameReadyInitializer<String> {
    override suspend fun create(context: android.content.Context): String {
        // Heavy local disk database / asset decompression task
        delay(1000)
        return "Core_Engine_Boot_Successfully"
    }

    override fun dependencies(): List<String> = emptyList()
}

// 3. Real Target Activity (after the Splash Trampoline)
class TrampolineMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrampolineSampleScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrampolineSampleScreen() {
    var coreStatus by remember { mutableStateOf("Core: Initializing... (Post-Frame of Main Activity)") }
    var isCoreReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val startAwait = System.currentTimeMillis()
        try {
            val result = FrameReady.await(DelayedFeatureInitializer::class.java)
            val delayText = System.currentTimeMillis() - startAwait
            coreStatus = "Feature Ready: $result (Complete: ${delayText}ms post-MainActivity frame)"
            isCoreReady = true
        } catch (e: Exception) {
            coreStatus = "Initializer Failed: ${e.message}"
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF34D399),
            background = Color(0xFF111827),
            surface = Color(0xFF1F2937)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FrameReady: Trampoline Bypassed", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1F2937),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF111827)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Card explaining Trampoline bypass
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Trampoline Bypass Info",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Successfully Skipped Splash Delay",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "The library detected that SplashActivity was finished and destroyed instantly, skipping frame trigger scheduling on Splash, and deferring execution until the real MainActivity rendered its first frame.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Initializer status list
                Text(
                    text = "App Initializer Sequence",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Start)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCoreReady) Color(0xFF065F46) else Color(0xFF374151)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCoreReady) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Done",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF34D399)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = coreStatus,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                // Execution Timeline logs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bypass Event Sequence Log",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "1. [0ms] SplashActivity resumes & triggers internal post-delayed safety gate.\n2. [600ms] SplashActivity starts MainActivity & calls finish().\n3. [650ms] FrameReady detects Splash is finished/destroyed and stops layout callbacks.\n4. [680ms] MainActivity starts and draws its first layout frame.\n5. [680ms] Post-frame trigger occurs instantly, starting DelayedFeatureInitializer!\n6. [1680ms] All systems are fully active.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
