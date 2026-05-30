# FrameReady 🚀

`FrameReady` is a high-performance, lightweight, and production-ready Android startup library designed to optimize app cold-start times. 

While standard `androidx.startup` (App Startup) runs initializers **synchronously on the main thread during `ContentProvider.onCreate()`** (blocking the UI before the first frame is drawn), `FrameReady` defers initialization **until after the real first frame has been successfully drawn to the screen**.

---

## 🌟 Key Features

- **Double-Buffered Frame Deferral**: Bypasses critical pre-frame phases, launching tasks on the first Choreographer callback frame.
- **Topological Sorting (Kahn's Sort)**: Automatically builds and validates dependency graphs at install-time.
- **Declarative Thread Routing**: Execute heavy initializers on `Dispatchers.IO` (BACKGROUND) or light UI tasks on `Dispatchers.Main` (MAIN) seamlessly.
- **Thread-safe Wait/Suspend Contract**: If a consumer requests an initializer's result via `await()` before it is ready, the coroutine **suspends** and resumes automatically.
- **Deterministic Trampoline Skip**: Tracks activity lifetimes to ignore transient visible routing/deep-linking activities (Splash/Notification trampolines), executing only after the user-visible primary activity draws.
- **Stable Calibrated Metrics**: Retains launch latency. Auto-calculates historical TTFF (P50, P90, P99) and cold-start improvement index after reaching a customizable stability threshold (e.g. 100 successful runs) with auto-resets on failure.
- **Zero-Config Manifest Merging**: Automatic component discovery using standard ContentProvider meta-data declarations.

---

## 📦 When to use this vs. standard App Startup?

| Feature | Standard `androidx.startup` | `FrameReady` |
| :--- | :--- | :--- |
| **Execution Window** | Pre-First Frame (`onCreate` Provider) | **Post-First Frame** (Choreographer Frame callback) |
| **Main Thread Impact** | Direct block (increases cold start times) | **Non-blocking** (runs concurrently on IO or Main) |
| **Target Components** | Crash reporters, Loggers, Analytics setup | Firebase, DB pre-migrations, Ad SDKs, Image-caches |
| **Access Flow** | In-place return | **Asynchronous `await()` / Suspend-Resume** |
| **Trampoline Safety** | N/A (runs before any Activity) | **Yes** (skips transient routing screens) |

---

## 🛠 Core Integration

### 1. Declaring a FrameReadyInitializer

Implement `FrameReadyInitializer<T>` to declare your task, its dependencies, and target thread context:

```kotlin
import android.content.Context
import com.frameready.FrameReadyInitializer
import com.frameready.ExecutionThread

class AInitializer : FrameReadyInitializer<String> {
    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
    
    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: Context): String {
        // Perform file / network / disk setup
        return "Core Config Active"
    }
}
```

If task `B` depends on task `A`'s finished output, declare it under `dependencies()`:

```kotlin
class BInitializer : FrameReadyInitializer<Database> {
    override fun dependencies() = listOf(AInitializer::class.java)

    override suspend fun create(context: Context): Database {
        // A is guaranteed to be finished here. Safe to call getOrNull!
        val config = FrameReady.getOrNull(AInitializer::class.java)!!
        return Database.init(context, config)
    }
}
```

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
    val database = FrameReady.await(BInitializer::class.java)
    database.queryHistory()
}
```

### Rule 3 — Cycle-detection at install-time (fast-failure)
If a dependency path is cyclic (e.g., `A -> B -> A`), `FrameReady` identifies this immediately during installation and throws a `CircularDependencyException` **before starting any initializer**.

### Rule 4 — No Main-thread blocking
`await()` is a `suspend` function and does not block. In addition, the synchronous `.get()` method will write warning traces if called on the primary Main Looper.

### Rule 5 — Timeout protection
Specify optional timeouts to prevent endless lockouts:
```kotlin
suspend val db = FrameReady.await(BInitializer::class.java, timeoutMs = 3000L)
// Throws InitializerTimeoutException if B takes longer than 3 seconds
```

---

## 🎪 Trampoline Activity Lifecycle Handling

Many apps launch an invisible routing activity first (`SplashActivity`, Deep-link router, Notification dispatcher), which launches the real `MainActivity` and calls `finish()` instantly.

If a startup SDK simply hooks onto the first activity's resume, it will execute too early—on a window the user never sees.

`FrameReady` solves this by tracking activity state transitions:
1. When an Activity enters `onActivityResumed`, register a delayed handler check for **500ms** (default `TRAMPOLINE_THRESHOLD`).
2. If the Activity stops (`onActivityStopped`) or finishes (`isFinishing`) within this 500ms window, the library flags it as a **trampoline activity** and skips its Choreographer registration.
3. The frame trigger registers **only on the first Activity that remains active beyond the threshold**.
4. On **Android 12+ (API 31+)**, notification trampolines are restricted by the OS, so the library skips the 500ms delay for notification-originated intents, executing immediately on resume to optimize routing.

---

## 📊 Cold Start Improvement & Stability Gates

To provide accurate performance indicators, the library maintains a stability gate block:
- Keeps a **stable consecutive launches counter** in SharedPreferences.
- **Resets the counter back to 0** if any InitializerTimeoutException or initialization execution crashes occur.
- Computes actual **P50, P90, and P99 percentiles** of TTFF dynamically past the threshold.
- Reports metrics via a listener hook once the launch counts achieve calibration (e.g., `N = 100` consecutive faultless launches):

```kotlin
FrameReady.setMetricsListener { metrics ->
    // Forward P50/P99 times and net cold-start gains to custom collectors
    FirebasePerformance.newTrace("cold_start_metrics").apply {
        putMetric("ttff_p50", metrics.ttffP50)
        putMetric("ttff_p99", metrics.ttffP99)
        putMetric("net_improvement_percentage", metrics.netImprovementRate.toLong())
        stop()
    }
}
```

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
    override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()
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
            FrameReady.await(DatabaseInitializer::class.java)
        }
    }
}
```

