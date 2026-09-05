# FrameReady 🚀

`FrameReady` is a Kotlin Multiplatform library that **defers each initializer's `create()`** until after first paint (Android Activity path) or until you call `signalCompositionReady()` (Compose / iOS). It does not delete SDK work. If the first screen `await()`s those results, time-to-usable stays about the same.

`androidx.startup` runs `Initializer.create()` on the main thread inside a `ContentProvider` **before** first frame. FrameReady still uses a ContentProvider on Android for **discovery and `install()`** (class loading and constructors can run that early). Only `create()` is scheduled later.

On Compose (Android or iOS), call `FrameReady.install(...)` once, then `FrameReady.signalCompositionReady()` from a `LaunchedEffect(Unit)` in the **root** composable. iOS has no Activity lifecycle auto-trigger. `LaunchedEffect` means that composable has started, not a hardware vsync guarantee.

---

## 🌟 Key Features

- **Post-frame `create()`**: Android Activity install triggers on a surviving, non-listed-trampoline activity (optional `trampolineThresholdMs` scan), then one `Choreographer` callback and on API 29+ a `FrameCommitCallback`. Compose/iOS use `signalCompositionReady()` from a root `LaunchedEffect`. Manifest class names are loaded at that trigger, not in the ContentProvider.
- **Topological Sorting (Kahn's Sort)**: Automatically builds and validates dependency graphs at install-time.
- **Declarative Thread Routing**: Execute heavy initializers on `Dispatchers.IO` (BACKGROUND) or light UI tasks on `Dispatchers.Main` (MAIN) seamlessly.
- **Thread-safe Wait/Suspend Contract**: If a consumer requests an initializer's result via `await()` before it is ready, the coroutine **suspends** and resumes automatically.
- **Deterministic Trampoline Skip**: List splash/router activities in `trampolineActivities`. Optional `trampolineThresholdMs` for unknown short-lived activities (default 0).
- **Stable Calibrated Metrics**: Retains launch latency. Auto-calculates historical TTFF (P50, P90, P99) and cold-start improvement index after reaching a customizable stability threshold (e.g. 100 successful runs) with auto-resets on failure.
- **Zero-Config Manifest Merging**: Automatic component discovery using standard ContentProvider meta-data declarations.

## ⚡ Library overhead

FrameReady's ContentProvider still runs before first frame. Benchmark 1 (below) compares a no-library app to FrameReady with **0 initializers**. Both apps `Thread.sleep(1500)` in `Application.onCreate`. The TTID delta was within run-to-run noise. That measurement does **not** support a "~1.5 ms vs ~120 ms ContentProvider" claim.

Historical metrics storage is bring-your-own (`FrameReadyStorage`). The library does not require its own on-disk cache.

---

## 🔬 Verified Macrobenchmark Results

The numbers below are real measurements from the included macrobenchmark suite, collected on a **Pixel 10 Pro (API 37 / Android 17) emulator** using `androidx.benchmark.macro 1.5.0-alpha01` and `StartupTimingMetric` with `StartupMode.COLD` (process killed between iterations, 5 iterations each).

> **Build note:** For production-representative numbers, install **release APKs** using `installRelease` (signed with the debug key — no extra keystore needed). The benchmark suppresses `DEBUGGABLE` errors so it also runs against debug APKs, but debug builds run ART in JIT mode and produce 20–40% higher absolute TTID. Use `installDebug` only for quick iteration; use `installRelease` when comparing numbers across runs.

---

### Benchmark 1 — Library Overhead (FrameReady with 0 Initializers)

**Workload:** Both apps block `Application.onCreate()` with 1,500 ms of `Thread.sleep` — a controlled stand-in for real SDK initialization (Analytics, Crashlytics, etc.). No Activity-level blocking. The only variable between the two apps is whether FrameReady's `ContentProvider` is present.

| Test | What it measures | Median TTID | Min | Max |
|:---|:---|---:|---:|---:|
| `benchmarkNoLibraryTraditional` | No library, pure blocking startup | **1,806.6 ms** | 1,772.4 ms | 1,945.8 ms |
| `benchmarkMetricsOnly` | FrameReady installed, **0 initializers** | **1,774.9 ms** | 1,751.1 ms | 1,888.4 ms |
| **Delta** | Library overhead | **−31.7 ms** | | |

> The −31.7 ms delta (< 2%) falls within the inter-iteration variability range (173 ms min–max spread across 5 emulator iterations). It is not a meaningful overhead signal. The `ContentProvider` + lifecycle-callback cost is not detectable against real app workloads.

---

### Benchmark 2 — Post-frame deferral (skip blocking `Activity.onCreate`)

**Workload:** Every `:sample-standard` launch still `Thread.sleep(800)` in `Application.onCreate`, including FrameReady mode. `traditional` and `appstartup` then `Thread.sleep(1500)` in `Activity.onCreate` **before** `setContent`. FrameReady mode skips that 1,500 ms. Registered initializers use `delay()` on background dispatchers after the first-frame trigger, so they do not affect TTID.

This measures **“do not block the main thread before `setContent`”**, not FrameReady vs a real `androidx.startup` integration.

> **`appstartup` mode:** `INIT_MODE = "appstartup"` only adds that 1,500 ms sleep. It does **not** use the `androidx.startup` library.

| Test | Strategy | Median TTID |
|:---|:---|---:|
| `benchmarkTraditional` | 800 ms in `Application.onCreate` + 1,500 ms in `Activity.onCreate` | **~2,699 ms** |
| `benchmarkAppStartupLibrary` | Same blocking pattern (not Jetpack App Startup) | **~2,708 ms** |
| `benchmarkFrameReady` | Same 800 ms in `Application.onCreate`, no Activity sleep | **~1,138 ms** |
| **Improvement** | vs the extra 1,500 ms main-thread sleep | **~57% faster TTID** |

`:sample-standard` also `await()`s eight successful initializers and then calls `reportFullyDrawn()`. Time-to-full-display is first frame **plus** that graph. TTID going down does not mean the first screen is already usable if it waits on those results.

---

### Running the Benchmarks Yourself

All benchmarks target physical devices or non-rooted emulators. Run them from the project root.

#### Prerequisites
```bash
# Confirm a device / emulator is connected
adb devices

# Install release APKs for accurate, production-representative numbers
# (All sample apps use the debug signing key for release builds — no keystore setup required)
./gradlew :sample-standard:installRelease
./gradlew :sample-baseline:installRelease
./gradlew :sample-metrics-only:installRelease
```

#### Run Benchmark 1 — Library Overhead (Baseline vs. Metrics-Only)
```bash
./gradlew :benchmark:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.benchmark.BaselineBenchmark
```
Runs two tests:
- `benchmarkNoLibraryTraditional` — no library, 1,500 ms blocking in `Application.onCreate`
- `benchmarkMetricsOnly` — FrameReady with 0 initializers, same 1,500 ms blocking

#### Run Benchmark 2 — Startup Deferral (Synchronous Blocking vs. FrameReady)
```bash
./gradlew :benchmark:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.benchmark.StartupBenchmark
```
Runs three tests:
- `benchmarkTraditional` — SDKs blocking in `Application.onCreate` and `Activity.onCreate`
- `benchmarkAppStartupLibrary` — same blocking pattern (simulates `androidx.startup` behavior)
- `benchmarkFrameReady` — same SDKs deferred post-frame via FrameReady

#### Run All Benchmarks at Once
```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

#### Where to Find Results

Results are printed to the Gradle console after each test and stored in:
```
benchmark/build/outputs/androidTest-results/connected/debug/<DEVICE>/testlog/test-results.log
```
Look for lines like:
```
timeToInitialDisplayMs   [min X.X], [median X.X], [max X.X]
```
Perfetto trace files (`.perfetto-trace`) for each iteration are pulled to the same directory and can be opened in [ui.perfetto.dev](https://ui.perfetto.dev) for frame-level profiling.

> **Note:** On **Android 17+ (API 37+)**, the benchmark runner requires `androidx.benchmark.macro >= 1.5.0-alpha01`. Earlier versions fail with `Unable to confirm activity launch completion []` due to a 15-character `/proc/PID/comm` truncation in `pgrep`. This project is already pinned to `1.5.0-alpha01` in `gradle/libs.versions.toml`.

---

## 🚀 Quick Start (3 Steps)

### 1. Add Dependency
Add JitPack to your root `build.gradle` or `settings.gradle`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```
Add the dependency to your module:
```kotlin
dependencies {
    implementation("com.github.narayan07feb:FrameReady:1.3.0")
}
```

### 2. Create an Initializer
Implement `FrameReadyInitializer<T>` to define your background task:
```kotlin
class DatabaseInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<KClass<out FrameReadyInitializer<*>>>()
    
    // Runs on Dispatchers.IO automatically!
    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: PlatformContext): String {
        // Run heavy SDK setups, DB migrations, or network calls here
        return "Database Connected"
    }
}
```

### 3. Register in Manifest
Add your initializer to the `AndroidManifest.xml` using the `FrameReadyProvider`. The library will automatically discover it and run it after the first frame!
```xml
<provider
    android:name="com.frameready.FrameReadyProvider"
    android:authorities="${applicationId}.frameready"
    android:exported="false">
    <meta-data
        android:name="com.example.yourpkg.DatabaseInitializer"
        android:value="post_frame_initializer" />
</provider>
```

On Android this uses Activity lifecycle (list trampolines, or opt-in `trampolineThresholdMs`) then a frame callback. `create()` and `Class.forName` for manifest names run after that, not during the provider.

**Compose / iOS:** there is no manifest discovery on iOS. Call `registerFactory` (required on iOS), `install(...)`, and from the **root** composable:

```kotlin
LaunchedEffect(Unit) { FrameReady.signalCompositionReady() }
```

Do not call `signalCompositionReady()` in the iOS host before `ComposeUIViewController` / first composition. That starts `create()` too early. The `:shared-ui` `MainScreen` already does this `LaunchedEffect`.

---

## 🤖 AI Assistant Prompt (Auto-Install)

Using an AI coding assistant (like Gemini, ChatGPT, Claude, or Copilot)? Just copy and paste this prompt into your AI chat, and it will automatically migrate your App Startup code to FrameReady for you!

```text
I want to integrate the `com.github.narayan07feb:FrameReady:<LATEST_VERSION>` library into my Android project via JitPack.

Please act as an interactive migration assistant. Before writing any code, ask me the following questions one by one:
1. Which heavy SDKs or libraries in my `Application.onCreate` or `androidx.startup` configurations do you want to migrate to FrameReady?
2. Do you want to collect FrameReady cold-start metrics?
3. If yes, on which Activity would you like to collect and observe these metrics?

Once I answer, analyze my code and generate the `FrameReadyInitializer` classes that execute on the `BACKGROUND` thread. Make sure to define dependencies using `List<KClass<out FrameReadyInitializer<*>>>` (`::class` references, not `::class.java`). Finally, show me how to register the new initializers in my `AndroidManifest.xml` under the `FrameReadyProvider`, and how to collect the `FrameReady.metricsFlow` in my chosen Activity.
```

---

## 📦 What actually changes vs blocking startup

Moving `create()` after first paint helps **TTID** only if that work used to run on the main thread **before** first pixels, and the first screen can render without those SDKs.

| | Blocking `onCreate` / App Startup `create()` | FrameReady |
|---|---|---|
| When `create()` runs | Before first frame (often on main) | After the platform trigger (see below) |
| First pixels | Wait on that work | Can draw if UI does not `await()` |
| First screen that `await()`s | Ready when init finishes (UI was blocked) | Ready when init finishes (UI already drawn) |
| Android trampolines | First resumed activity | Skip `trampolineActivities`; optional delay scan (default 0) |
| ANR | Possible if you block main too long | Still possible if `executionThread() = MAIN` and `create()` blocks |

**Do not put here:** crash reporters that must be up before the first crash, or anything the first frame must have synchronously.

The five `sample-*` apps show integration shapes (Hilt, trampoline, notification extras). They are **not** independently measured TTFF tables. Measured numbers are only Benchmark 1 and 2 above.

### Why TTID can drop

If SDKs run sequentially on the main thread before `setContent`, the activity cannot draw. FrameReady runs `create()` later, in dependency order, in parallel where the graph allows. Independent nodes overlap. Callers use suspending `await()`, not `Thread.sleep` on main.

**Android Activity path:** after a non-trampoline `onResume`, one `Choreographer` callback, then on API 29+ `registerFrameCommitCallback`. Optional `trampolineThresholdMs` uses `Handler.postDelayed`. That is not a double-buffered callback loop.

**Compose / iOS path:** you call `signalCompositionReady()`. Until you do (or the 10 s headless timeout), `create()` does not run.

---

## 🛠 Core Integration

### 1. Declaring a FrameReadyInitializer

Implement `FrameReadyInitializer<T>` to declare your task, its dependencies, and target thread context:

```kotlin
import com.frameready.FrameReadyInitializer
import com.frameready.ExecutionThread
import com.frameready.PlatformContext
import kotlin.reflect.KClass

class AInitializer : FrameReadyInitializer<String> {
    override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
    
    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: PlatformContext): String {
        // Perform file / network / disk setup
        return "Core Config Active"
    }
}
```

If task `B` depends on task `A`'s finished output, declare it under `dependencies()`:

```kotlin
class BInitializer : FrameReadyInitializer<Database> {
    override fun dependencies() = listOf(AInitializer::class)

    override suspend fun create(context: PlatformContext): Database {
        // A is guaranteed to be finished here. Safe to call getOrNull!
        val config = FrameReady.getOrNull(AInitializer::class)!!
        return Database.init(context, config)
    }
}
```

> **API note:** every `FrameReady` member — `await`, `getOrNull`, `get`, `disable`, `retry`,
> `registerFactory`, `asStateFlow`, `asDeferred` — takes a `KClass` (`SomeInitializer::class`),
> not `java.lang.Class` (`::class.java`). The one exception is the legacy
> `FrameReady.install(context, List<Class<Any>>)` overload used by manifest auto-discovery
> (Options A/B below) — that one still takes `Class` for source/binary compatibility.

---

## 🚦 Dependency Wait / Suspend Contract Rules

To protect your system from deadlocks and null references, the library strictly enforces **five core wait-rules**:

### Rule 1 — Declared dependencies complete first
If `B` depends on `A`, `B` will never enter its `create()` method until `A`'s initializer has published its value. This is solved via Topological Kahn Sort sorting.

### Rule 2 — External calls early will SUSPEND
If external code (e.g. a ViewModel) requests a result using `await()` before compilation finishes, the coroutine suspends and resumes automatically when the result completes:

```kotlin
// Called from a ViewModel's init block before first frame completes:
viewModelScope.launch {
    // 100% safe. Suspends caller, resumes as soon as B initializer finishes!
    val database = FrameReady.await(BInitializer::class)
    database.queryHistory()
}
```

### Rule 3 — Cycle-detection at first frame
If a dependency path is cyclic (e.g., `A -> B -> A`), `FrameReady` throws `CircularDependencyException` when the first-frame trigger runs (sort happens then, not at `install()`, so constructors are not paid in `ContentProvider`).

### Rule 4 — No Main-thread blocking
`await()` is a `suspend` function and does not block. In addition, the synchronous `.get()` method will write warning traces if called on the primary Main Looper.

### Rule 5 — Timeout protection
Specify optional timeouts to prevent endless lockouts:
```kotlin
suspend val db = FrameReady.await(BInitializer::class, timeoutMs = 3000L)
// Throws InitializerTimeoutException if B takes longer than 3 seconds
```

---

## 🎪 Trampoline Activity Lifecycle Handling

Many apps launch an invisible routing activity first (`SplashActivity`, Deep-link router, Notification dispatcher), which launches the real `MainActivity` and calls `finish()` instantly.

If a startup SDK simply hooks onto the first activity's resume, it will execute too early—on a window the user never sees.

`FrameReady` does **not** wait 500 ms on every cold start. Default `trampolineThresholdMs` is **0**: the first resumed activity that is not in `trampolineActivities` and not finishing triggers immediately (then Choreographer / frame-commit).

List splash/router activities:

```kotlin
FrameReady.trampolineActivities.add(SplashActivity::class.java)
```

Set `FrameReady.trampolineThresholdMs = 500L` only if you need to detect unknown short-lived activities without listing them.

Notification-originated intents (API 31+) still skip that delay and trigger on resume.

### 🔔 Notification & Deep Link Integration Guidelines

Since Android has no native flag indicating an `Activity` was launched via a notification, `FrameReady` uses heuristic detection to bypass the visual frame timing delay and run initializers immediately. To ensure your notifications and dynamic intents are handled optimaly, follow these guidelines:

#### 1. Standard Notifications (Bypassing the 500ms delay)
If a user taps on a notification to launch a specific detail screen directly, we want `FrameReady` to initialize post-frame immediately without wasting 500ms checking if the screen is a trampoline.

* **Detection Heuristic**: `FrameReady` checks if the starting intent contains any of the following:
  * An extra key `"from_notification"`
  * An extra key `"notification_id"`
  * An intent action string (`intent.action`) containing the word `"NOTIFICATION"` (case-insensitive checks aren't native, so it checks for substring `"NOTIFICATION"`).
* **Consumer Best Practice**: When creating your notification `PendingIntent`, always include the `"from_notification"` extra explicitly or use a custom action:
  ```kotlin
  val intent = Intent(context, DetailActivity::class.java).apply {
      putExtra("from_notification", true) // Ensures FrameReady triggers instantly
      putExtra("notification_id", uniqueNotifId)
      action = "com.example.action.NOTIFICATION_OPEN"
  }
  ```

#### 2. Deep Links & Deep-Link Trampolines
If a notification or an external web link opens a transparent routing/gateway Activity (which performs verification and routes the user to another page):

* **Avoid Early Triggers**: Since this gateway activity finishes instantly (`finish()`), `FrameReady`'s automatic trampoline logic will ignore it and wait for the real target Activity to surface.
* **Explicit Registration**: For absolute safety, register deep-link handler classes as explicit trampolines during application startup so they are skipped instantly without waiting for any threshold:
  ```kotlin
  FrameReady.trampolineActivities.add(MyDeepLinkActivity::class.java)
  ```

---

## 📊 Cold Start Improvement & Stability Gates

To provide accurate performance indicators, the library maintains a stability gate block:
- Keeps a **stable consecutive launches counter** in SharedPreferences.
- **Resets the counter back to 0** if any InitializerTimeoutException or initialization execution crashes occur.
- Tracks real-time **Cold Start Rate** (`coldStartRate`), representing the percentage of total launches that are cold starts.
- Calculates high-accuracy **OS Displayed Time** (`displayedMs`), measuring process start (or content provider start) to first frame completion, matching the Android `I/ActivityTaskManager: Displayed` system diagnostic logs.
- Captures **Draw Completion Activity Name** (`activityName`), helping consumers easily isolate and monitor distinct startup rates based on different activity entry paths.
- Computes actual **P50, P90, and P99 percentiles** of TTFF dynamically past the threshold.
- Exposes a `SharedFlow<StartupMetrics>` with `replay = 1`, so any late subscriber immediately receives the latest metrics!

### Observing Metrics via SharedFlow

Because `FrameReady.metricsFlow` is a `SharedFlow`, you can collect it from anywhere in your app—such as your `Application` class or main `Activity`.

**Example 1: Collecting in your `Application` class**
Since `Application` does not have a built-in lifecycle scope, create a simple coroutine scope to collect the flow:

```kotlin
class MyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        applicationScope.launch {
            FrameReady.metricsFlow.collect { metrics ->
                // Forward P50/P99 times, cold-start rates, and activity-specific timings to custom collectors
                FirebasePerformance.newTrace("cold_start_metrics").apply {
                    putMetric("ttff_p50", metrics.ttffP50)
                    putMetric("ttff_p99", metrics.ttffP99)
                    putMetric("net_improvement_percentage", metrics.netImprovementRate.toLong())
                    putMetric("cold_start_rate", metrics.coldStartRate.toLong())
                    putMetric("displayed_ms", metrics.displayedMs)
                    putAttribute("completed_activity", metrics.activityName)
                    stop()
                }
            }
        }
    }
}
```

**Example 2: Collecting in your Main `Activity`**
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // lifecycle.repeatOnLifecycle is recommended for UI-bound collection
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                FrameReady.metricsFlow.collect { metrics ->
                    Log.d("Startup", "TTFF was ${metrics.displayedMs}ms")
                }
            }
        }
    }
}
```

### Understanding StartupMetrics

The `metricsFlow` emits a comprehensive `StartupMetrics` payload. Here is a breakdown of the most important fields:

* **`displayedMs`**: The total elapsed time starting from the moment the Android OS originally forked the application process. On supported devices (API 24+), FrameReady automatically absorbs the pre-boot OS overhead into this metric. This closely mirrors the official `ActivityTaskManager: Displayed` OS log.
* **`ttffMs`**: The internal Time-to-First-Frame (TTFF) measured from `Application.onCreate` to the moment your Activity drew its first frame.
* **`activityName`**: The specific Activity that triggered the final rendering completion. If your Splash Screen redirects to a Home Screen or Settings Screen, `activityName` tells you precisely which destination caused the telemetry emission.

> [!NOTE]
> The following historical fields require **local persistence** to calculate. By default, FrameReady does not store any data locally, and these fields will emit default values. To enable historical medians, implement `FrameReadyStorage` and set `FrameReady.storage = yourImplementation` before calling `install()`.

* **`coldStartRate`**: A percentage (`Double`) representing how many of the app's total historical launches were true OS "Cold Starts" (e.g., `100.0` = 100%).
* **`stableLaunchCount`**: The number of consecutive, crash-free launches your application has completed. If the app crashes during boot, FrameReady intercepts the failure and resets this to `0`. FrameReady always emits metrics every launch — use `stableLaunchCount` in your collector to decide when the data is statistically meaningful (e.g. only send `ttffP50` to your analytics backend once `stableLaunchCount >= 50`).
* **`ttffP50` / `ttffP90` / `ttffP99`**: The historically maintained percentiles (Median, 90th, 99th) of your startup times, calculated dynamically based on stable historical data.

---

## 💉 Dependency Injection & Hilt Integration

Because initializers must have a zero-argument default constructor for instantiation, you cannot use constructor injection (`@Inject`) directly in a `FrameReadyInitializer`. 

Instead, you can resolve Hilt-managed services using **Hilt Entry Points**, or expose asynchronous values initialized by `FrameReady` back into the Hilt dependency graph.

### 1. Requesting Hilt-managed dependencies inside an Initializer

You can use `@EntryPoint` to access Hilt-managed bindings inside `create(context)` safely:

```kotlin
import android.content.Context
import com.frameready.FrameReadyInitializer
import com.frameready.ExecutionThread
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DatabaseInitializer : FrameReadyInitializer<SQLiteDatabase> {
    override fun dependencies() = emptyList<KClass<out FrameReadyInitializer<*>>>()
    override fun executionThread() = ExecutionThread.BACKGROUND

    // Declare the Hilt EntryPoint
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseInitializerEntryPoint {
        fun getDatabaseHelper(): DatabaseHelper
    }

    override suspend fun create(context: Context): SQLiteDatabase {
        // Retrieve the entry point accessor from application context
        val entryPoint = EntryPointAccessors.fromApplication(
            context, 
            DatabaseInitializerEntryPoint::class.java
        )
        
        val helper = entryPoint.getDatabaseHelper()
        return helper.writableDatabase
    }
}
```

### 2. Providing FrameReady values asynchronously to the Hilt Graph

If other components in your Hilt graph require a post-first-frame dependency initialized by `FrameReady`, you can expose it using `@Provides` inside a Hilt module by suspended injection or using a helper provider:

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideAsyncDatabase(): suspend () -> SQLiteDatabase {
        return {
            // Suspends until FrameReady has successfully completed the initialization
            FrameReady.await(DatabaseInitializer::class)
        }
    }
}
```

