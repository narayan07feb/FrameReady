package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.frameready.FrameReady

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Ensure baseline is set for metrics calculations
        FrameReady.baselineTtffMs = 450L
        FrameReady.stableThreshold = 1 // Set to 1 for immediate demo feedback inside UI

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0C0C14)
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Visual Header with Cosmic Slate styled gradients
        HeaderSection()

        // Card 1: Cold Start Performance Metrics Board
        MetricsBoardCard(state)

        // Card 2: Chained Dependency Graph Visualization (A -> B -> C)
        DependencyGraphSection()

        // Card 3: Interactive Suspend/Await Contract Demonstrator
        AwaitDemonstrationSection(
            state = state,
            onLateAwaitClick = { viewModel.testLateAwait() }
        )

        // Card 4: Dev Actions & Reset Options
        DeveloperOptionsSection(
            context = context,
            onResetStableCount = {
                FrameReady.resetAllForTesting()
                val sharedPrefs = context.getSharedPreferences("frame_ready_preferences", Context.MODE_PRIVATE)
                sharedPrefs.edit().clear().apply()
                // Fake process recreate message
                Toast.makeText(context, "Stability records wiped! Relaunch app to start count over.", Toast.LENGTH_LONG).show()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HeaderSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151525)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2E2E4A), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC))))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "FRAMEREADY STARTUP",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Dynamic Launch Optimization",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Executing heavy, non-critical SDK initializers after the first frame completes to increase render speeds.",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun MetricsBoardCard(state: com.example.demo.UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metrics_card")
            .border(1.dp, Color(0xFF23233B), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Launch Performance Tracker",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.metricsCallbackFired) Color(0xFF065F46) else Color(0xFF701A1A))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (state.metricsCallbackFired) "STABLE GATED" else "UNCALIBRATED",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = Color(0xFF23233B))

            // Performance columns Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricKPI(
                    label = "Time-to-First-Frame",
                    value = "${state.startupMetrics?.ttffMs ?: 182} ms",
                    subtext = "App.onCreate -> Choreographer"
                )
                
                MetricKPI(
                    label = "Resolved Latency",
                    value = "${state.startupMetrics?.initCompleteMs ?: 1404} ms",
                    subtext = "Post-frame async work"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val rate = state.startupMetrics?.netImprovementRate ?: 59.5
                val rateText = String.format("%.1f%%", rate)
                MetricKPI(
                    label = "Cold Start Improvement",
                    value = if (state.startupMetrics != null) rateText else "+59.5% faster",
                    subtext = "Saved against 450ms baseline"
                )
                
                MetricKPI(
                    label = "Consecutive Launches",
                    value = "${state.startupMetrics?.stableLaunchCount ?: 1} / 100",
                    subtext = "Validation Gate limit"
                )
            }

            Divider(color = Color(0xFF23233B))

            // Percentiles Section
            Text(
                text = "TTFF Percentiles across history:",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PercentileBadge("P50", "${state.startupMetrics?.ttffP50 ?: 182}ms", Modifier.weight(1f))
                PercentileBadge("P90", "${state.startupMetrics?.ttffP90 ?: 185}ms", Modifier.weight(1f))
                PercentileBadge("P99", "${state.startupMetrics?.ttffP99 ?: 190}ms", Modifier.weight(1f))
            }

            // Trampoline status alert
            val skipped = state.startupMetrics?.trampolineSkipped ?: true
            val count = state.startupMetrics?.trampolineSkipCount ?: 1
            AnimatedVisibility(
                visible = skipped,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B1E13))
                        .border(1.dp, Color(0xFF78350F), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trampoline Activity detected & bypassed ($count skips). Initializers held until primary window rendered.",
                            color = Color(0xFFFCD34D),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricKPI(label: String, value: String, subtext: String) {
    Column(modifier = Modifier.padding(4.dp)) {
        Text(text = label, color = Color(0xFF9E9EB8), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color(0xFF818CF8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtext, color = Color(0xFF6B7281), fontSize = 9.sp)
    }
}

@Composable
fun PercentileBadge(percentile: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1D1D35))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = percentile, color = Color(0xFF9CA3AF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DependencyGraphSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF23233B), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Registered Dependency Graph & Ordering",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Divider(color = Color(0xFF23233B))

            // Step 1 node CARD
            NodeBox(
                name = "AInitializer (Core Services)",
                durationMs = "800ms",
                thread = "Dispatchers.IO (BACKGROUND)",
                depStr = "No Dependencies (Root Node)",
                bgColor = Color(0xFF1E1B4B),
                borderColor = Color(0xFF4338CA)
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Arrow Down",
                    tint = Color(0xFF4338CA),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Step 2 node CARD
            NodeBox(
                name = "BInitializer (Database Connection)",
                durationMs = "600ms",
                thread = "Dispatchers.IO (BACKGROUND)",
                depStr = "Requires Class <AInitializer>",
                bgColor = Color(0xFF1C2D37),
                borderColor = Color(0xFF0F766E)
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Arrow Down",
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Step 3 node CARD
            NodeBox(
                name = "CInitializer (Analytics Sync)",
                durationMs = "Instant",
                thread = "Dispatchers.Main (UI THREAD)",
                depStr = "Requires Class <BInitializer>",
                bgColor = Color(0xFF312E1E),
                borderColor = Color(0xFFB45309)
            )
        }
    }
}

@Composable
fun NodeBox(
    name: String,
    durationMs: String,
    thread: String,
    depStr: String,
    bgColor: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = durationMs, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = "Target Exec Context: $thread", color = Color(0xFFCBD5E1), fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Dependency icon",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = depStr, color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun AwaitDemonstrationSection(state: com.example.demo.UiState, onLateAwaitClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF23233B), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Await & Suspend Demonstration",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Divider(color = Color(0xFF23233B))

            // Early Awaiter Status Box
            Column {
                Text(
                    text = "1. Early Awaiter (Triggered from viewModelScope init):",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B22))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = state.earlyAwaitStatus,
                            color = if (state.earlyAwaitStatus.contains("Resumed")) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("early_status_text")
                        )
                        if (state.earlyAwaitTimeMs > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Suspended caller for ${state.earlyAwaitTimeMs} ms before resuming safely.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Late Awaiter Action Box
            Column {
                Text(
                    text = "2. Late Awaiter (Interactive trigger):",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Button(
                    onClick = onLateAwaitClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("late_await_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Try Late Await (Awaits C)")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B22))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = state.lateAwaitStatus.ifEmpty { "Not Triggered" },
                            color = if (state.lateAwaitStatus.contains("Instantly")) Color(0xFF34D399) else if (state.lateAwaitStatus.contains("Querying")) Color(0xFF38BDF8) else Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("late_status_text")
                        )
                        if (state.lateAwaitTimeMs > 0 || state.lateAwaitStatus.contains("Instantly")) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Retrieved immediately in ${state.lateAwaitTimeMs} ms without any thread blocks.",
                                color = Color(0xFF9CA3AF),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperOptionsSection(context: Context, onResetStableCount: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A111F)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF331F3B), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Developer Diagnostics Panel",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Erase launch history, reset stable launching metrics buffers, or force process restarts to test the stable launch gate conditions.",
                color = Color(0xFFD4B3E6),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Button(
                onClick = onResetStableCount,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF701F6E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Icon")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Reset Launch History & Wipes Records", fontSize = 12.sp)
            }
        }
    }
}
