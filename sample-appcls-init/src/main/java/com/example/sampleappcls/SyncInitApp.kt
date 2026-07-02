package com.example.sampleappcls

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Traditional approach: all SDKs initialized sequentially in Application.onCreate().
//
// This is exactly what most Android apps did (and many still do): block the main
// thread in Application.onCreate() until every SDK is ready, then draw the first frame.
//
// Same 8 SDKs + same delays as sample-standard's FrameReady initializers:
//
//   SDK             Delay   Deps
//   --------------- ------- ------------------
//   Analytics       800ms   none
//   CrashReporter   400ms   none
//   ImageLoader     300ms   none
//   Database       1200ms   none
//   Config          600ms   none
//   FeatureFlags    600ms   → Analytics
//   PushNotif       500ms   → CrashReporter
//   NetworkCache    500ms   → Database + Config
//   ─────────────────────────────────────────
//   Sequential sum: 4900ms  (manual dep ordering, no parallelism)
//
// Timeline:
//   t=0ms    Application.onCreate() starts — user sees nothing
//   t=4900ms All SDKs initialized, first frame drawn
//   t=4900ms reportFullyDrawn() — deps immediately available, 0ms access wait
//
// Compare with FrameReady (sample-standard):
//   t=~100ms First frame drawn (user sees app 4800ms sooner)
//   t=~1800ms reportFullyDrawn() — parallel critical path: DB(1200ms)→NetworkCache(500ms)
//   Total to fully usable: 1800ms vs 4900ms (2.7× faster end-to-end)
// ─────────────────────────────────────────────────────────────────────────────

data class SdkEntry(
    val name: String,
    val emoji: String,
    val deps: String,
    val result: String,
    val delayMs: Long
)

class SyncInitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val t0 = SystemClock.elapsedRealtime()

        // Dependency-ordered sequential init — identical work to sample-standard's FrameReady inits.
        // No-dep SDKs first, then dependents in topological order.

        Thread.sleep(800)
        analyticsSession = "analytics::session_id=fr_${System.currentTimeMillis()}"
        log.add(SdkEntry("Analytics", "📊", "none", analyticsSession, 800))

        Thread.sleep(400)
        crashReporterReady = true
        log.add(SdkEntry("CrashReporter", "🛡️", "none", "crash_reporter::enabled=true handler=registered", 400))

        Thread.sleep(300)
        imageLoaderReady = true
        log.add(SdkEntry("ImageLoader", "🖼️", "none", "image_loader::cache=512MB strategy=LRU", 300))

        Thread.sleep(1200)
        dbReady = true
        log.add(SdkEntry("Database", "🗄️", "none", "db::SQLite_v3 migrations=applied pool=ready", 1200))

        Thread.sleep(600)
        config = mapOf("env" to "production", "logLevel" to "WARN", "apiVersion" to "v3")
        log.add(SdkEntry("Config", "⚙️", "none", "config::env=production apiVersion=v3", 600))

        // Dependents — must run after their deps; no parallelism possible without restructuring
        Thread.sleep(600) // waits for Analytics (already done) then 600ms own work
        featureFlags = mapOf("new_checkout_ui" to true, "dark_mode_v2" to false)
        log.add(SdkEntry("FeatureFlags", "🚩", "→ Analytics", "flags::checkout=true dark_v2=false", 600))

        Thread.sleep(500) // waits for CrashReporter (already done) then 500ms own work
        pushToken = "fcm::token=FR_${Integer.toHexString(hashCode())}_registered"
        log.add(SdkEntry("PushNotif", "🔔", "→ CrashReporter", pushToken, 500))

        Thread.sleep(500) // waits for DB + Config (already done) then 500ms own work
        networkReady = true
        log.add(SdkEntry("NetworkCache", "🌐", "→ DB + Config", "network::client=ready api=${config["apiVersion"]}", 500))

        totalBlockedMs = SystemClock.elapsedRealtime() - t0
    }

    companion object {
        // Shared SDK state — immediately readable once Application.onCreate() returns
        var analyticsSession: String = ""
        var crashReporterReady: Boolean = false
        var imageLoaderReady: Boolean = false
        var dbReady: Boolean = false
        var config: Map<String, String> = emptyMap()
        var featureFlags: Map<String, Boolean> = emptyMap()
        var pushToken: String = ""
        var networkReady: Boolean = false
        var totalBlockedMs: Long = 0
        val log = mutableListOf<SdkEntry>()
    }
}

// ─────────────────────────────────────────────────────────────────────────────

class SyncInitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SyncInitScreen() }
        // All deps are ready — Application.onCreate() already completed before we get here.
        // TTFD == TTID: no background work is happening after the first frame.
        reportFullyDrawn()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncInitScreen() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary    = Color(0xFFFFB74D),
            background = Color(0xFF0D1100),
            surface    = Color(0xFF1A1E00)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Traditional App Class Init", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Sequential · blocking Application.onCreate()", fontSize = 11.sp, color = Color.White.copy(0.5f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = Color(0xFF1A1E00),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF0D1100)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Cost banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1A00)),
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Blocked first frame for ${SyncInitApplication.totalBlockedMs}ms",
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "User saw a blank screen for ${SyncInitApplication.totalBlockedMs}ms.",
                            color = Color.White.copy(0.75f), fontSize = 12.sp
                        )
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Text(
                            "Benchmark comparison (same 8 SDKs, same delays):",
                            color = Color.White.copy(0.5f), fontSize = 11.sp
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("TTID", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                Text("~${SyncInitApplication.totalBlockedMs}ms", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("(blocked)", color = Color(0xFFFFB74D).copy(0.6f), fontSize = 10.sp)
                            }
                            Column {
                                Text("TTFD", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                Text("~${SyncInitApplication.totalBlockedMs}ms", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("(same — 0ms wait)", color = Color(0xFFFFB74D).copy(0.6f), fontSize = 10.sp)
                            }
                            Column {
                                Text("Access wait", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                Text("0ms", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("(ready at TTID)", color = Color(0xFF81C784).copy(0.6f), fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Text(
                            "FrameReady (sample-standard): TTID ~100ms · TTFD ~1800ms\n" +
                            "→ User sees app 4800ms sooner, fully ready 3100ms sooner.",
                            color = Color(0xFF80CBC4), fontSize = 11.sp
                        )
                    }
                }

                Text(
                    "8 SDKs — all ready at first frame · access wait: 0ms",
                    color = Color.White.copy(0.4f), fontWeight = FontWeight.Bold, fontSize = 12.sp
                )

                SyncInitApplication.log.forEach { sdk ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1E00)),
                        shape  = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(sdk.emoji, fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(sdk.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (sdk.deps != "none") {
                                        Spacer(Modifier.width(6.dp))
                                        Text(sdk.deps, color = Color.White.copy(0.35f), fontSize = 10.sp)
                                    }
                                }
                                Text(sdk.result, color = Color(0xFFFFB74D).copy(0.8f), fontSize = 10.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("✅", fontSize = 14.sp)
                                Text("+${sdk.delayMs}ms", color = Color(0xFFFFB74D), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