---

## 🛠 Setup & Installation

### Option A: Zero-Config (Auto-Install)

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
        super.onCreate()
        FrameReady.install(this, listOf(
            AInitializer::class.java,
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

### 2. Double-Buffered Frame Rasterization
Instead of triggering immediately when a callback is queued, FrameReady uses a **double-buffered postFrameCallback loop**. This guarantees that the first layout pass is fully rasterized and visual pixels on the screen are completely rendered before initializers are granted compute resources.

### 3. Strict Main-Thread Deadlock Prevention
If `.get(Initializer)` is called on the Main Thread before that initializer completes, the library throws an explicit `IllegalStateException` with a descriptive message rather than silently pausing/deadlocking the main thread, allowing developers to spot violations immediately.

### 4. Custom Trampolines & Flexible Thresholds
Easily register customized splash-screens, transient webviews, or specific deep-link routers that should immediately skip triggering first frame:
```kotlin
// Extend or restrict the trampoline scan delay
FrameReady.trampolineThresholdMs = 300L

// Register custom activities that are known trampolines
FrameReady.trampolineActivities.add(MyCustomSplashActivity::class.java)
```

### 5. Localized Exception Isolation
If any initializer fails (including runtime crashes or timeouts), the error is isolated locally and the respective deferred outputs are completed exceptionally. Unrelated peer initializers continue running unaffected, while the library resets its consecutive stability counters to protect performance tracking integrity.

### 6. Blocking Thread Interruption & Timeouts
For legacy SDKs or tasks containing non-suspendable blocking work (e.g. `Thread.sleep` or synchronous socket/disk reads), FrameReady executes them wrapped in `kotlinx.coroutines.runInterruptible`.

You can set custom safety timeout limits per initializer:
```kotlin
class MyLegacySdkInitializer : FrameReadyInitializer<String> {
    override fun timeoutMs(): Long = 3000L // 3-second timeout

    override suspend fun create(context: Context): String {
        // Even though this blocks, it will be interrupted at 3000ms!
        Thread.sleep(5000) 
        return "Loaded"
    }
}
```
If a timeout is hit, FrameReady triggers `Thread.interrupt()` executing the blocked block, raises an `InterruptedException` to release the thread instantly, and completes exceptionally with `InitializerTimeoutException`.

---

## 🧑‍💻 License

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
```
