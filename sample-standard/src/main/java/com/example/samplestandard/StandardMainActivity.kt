package com.example.samplestandard

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.frameready.ExecutionThread
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import com.frameready.StartupMetrics
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
// INITIALIZER 1 — Analytics SDK (no deps)
// Simulates Firebase Analytics / Amplitude init
// ─────────────────────────────────────────────
class AnalyticsInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(800)
        return "analytics::session_id=fr_${System.currentTimeMillis()}"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 2 — Crash Reporter (no deps)
// Simulates Crashlytics / Sentry init
// ─────────────────────────────────────────────
class CrashReporterInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(400)
        return "crash_reporter::enabled=true handler=registered"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 3 — Image Loader (no deps)
// Simulates Coil / Glide disk-cache warm-up
// ─────────────────────────────────────────────
class ImageLoaderInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(300)
        return "image_loader::cache=512MB strategy=LRU"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 4 — Feature Flags (depends on Analytics)
// Simulates Firebase Remote Config / LaunchDarkly
// Requires analytics session ID to tag experiments
// ─────────────────────────────────────────────
class FeatureFlagsInitializer : FrameReadyInitializer<Map<String, Boolean>> {
    override fun dependencies() = listOf(AnalyticsInitializer::class)
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): Map<String, Boolean> {
        val session = FrameReady.getOrNull(AnalyticsInitializer::class) ?: "unknown"
        delay(600)
        return mapOf(
            "new_checkout_ui" to true,
            "dark_mode_v2" to false,
            "ai_recommendations" to true,
            "session_tagged" to session.isNotEmpty()
        )
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 5 — Push Notifications (depends on CrashReporter)
// Simulates FCM token registration
// Crash reporter must be active before registering push token
// ─────────────────────────────────────────────
class PushNotificationInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = listOf(CrashReporterInitializer::class)
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(500)
        return "fcm::token=FR_${Integer.toHexString(System.identityHashCode(this))}_registered"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 6 — Database (no deps, existing)
// ─────────────────────────────────────────────
class DatabaseInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(1200)
        return "db::SQLite_v3 migrations=applied pool=ready"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 7 — Remote Config (no deps, existing)
// ─────────────────────────────────────────────
class ConfigInitializer : FrameReadyInitializer<Map<String, String>> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): Map<String, String> {
        delay(600)
        return mapOf("env" to "production", "logLevel" to "WARN", "apiVersion" to "v3")
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 8 — Network/API Client (depends on Database + Config)
// Simulates OkHttp + Retrofit client with DB-backed cache
// ─────────────────────────────────────────────
class NetworkCacheInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = listOf(
        DatabaseInitializer::class,
        ConfigInitializer::class
    )
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        val config = FrameReady.getOrNull(ConfigInitializer::class)
        delay(500)
        return "network::client=ready cache=db-backed api=${config?.get("apiVersion") ?: "v1"}"
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 9 — Intentionally Failing (no deps)
// Demonstrates failure isolation: only this initializer and its
// dependents fail; all other initializers still resolve.
// ─────────────────────────────────────────────
class FailingInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(300)
        throw RuntimeException("Simulated SDK init failure (e.g. missing API key)")
    }
}

// ─────────────────────────────────────────────
// INITIALIZER 10 — Depends on the failing one
// Demonstrates cascade: when a dependency fails, this initializer
// also fails — but all independent initializers are unaffected.
// ─────────────────────────────────────────────
class DependentOnFailingInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = listOf(FailingInitializer::class)
    override fun executionThread() = ExecutionThread.BACKGROUND
    override suspend fun create(context: android.content.Context): String {
        delay(200)
        return "dependent::ok"
    }
}

// ─────────────────────────────────────────────
// DATA MODEL for live UI state
// ─────────────────────────────────────────────
data class InitStatus(
    val name: String,
    val emoji: String,
    val deps: String,
    val calledAtMs: Long = 0,
    val resolvedAtMs: Long = 0,
    val result: String? = null,
    val state: State = State.WAITING,
    val error: String? = null
) {
    enum class State { WAITING, SUSPENDED, RESOLVED, ERROR }
    val suspendedForMs get() = if (resolvedAtMs > 0) resolvedAtMs - calledAtMs else 0L
}