---

## 🛠 Setup & Installation

### Adding the Dependency

**Option 1: JitPack (Easiest)**
Since `FrameReady` uses standard Maven Publish configuration, you can immediately pull it via JitPack.
Add JitPack to your `settings.gradle.kts` or root `build.gradle`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```
Then add the dependency to your module:
```kotlin
dependencies {
    implementation("com.github.narayan07feb:FrameReady:1.3.0")
}
```

**Option 2: Maven Central**
FrameReady includes a fully configured `maven-publish` plugin. If the repository owner publishes to Sonatype Maven Central, you can use the standard coordinates:
```kotlin
dependencies {
    implementation("com.frameready:frameready:1.3.0")
}
```

---

### Application Configuration

#### Option A: Zero-Config (Auto-Install)

Add the `FrameReadyProvider` tag into your `AndroidManifest.xml`. Declare your startup components inside nested `<meta-data>` nodes with `android:value="post_frame_initializer"`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <provider
            android:name="com.frameready.FrameReadyProvider"
            android:authorities="${applicationId}.frameready"
            android:exported="false">
            <meta-data
                android:name="com.example.demo.AInitializer"
                android:value="post_frame_initializer" />
            <meta-data
                android:name="com.example.demo.BInitializer"
                android:value="post_frame_initializer" />
        </provider>
    </application>
</manifest>
```

