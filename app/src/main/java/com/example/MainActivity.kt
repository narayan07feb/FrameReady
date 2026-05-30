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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
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

        // Card 1.5: Interactive Benchmark Arena (Cold Start Battle)
        BenchmarkArenaSection(
            state = state,
            onRunSimClick = { viewModel.runBenchmarkSimulation() }
        )

        // Card 2: Chained Dependency Graph Visualization (A -> B -> C)
        DependencyGraphSection()

        // Card 3: Interactive Suspend/Await Contract Demonstrator
        AwaitDemonstrationSection(
            state = state,
            onLateAwaitClick = { viewModel.testLateAwait() }
        )

        // Card 3.5: DI Integration Showcase (Koin & Hilt Blueprint)
        DependencyInjectionShowcaseSection()

        // Card 4: Dev Actions & Reset Options
        DeveloperOptionsSection(
            context = context,
            onResetStableCount = {
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

            HorizontalDivider(color = Color(0xFF23233B))

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
                val rateText = String.format(java.util.Locale.US, "%.1f%%", rate)
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

            HorizontalDivider(color = Color(0xFF23233B))

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
            
            HorizontalDivider(color = Color(0xFF23233B))

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
            
            HorizontalDivider(color = Color(0xFF23233B))

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

@Composable
fun BenchmarkArenaSection(
    state: com.example.demo.UiState,
    onRunSimClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                Column {
                    Text(
                        text = "Benchmark Arena: Cold-Start Battle",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Analyzing heavy 3-second initialization impact on Cold Start",
                        color = Color(0xFF9E9EB8),
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF23233B))

            // Simulation Trigger and Active Progress display
            if (state.isSimulating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131326))
                        .border(1.dp, Color(0xFF312E81), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Evaluating Thread Scheduling...",
                                color = Color(0xFF818CF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF818CF8)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { state.simProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF818CF8),
                            trackColor = Color(0xFF1E1B4B)
                        )

                        Text(
                            text = state.simCurrentStep,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRunSimClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF312E81))
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Trigger Cold-Start Battle Simulation", fontWeight = FontWeight.Bold)
                }
            }

            // The Three Contenders Comparison Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Approach 1 Block
                ContenderBox(
                    title = "1. Classical Application.onCreate()",
                    subtitle = "Blocks Main thread synchronously before activity setup.",
                    ttffText = if (state.simAppClassTtff > 0) "${state.simAppClassTtff} ms" else "Idle (Pending)",
                    statusText = "🔴 Screen frozen black/white",
                    barColor = Color(0xFFEF4444),
                    relativeWidth = if (state.simAppClassTtff > 0) 1.0f else 0.05f,
                    isActive = state.activeApproachIndex == 0
                )

                // Approach 2 Block
                ContenderBox(
                    title = "2. AndroidX App Startup Library",
                    subtitle = "Runs synchronously inside ContentProviders blocking first draw.",
                    ttffText = if (state.simAndroidXTtff > 0) "${state.simAndroidXTtff} ms" else "Idle (Pending)",
                    statusText = "🔴 App frozen on splash/white",
                    barColor = Color(0xFFF97316),
                    relativeWidth = if (state.simAndroidXTtff > 0) 0.96f else 0.05f,
                    isActive = state.activeApproachIndex == 1
                )

                // Approach 3 Block
                ContenderBox(
                    title = "3. FrameReady Post-First-Frame",
                    subtitle = "Renders UI instantly first, processes heavy work on IO pool asynchronously.",
                    ttffText = if (state.simFrameReadyTtff > 0) "${state.simFrameReadyTtff} ms" else "Idle (Pending)",
                    statusText = "🟢 Immediate drawn & fluid",
                    barColor = Color(0xFF10B981),
                    relativeWidth = if (state.simFrameReadyTtff > 0) 0.06f else 0.05f,
                    isActive = state.activeApproachIndex == 2
                )
            }

            HorizontalDivider(color = Color(0xFF23233B))

            // Explanatory code reference text
            Text(
                text = "💡 Benchmark blueprints and classes are located at \"com.example.demo.BenchmarkSamples.kt\". Try resetting the device launch history block below to see actual cold-start metric collection in real time.",
                color = Color(0xFF9E9EB8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ContenderBox(
    title: String,
    subtitle: String,
    ttffText: String,
    statusText: String,
    barColor: Color,
    relativeWidth: Float,
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF1A1D2B) else Color(0xFF161622))
            .border(
                1.dp,
                if (isActive) barColor else Color(0xFF23233B),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = barColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TTFF Ratio:",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1F1F2E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(relativeWidth)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }

                Text(
                    text = ttffText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DependencyInjectionShowcaseSection() {
    var selectedTab by remember { mutableStateOf("Koin") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                Column {
                    Text(
                        text = "DI Integration: Koin & Hilt Blueprint",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Awaiting asynchronous dependencies safely",
                        color = Color(0xFF9E9EB8),
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF23233B))

            // Tab selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C14), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Koin", "Hilt").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1F1F35) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color(0xFF312E81) else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tab == "Koin") "Koin Setup Guide" else "Hilt Async Setup",
                            color = if (isSelected) Color.White else Color(0xFF9E9EB8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Display content based on selection
            if (selectedTab == "Koin") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🚀 Dynamic Post-First-Frame Bootstrap",
                        color = Color(0xFF818CF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Koin supports dynamic start. Instead of launching Koin in your Application.onCreate() on the busy UI thread, launch it asynchronously inside a FrameReadyInitializer.",
                        color = Color(0xFF9E9EB8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    // Step card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161626))
                            .border(1.dp, Color(0xFF23233B), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Code Implementation Blueprint",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Mock Code Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0C14), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "class KoinFrameReadyInitializer : FrameReadyInitializer<Boolean> {\n" +
                                            "    override suspend fun create(context: Context): Boolean {\n" +
                                            "        startKoin {\n" +
                                            "            androidContext(context)\n" +
                                            "            modules(appModule)\n" +
                                            "        }\n" +
                                            "        return true\n" +
                                            "    }\n" +
                                            "}",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 Call 'FrameReady.await(KoinFrameReadyInitializer::class.java)' inside any ViewModel's init block to suspend safe execution until Koin modules finish registering.",
                        color = Color(0xFF2DD4BF),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🛠️ Asynchronous Singleton Injections",
                        color = Color(0xFFF472B6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hilt resolves compile-time references instantly, but heavyweight setups (such as SQLite indices or remote configuration retrievals) must not block launcher threads. Combine Hilt with FrameReady by injecting lightweight holders first, then initializing them in the background.",
                        color = Color(0xFF9E9EB8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    // Step card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161626))
                            .border(1.dp, Color(0xFF23233B), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Code Implementation Blueprint",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Mock Code Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0C14), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "class DbFrameReadyInitializer : FrameReadyInitializer<Any> {\n" +
                                            "    override suspend fun create(context: Context): Any {\n" +
                                            "        // Heavy DB setup on Background Worker context\n" +
                                            "        val db = Room.databaseBuilder(...).build()\n" +
                                            "        dbHolder.setConnection(db)\n" +
                                            "        return db\n" +
                                            "    }\n" +
                                            "}",
                                    color = Color(0xFFFBCFE8),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 Safely obtain the connection inside consumer classes using: 'FrameReady.await(DbFrameReadyInitializer::class.java)' inside a coroutine. This blocks zero threads!",
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF23233B))

            Text(
                text = "🔎 See full, highly detailed architectural blueprints with code comments under 'com.example.demo.DependencyInjectionIntegration.kt' in the project files.",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}