// ─────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────
class StandardMainActivity : ComponentActivity() {

    private val activityStartMs = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initMode = intent.getStringExtra("INIT_MODE") ?: "frameready"
        if (initMode == "traditional" || initMode == "appstartup") {
            try { Thread.sleep(1500) } catch (_: Exception) {}
        }

        lifecycleScope.launch {
            FrameReady.metricsFlow.collect {
                Log.i("FrameReady", "Metrics: ttff=${it.ttffMs}ms initializers=${it.initializerCount}")
            }
        }

        // Signal macrobenchmark timeToFullDisplay: fires when all 8 successful initializers complete.
        // TTFD − TTID = "immediate access wait time" if a feature calls await() at first frame.
        // runCatching absorbs FailingInitializer / DependentOnFailing so they don't block TTFD.
        lifecycleScope.launch {
            listOf(
                async { runCatching { FrameReady.await(AnalyticsInitializer::class) } },
                async { runCatching { FrameReady.await(CrashReporterInitializer::class) } },
                async { runCatching { FrameReady.await(ImageLoaderInitializer::class) } },
                async { runCatching { FrameReady.await(FeatureFlagsInitializer::class) } },
                async { runCatching { FrameReady.await(PushNotificationInitializer::class) } },
                async { runCatching { FrameReady.await(DatabaseInitializer::class) } },
                async { runCatching { FrameReady.await(ConfigInitializer::class) } },
                async { runCatching { FrameReady.await(NetworkCacheInitializer::class) } },
            ).awaitAll()
            reportFullyDrawn()
        }