### Option B: Seamless handoff bridge from androidx.startup

If you already use Standard App Startup, declare our handoff bridge inside your pre-frame `InitializationProvider`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.frameready.FrameReadyAppInitializer"
        android:value="androidx.startup" />
</provider>
```

### Option C: Manual Registration

De-register the manifest providers and install manually inside your custom `Application.onCreate` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        FrameReady.baselineTtffMs = 500L

        // (Optional) Implement FrameReadyStorage to persist metrics like P50 Medians and stable threshold counters.
        // FrameReady.storage = MyCustomStorageImpl()

        FrameReady.install(this, listOf(
            DatabaseInitializer::class.java,
            BInitializer::class.java
        ))
    }
}
```

---

## 🛡 Advanced Safety Policies & Customization

FrameReady includes enterprise-grade guardrails to ensure robust delivery under edge cases or developer misconfigurations:

### 1. Cumulative & Incremental Registration
`FrameReady.install()` is thread-safe and supports multi-pass installation. If features in modular repositories register elements independently at separate times, they are merged cumulatively into the topological graph. Submissions are finalized only once the first frame has successfully drawn to the screen.

### 2. Android frame trigger
The Class-based `install` (manifest provider) triggers on the first non-trampoline resume, then one `Choreographer` callback, then on API 29+ `registerFrameCommitCallback`. Unknown-splash scanning is opt-in: `trampolineThresholdMs = 500`. Compose apps should call `signalCompositionReady()` from a root `LaunchedEffect`.

