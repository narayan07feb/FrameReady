package com.example.demo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.extraColors
import com.example.ui.theme.extraTypography

/**
 * The FrameReady demo's main showcase screen. Shared verbatim between Android and iOS via
 * Compose Multiplatform — no platform-specific branches in this file.
 */
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Cap body content width on large/tablet screens and center it — full width is
    // for navigation chrome, not paragraphs (M3 layout guidance).
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()

            MetricsBoardCard(state)

            BenchmarkArenaSection(
                state = state,
                onRunSimClick = { viewModel.runBenchmarkSimulation() }
            )

            DependencyGraphSection()

            AwaitDemonstrationSection(
                state = state,
                onLateAwaitClick = { viewModel.testLateAwait() }
            )

            DependencyInjectionShowcaseSection()

            DeveloperOptionsSection(
                resetMessage = state.resetMessage,
                onResetStableCount = { viewModel.resetStabilityRecords() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeaderSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "FRAMEREADY STARTUP",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Dynamic Launch Optimization",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Executing heavy, non-critical SDK initializers after the first frame completes to increase render speeds.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MetricsBoardCard(state: UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metrics_card")
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (state.metricsCallbackFired) MaterialTheme.extraColors.successContainer
                            else MaterialTheme.colorScheme.errorContainer
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (state.metricsCallbackFired) "STABLE GATED" else "UNCALIBRATED",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            val currentActivity = state.startupMetrics?.activityName?.ifEmpty { "MainActivity" } ?: "MainActivity"
            val displayedTime = state.startupMetrics?.displayedMs ?: 210L
            Text(
                text = "Captured from Activity: $currentActivity (OS Displayed: +${displayedTime}ms)",
                color = MaterialTheme.extraColors.infoSoft,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Performance columns Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricKPI(
                    label = "Time-to-First-Frame",
                    value = "${state.startupMetrics?.ttffMs ?: 182} ms",
                    subtext = "App.onCreate -> DecorView Draw"
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
                val rateText = formatPercent(rate)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val coldStartFraction = state.startupMetrics?.coldStartRate ?: 100.0
                val coldStartFractionText = formatPercent(coldStartFraction)
                MetricKPI(
                    label = "Cold Start Rate",
                    value = coldStartFractionText,
                    subtext = "Ratio of cold-start to total launches"
                )

                MetricKPI(
                    label = "Launch Diagnostics",
                    value = if (coldStartFraction < 100.0) "Mixed Starts" else "Cold Baseline",
                    subtext = "Calculated on-device telemetry"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Percentiles Section
            Text(
                text = "TTFF Percentiles across history:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
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

            // Trampoline status alert — respects reduced-motion (M3 motion guidance).
            val skipped = state.startupMetrics?.trampolineSkipped ?: true
            val count = state.startupMetrics?.trampolineSkipCount ?: 1
            val reduceMotion = LocalReducedMotion.current
            AnimatedVisibility(
                visible = skipped,
                enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.extraColors.warningContainer)
                        .border(1.dp, MaterialTheme.extraColors.warningOutline, MaterialTheme.shapes.small)
                        .padding(10.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.extraColors.warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trampoline Activity detected & bypassed ($count skips). Initializers held until primary window rendered.",
                            color = MaterialTheme.extraColors.onWarningContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricKPI(label: String, value: String, subtext: String) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.extraTypography.metricValue)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtext, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PercentileBadge(percentile: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = percentile, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(text = value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DependencyGraphSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Registered Dependency Graph & Ordering",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            NodeBox(
                name = "AInitializer (Core Services)",
                durationMs = "800ms",
                thread = "Dispatchers.IO (BACKGROUND)",
                depStr = "No Dependencies (Root Node)",
                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                borderColor = MaterialTheme.colorScheme.primaryContainer
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }

            NodeBox(
                name = "BInitializer (Database Connection)",
                durationMs = "600ms",
                thread = "Dispatchers.IO (BACKGROUND)",
                depStr = "Requires Class <AInitializer>",
                bgColor = MaterialTheme.extraColors.info.copy(alpha = 0.16f),
                borderColor = MaterialTheme.colorScheme.tertiaryContainer
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }

            NodeBox(
                name = "CInitializer (Analytics Sync)",
                durationMs = "Instant",
                thread = "Dispatchers.Main (UI THREAD)",
                depStr = "Requires Class <BInitializer>",
                bgColor = MaterialTheme.extraColors.warning.copy(alpha = 0.12f),
                borderColor = MaterialTheme.extraColors.warningStrong
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
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = durationMs, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(text = "Target Exec Context: $thread", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = depStr, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun AwaitDemonstrationSection(state: UiState, onLateAwaitClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Await & Suspend Demonstration",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Early Awaiter Status Box
            Column {
                Text(
                    text = "1. Early Awaiter (Triggered from viewModelScope init):",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Column {
                        Text(
                            text = state.earlyAwaitStatus,
                            color = if (state.earlyAwaitStatus.contains("Resumed")) MaterialTheme.extraColors.success else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("early_status_text")
                        )
                        if (state.earlyAwaitTimeMs > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Suspended caller for ${state.earlyAwaitTimeMs} ms before resuming safely.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Late Awaiter Action Box
            Column {
                Text(
                    text = "2. Late Awaiter (Interactive trigger):",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onLateAwaitClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("late_await_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Try Late Await (Awaits C)")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Column {
                        Text(
                            text = state.lateAwaitStatus.ifEmpty { "Not Triggered" },
                            color = when {
                                state.lateAwaitStatus.contains("Instantly") -> MaterialTheme.extraColors.success
                                state.lateAwaitStatus.contains("Querying") -> MaterialTheme.extraColors.info
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("late_status_text")
                        )
                        if (state.lateAwaitTimeMs > 0 || state.lateAwaitStatus.contains("Instantly")) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Retrieved immediately in ${state.lateAwaitTimeMs} ms without any thread blocks.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperOptionsSection(resetMessage: String, onResetStableCount: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Developer Diagnostics Panel",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = "Erase launch history, reset stable launching metrics buffers, or force process restarts to test the stable launch gate conditions.",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge
            )

            Button(
                onClick = onResetStableCount,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Reset Launch History & Wipes Records", style = MaterialTheme.typography.bodySmall)
            }

            if (resetMessage.isNotEmpty()) {
                Text(
                    text = resetMessage,
                    color = MaterialTheme.extraColors.success,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("reset_message_text")
                )
            }
        }
    }
}

@Composable
fun BenchmarkArenaSection(
    state: UiState,
    onRunSimClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Analyzing heavy 3-second initialization impact on Cold Start",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Simulation Trigger and Active Progress display
            if (state.isSimulating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.extraColors.primaryContainerDeep, MaterialTheme.shapes.small)
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
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { state.simProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )

                        Text(
                            text = state.simCurrentStep,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRunSimClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extraColors.primaryContainerDeep)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Trigger Cold-Start Battle Simulation", fontWeight = FontWeight.Bold)
                }
            }

            // The Three Contenders Comparison Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContenderBox(
                    title = "1. Classical Application.onCreate()",
                    subtitle = "Blocks Main thread synchronously before activity setup.",
                    ttffText = if (state.simAppClassTtff > 0) "${state.simAppClassTtff} ms" else "Idle (Pending)",
                    statusText = "Screen frozen black/white",
                    barColor = MaterialTheme.colorScheme.error,
                    relativeWidth = if (state.simAppClassTtff > 0) 1.0f else 0.05f,
                    isActive = state.activeApproachIndex == 0
                )

                ContenderBox(
                    title = "2. AndroidX App Startup Library",
                    subtitle = "Runs synchronously inside ContentProviders blocking first draw.",
                    ttffText = if (state.simAndroidXTtff > 0) "${state.simAndroidXTtff} ms" else "Idle (Pending)",
                    statusText = "App frozen on splash/white",
                    barColor = MaterialTheme.extraColors.chartOrange,
                    relativeWidth = if (state.simAndroidXTtff > 0) 0.96f else 0.05f,
                    isActive = state.activeApproachIndex == 1
                )

                ContenderBox(
                    title = "3. FrameReady Post-First-Frame",
                    subtitle = "Renders UI instantly first, processes heavy work on IO pool asynchronously.",
                    ttffText = if (state.simFrameReadyTtff > 0) "${state.simFrameReadyTtff} ms" else "Idle (Pending)",
                    statusText = "Immediate drawn & fluid",
                    barColor = MaterialTheme.extraColors.successStrong,
                    relativeWidth = if (state.simFrameReadyTtff > 0) 0.06f else 0.05f,
                    isActive = state.activeApproachIndex == 2
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Benchmark blueprints and classes are located at \"com.example.demo.BenchmarkSamples.kt\". Try resetting the device launch history block below to see actual cold-start metric collection in real time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
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
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isActive) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                1.dp,
                if (isActive) barColor else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small
            )
            .padding(12.dp)
            .semantics(mergeDescendants = true) {}
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = barColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "TTFF Ratio:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(relativeWidth)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(barColor)
                    )
                }

                Text(
                    text = ttffText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
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
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Awaiting asynchronous dependencies safely",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Tab selectors — ≥48dp touch target height (M3 accessibility guidance).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Koin", "Hilt").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.extraColors.primaryContainerDeep else Color.Transparent,
                                MaterialTheme.shapes.extraSmall
                            )
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tab == "Koin") "Koin Setup Guide" else "Hilt Async Setup",
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Display content based on selection
            if (selectedTab == "Koin") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Dynamic Post-First-Frame Bootstrap",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Koin supports dynamic start. Instead of launching Koin in your Application.onCreate() on the busy UI thread, launch it asynchronously inside a FrameReadyInitializer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )

                    CodeBlueprintCard(
                        code = "class KoinFrameReadyInitializer : FrameReadyInitializer<Boolean> {\n" +
                                "    override suspend fun create(context: Context): Boolean {\n" +
                                "        startKoin {\n" +
                                "            androidContext(context)\n" +
                                "            modules(appModule)\n" +
                                "        }\n" +
                                "        return true\n" +
                                "    }\n" +
                                "}",
                        codeColor = MaterialTheme.extraColors.info
                    )

                    Text(
                        text = "Call 'FrameReady.await(KoinFrameReadyInitializer::class)' inside any ViewModel's init block to suspend safe execution until Koin modules finish registering.",
                        color = MaterialTheme.extraColors.success,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Asynchronous Singleton Injections",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hilt resolves compile-time references instantly, but heavyweight setups (such as SQLite indices or remote configuration retrievals) must not block launcher threads. Combine Hilt with FrameReady by injecting lightweight holders first, then initializing them in the background.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )

                    CodeBlueprintCard(
                        code = "class DbFrameReadyInitializer : FrameReadyInitializer<Any> {\n" +
                                "    override suspend fun create(context: Context): Any {\n" +
                                "        // Heavy DB setup on Background Worker context\n" +
                                "        val db = Room.databaseBuilder(...).build()\n" +
                                "        dbHolder.setConnection(db)\n" +
                                "        return db\n" +
                                "    }\n" +
                                "}",
                        codeColor = MaterialTheme.colorScheme.secondary
                    )

                    Text(
                        text = "Safely obtain the connection inside consumer classes using: 'FrameReady.await(DbFrameReadyInitializer::class)' inside a coroutine. This blocks zero threads!",
                        color = MaterialTheme.extraColors.warning,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "See full, highly detailed architectural blueprints with code comments under 'com.example.demo.DependencyInjectionIntegration.kt' in the project files.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun CodeBlueprintCard(code: String, codeColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Code Implementation Blueprint",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.extraSmall)
                    .padding(8.dp)
            ) {
                Text(
                    text = code,
                    color = codeColor,
                    style = MaterialTheme.extraTypography.code
                )
            }
        }
    }
}

/** `String.format("%.1f%%", ...)` isn't available in commonMain — this is the portable equivalent. */
private fun formatPercent(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return "$rounded%"
}