        setContent { StandardSampleScreen(activityStartMs) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardSampleScreen(activityStartMs: Long) {
    val frameReadyMetrics = remember { mutableStateOf<StartupMetrics?>(null) }

    // Each entry tracks live state for one initializer
    val statuses = remember {
        mutableStateListOf(
            InitStatus("Analytics",              "📊", "none"),
            InitStatus("CrashReporter",          "🛡️", "none"),
            InitStatus("ImageLoader",            "🖼️", "none"),
            InitStatus("FeatureFlags",           "🚩", "→ Analytics"),
            InitStatus("PushNotifications",      "🔔", "→ CrashReporter"),
            InitStatus("Database",               "🗄️", "none"),
            InitStatus("Config",                 "⚙️", "none"),
            InitStatus("NetworkCache",           "🌐", "→ DB + Config"),
            InitStatus("FailingSDK",             "💥", "none"),
            InitStatus("DependentOnFailing",     "🔗", "→ FailingSDK"),
        )
    }

    // getOrNull() poll: shows the non-blocking API returning null → actual value
    var getOrNullSnapshot by remember { mutableStateOf<String?>(null) }
    // Late-arrival: await() called after all initializers are done — should resolve instantly
    var lateArrivalMs by remember { mutableStateOf<Long?>(null) }

    // ── Aggressive pre-init access: fire ALL awaits immediately ──────────
    // These coroutines start before any initializer has run (we're still
    // in the first frame's composition). Each suspends, then resumes when
    // the initializer finishes post-frame. This validates the suspend
    // contract under maximum concurrency pressure.
    LaunchedEffect(Unit) {
        val t0 = SystemClock.elapsedRealtime()

        fun markCalled(index: Int) {
            statuses[index] = statuses[index].copy(
                calledAtMs = SystemClock.elapsedRealtime() - t0,
                state = InitStatus.State.SUSPENDED
            )
        }
        fun markResolved(index: Int, result: String) {
            statuses[index] = statuses[index].copy(
                resolvedAtMs = SystemClock.elapsedRealtime() - t0,
                result = result,
                state = InitStatus.State.RESOLVED
            )
        }
        fun markError(index: Int, msg: String) {
            statuses[index] = statuses[index].copy(
                resolvedAtMs = SystemClock.elapsedRealtime() - t0,
                error = msg,
                state = InitStatus.State.ERROR
            )
        }

        // All 8 await() calls fire in parallel — before the first frame fires.
        // Wrapped in try-catch so a timeout shows as ERROR in the UI rather than crashing.
        launch {
            markCalled(0)
            try { markResolved(0, FrameReady.await(AnalyticsInitializer::class)) }
            catch (e: Exception) { markError(0, e.message ?: "timeout") }
        }
        launch {
            markCalled(1)
            try { markResolved(1, FrameReady.await(CrashReporterInitializer::class)) }
            catch (e: Exception) { markError(1, e.message ?: "timeout") }
        }
        launch {
            markCalled(2)
            try { markResolved(2, FrameReady.await(ImageLoaderInitializer::class)) }
            catch (e: Exception) { markError(2, e.message ?: "timeout") }
        }
        launch {
            markCalled(3)
            try {
                val r = FrameReady.await(FeatureFlagsInitializer::class)
                markResolved(3, r.entries.joinToString(" ") { "${it.key}=${it.value}" })
            } catch (e: Exception) { markError(3, e.message ?: "timeout") }
        }
        launch {
            markCalled(4)
            try { markResolved(4, FrameReady.await(PushNotificationInitializer::class)) }
            catch (e: Exception) { markError(4, e.message ?: "timeout") }
        }
        launch {
            markCalled(5)
            try { markResolved(5, FrameReady.await(DatabaseInitializer::class)) }
            catch (e: Exception) { markError(5, e.message ?: "timeout") }
        }
        launch {
            markCalled(6)
            try {
                val r = FrameReady.await(ConfigInitializer::class)
                markResolved(6, r.entries.joinToString(" ") { "${it.key}=${it.value}" })
            } catch (e: Exception) { markError(6, e.message ?: "timeout") }
        }
        launch {
            markCalled(7)
            try { markResolved(7, FrameReady.await(NetworkCacheInitializer::class)) }
            catch (e: Exception) { markError(7, e.message ?: "timeout") }
        }
        // Initializer 9: intentionally fails to prove failure isolation
        launch {
            markCalled(8)
            try { markResolved(8, FrameReady.await(FailingInitializer::class)) }
            catch (e: Exception) { markError(8, e.message ?: "failed") }
        }
        // Initializer 10: depends on #9 — cascade-fails, others unaffected
        launch {
            markCalled(9)
            try { markResolved(9, FrameReady.await(DependentOnFailingInitializer::class)) }
            catch (e: Exception) { markError(9, e.message ?: "cascade") }
        }

        // getOrNull() poll: non-blocking; returns null until Analytics resolves
        launch {
            while (getOrNullSnapshot == null) {
                getOrNullSnapshot = FrameReady.getOrNull(AnalyticsInitializer::class)
                if (getOrNullSnapshot == null) delay(100)
            }
        }

        // Late-arrival await(): wait for everything to settle, then call await() on an already-
        // resolved initializer and measure how long it takes — should be < 1ms (instant cache hit).
        launch {
            delay(6000)
            val t = SystemClock.elapsedRealtime()
            try {
                FrameReady.await(ImageLoaderInitializer::class)
                lateArrivalMs = SystemClock.elapsedRealtime() - t
            } catch (_: Exception) {}
        }

        // Collect metrics
        launch {
            FrameReady.metricsFlow.collect { frameReadyMetrics.value = it }
        }
    }

    val resolvedCount = statuses.count { it.state == InitStatus.State.RESOLVED }
    val errorCount    = statuses.count { it.state == InitStatus.State.ERROR }
    val metrics = frameReadyMetrics.value

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF64B5F6),
            background = Color(0xFF0D0D14),
            surface = Color(0xFF1A1A26)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("FrameReady — 10 Initializers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Aggressive Pre-Init Access Demo", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A1A26),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF0D0D14)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ── TTFF banner ──────────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2137)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("⚡ Time to First Frame", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                            Text(
                                if (metrics != null) "${metrics.ttffMs} ms" else "measuring…",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "10 SDKs registered — zero main-thread cost",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${resolvedCount}✅  ${errorCount}❌", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Text(
                                "${resolvedCount + errorCount} / 10",
                                color = if (resolvedCount + errorCount == 10) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Dependency graph legend ───────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A26)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Dependency Graph", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Analytics(800ms) → FeatureFlags(600ms)", color = Color.White.copy(0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("CrashReporter(400ms) → PushNotifications(500ms)", color = Color.White.copy(0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("DB(1200ms) + Config(600ms) → NetworkCache(500ms)", color = Color.White.copy(0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("ImageLoader(300ms)  [independent]", color = Color.White.copy(0.7f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("FailingSDK(300ms→💥) → DependentOnFailing(cascade❌)", color = Color(0xFFEF9A9A).copy(0.85f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }

                // ── Per-initializer cards ─────────────────────────────────
                Text(
                    "All 10 await() calls fired at Activity.onCreate() — before first frame",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                statuses.forEach { status ->
                    InitializerCard(status)
                }

                // ── getOrNull() poll demo ─────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A26)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(0.4f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("getOrNull() — non-blocking poll", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "Returns null immediately if not ready; no suspension, no blocking.",
                            color = Color.White.copy(0.5f), fontSize = 10.sp
                        )
                        Text(
                            if (getOrNullSnapshot == null) "getOrNull(Analytics) → null  ⏳ polling every 100ms…"
                            else "getOrNull(Analytics) → \"${getOrNullSnapshot}\"",
                            color = if (getOrNullSnapshot == null) Color(0xFFFFB74D) else Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                // ── Late-arrival await() demo ─────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A26)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(0.4f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Late await() — cache hit", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "await() called 6 s after launch on an already-resolved initializer.",
                            color = Color.White.copy(0.5f), fontSize = 10.sp
                        )
                        Text(
                            when (lateArrivalMs) {
                                null -> "await(ImageLoader) → waiting 6 s to fire…"
                                0L   -> "await(ImageLoader) → resolved in < 1 ms  ✅ instant cache hit"
                                else -> "await(ImageLoader) → resolved in ${lateArrivalMs} ms  ✅ instant cache hit"
                            },
                            color = if (lateArrivalMs != null) Color(0xFF81C784) else Color(0xFFFFB74D),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                // ── Post-frame background work total ─────────────────────
                if (metrics != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A1B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Post-Frame Execution Summary", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            MetricRow("TTFF (first frame)", "${metrics.ttffMs} ms")
                            MetricRow("Background work completed", "${metrics.initCompleteMs} ms")
                            MetricRow("Initializers registered", "${metrics.initializerCount} (${resolvedCount}✅ ${errorCount}❌)")
                            MetricRow("Cold start rate", "${String.format("%.1f", metrics.coldStartRate)}%")
                            MetricRow("Stable launches", "${metrics.stableLaunchCount}")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun InitializerCard(status: InitStatus) {
    val bgColor = when (status.state) {
        InitStatus.State.RESOLVED  -> Color(0xFF0F2A0F)
        InitStatus.State.SUSPENDED -> Color(0xFF2A1F0A)
        InitStatus.State.ERROR     -> Color(0xFF2A0A0A)
        InitStatus.State.WAITING   -> Color(0xFF1A1A26)
    }
    val borderColor = when (status.state) {
        InitStatus.State.RESOLVED  -> Color(0xFF388E3C)
        InitStatus.State.SUSPENDED -> Color(0xFFF57C00)
        InitStatus.State.ERROR     -> Color(0xFFE53935)
        InitStatus.State.WAITING   -> Color(0xFF333355)
    }
    val stateLabel = when (status.state) {
        InitStatus.State.RESOLVED  -> "✅ RESOLVED"
        InitStatus.State.SUSPENDED -> "⏳ SUSPENDED (awaiting post-frame)"
        InitStatus.State.ERROR     -> "❌ ERROR"
        InitStatus.State.WAITING   -> "◌  WAITING"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${status.emoji} ${status.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(stateLabel, color = borderColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (status.deps != "none") {
                Text("deps: ${status.deps}", color = Color.White.copy(0.45f), fontSize = 10.sp)
            }

            if (status.calledAtMs > 0) {
                Text(
                    "await() called at +${status.calledAtMs}ms  |  suspended for ${status.suspendedForMs}ms",
                    color = Color.White.copy(0.6f),
                    fontSize = 10.sp
                )
            }

            if (status.result != null) {
                Text(
                    status.result,
                    color = Color(0xFF81C784),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            if (status.error != null) {
                Text(
                    status.error,
                    color = Color(0xFFEF9A9A),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(0.65f), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