### 3. Strict Main-Thread Deadlock Prevention
If `.get(Initializer)` is called on the Main Thread before that initializer completes, the library throws an explicit `IllegalStateException` with a descriptive message rather than silently pausing/deadlocking the main thread, allowing developers to spot violations immediately.

### 4. Custom Trampolines & Flexible Thresholds
Easily register customized splash-screens, transient webviews, or specific deep-link routers that should immediately skip triggering first frame:
```kotlin
// Optional: detect unknown splash activities (off by default)
FrameReady.trampolineThresholdMs = 500L

// Register known trampolines (preferred)
FrameReady.trampolineActivities.add(MyCustomSplashActivity::class.java)
```

### 5. Localized Exception Isolation
If any initializer fails (including runtime crashes or timeouts), the error is isolated locally and the respective deferred outputs are completed exceptionally. Unrelated peer initializers continue running unaffected, while the library resets its consecutive stability counters to protect performance tracking integrity.

### 6. Blocking timeouts (Android only)
On Android, `BACKGROUND` `create()` runs under `runInterruptible`, so `Thread.sleep` can be interrupted when `timeoutMs()` fires. On iOS, cancellation is cooperative only. Blocking native work will not stop when the timeout fires.

```kotlin
class MyLegacySdkInitializer : FrameReadyInitializer<String> {
    override fun timeoutMs(): Long = 3000L // 3-second timeout

    override suspend fun create(context: PlatformContext): String {
        Thread.sleep(5000)
        return "Loaded"
    }
}
```

---

## 🧑‍💻 License

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
```
