package com.example.samplenotification

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
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

// 1. Heavy Post-Frame Initializer simulating instant routing work
class NotificationFastInitializer : FrameReadyInitializer<String> {
    override suspend fun create(context: android.content.Context): String {
        delay(800)
        return "Route_Telemetry_Active"
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
}

// 2. Main Launcher Dashboard
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DashboardScreen(
                onLaunchStandard = {
                    val intent = Intent(this, NotificationTargetActivity::class.java)
                    startActivity(intent)
                },
                onLaunchNotification = {
                    val intent = Intent(this, NotificationTargetActivity::class.java).apply {
                        putExtra("from_notification", true) // Bypasses initial-delay safety gate completely
                        putExtra("notification_id", 3077)
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onLaunchStandard: () -> Unit, onLaunchNotification: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF60A5FA),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FrameReady: Launch Options", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Info Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Direct Launch vs. Notification Bypass",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Ordinarily, FrameReady schedules checks using Choreographer and handler posts to ignore blank/trampoline/splash screens. However, notification intents direct users to deep screens directly. We bypass the delayed evaluation and fire initializers instantly on resume.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Launch Option 1
                Button(
                    onClick = onLaunchStandard,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("1. Standard Direct Target Cold Start", fontWeight = FontWeight.Bold)
                }

                // Launch Option 2
                Button(
                    onClick = onLaunchNotification,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Bell")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Simulate Notification Launch (Bypass Active)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1.0f))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2736))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How to test",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Option 1 runs the standard post-frame dispatcher.\nOption 2 uses intents holding notification extras, causing the background task engine to skip safety delays and trigger on resume instantly.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

// 3. Destination Details Screen
class NotificationTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Detect heuristic
        val hasNotificationExtra = intent?.hasExtra("from_notification") == true ||
                                   intent?.hasExtra("notification_id") == true
        
        setContent {
            TargetScreen(isNotificationOriginated = hasNotificationExtra)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetScreen(isNotificationOriginated: Boolean) {
    var statusText by remember { mutableStateOf("Initializing... (Post-Frame)") }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        try {
            val result = FrameReady.await(NotificationFastInitializer::class.java)
            val duration = System.currentTimeMillis() - startTime
            statusText = "Telemetry: $result (Complete in ${duration}ms)"
            isReady = true
        } catch (e: Exception) {
            statusText = "Failed: ${e.message}"
        }
    }

    val themeColor = if (isNotificationOriginated) Color(0xFF2563EB) else Color(0xFF334155)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = themeColor,
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notification Destination", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF0F172A)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Heuristic Classification Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = themeColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Classification",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (isNotificationOriginated) "Notification Classification: ACTIVE" else "Standard Cold Start Flow",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isNotificationOriginated) 
                                    "Bypassed safety timing gates and triggered post-resume initializers instantly." 
                                    else "Using regular Post-Frame Choreographer dispatch loops.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Initializer state indicator
                Text(
                    text = "System Initializer Task Status",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isReady) Color(0xFF1E293B) else Color(0xFF1A2235)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isReady) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Active",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF60A5FA)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                // Summary sequence description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Heuristics Utilized by FrameReady",
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• Intent has 'notification_id' extra\n• Intent has 'from_notification' extra\n• Intent action string contains the word 'NOTIFICATION'\nAll three checks run inside a try-catch blocks to guarantee safety against BadParcelableExceptions.",
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
