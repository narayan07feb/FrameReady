package com.frameready

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Startup metrics report.
 */
data class StartupMetrics(
    val ttffMs: Long,               // Application.onCreate -> first frame callback
    val initStartMs: Long,          // first frame -> first initializer started
    val initCompleteMs: Long,       // first frame -> all initializers completed
    val trampolineSkipped: Boolean,  // whether a trampoline was skipped
    val trampolineSkipCount: Int,   // number of skipped activities
    val initializerCount: Int,      // total count of executed initializers
    val stableLaunchCount: Int,     // consecutive stable launches achieved
    val ttffP50: Long,              // P50 percentile value for stable launches
    val ttffP90: Long,              // P90 percentile value for stable launches
    val ttffP99: Long,              // P99 percentile value for stable launches
    val netImprovementRate: Double,  // percentage of cold-start improvement over baseline
    val coldStartRate: Double = 100.0, // percentage of total launches that are cold starts
    val displayedMs: Long = 0L,       // process start -> first draw completed in milliseconds
    val activityName: String = ""    // name of the activity that completed drawing
)

object FrameReady {
    private const val TAG = "FrameReady"
    private const val PREFS_NAME = "frame_ready_preferences"
    private const val KEY_STABLE_COUNT = "consecutive_stable_launches"
    private const val KEY_TTFF_HISTORY = "ttff_historical_times"
    private const val KEY_TOTAL_LAUNCHES = "total_launches_count"
    private const val KEY_COLD_LAUNCHES = "cold_launches_count"
    private const val DEFAULT_STABLE_THRESHOLD = 100

    private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private var globalAppContext: Context? = null

    // Result store
    private val resultMap = ConcurrentHashMap<Class<Any>, CompletableDeferred<Any?>>()
    private val initializers = ConcurrentHashMap.newKeySet<Class<Any>>()
    private val initializerInstances = ConcurrentHashMap<Class<Any>, FrameReadyInitializer<Any?>>()
    
    private val isInstalled = AtomicBoolean(false)
    private val hasTriggered = AtomicBoolean(false)
    
    @VisibleForTesting
    internal var appOnCreateTime: Long = SystemClock.elapsedRealtime()

    @Volatile
    var contentProviderStartTime: Long = SystemClock.elapsedRealtime()

    private var sortedInitializers: List<Class<Any>>? = null

    private val _metricsFlow = MutableSharedFlow<StartupMetrics>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /**
     * A reactive stream that emits the final metrics once the library stabilizes.
     * Consumers can collect this flow without worrying about memory leaks or overwriting listeners.
     */
    val metricsFlow: SharedFlow<StartupMetrics> = _metricsFlow.asSharedFlow()

    private val isExecutingInUnitTest by lazy {
        runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
    }
    
    // Configurable thresholds for testing/open-source adoption
    var baselineTtffMs: Long = 350L
    var stableThreshold: Int = DEFAULT_STABLE_THRESHOLD

    // Dynamic/Configurable Trampoline overrides
    var trampolineThresholdMs: Long = 500L
    val trampolineActivities = ConcurrentHashMap.newKeySet<Class<out Activity>>()

    // Trampoline activity tracking
    private val activityMap = ConcurrentHashMap<Activity, ActivityEntry>()
    private val activeActivitiesCount = AtomicInteger(0)
    private val trampolineSkipCount = AtomicInteger(0)
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    private val coldLaunchCounted = AtomicBoolean(false)
    private val firstActivityStartedInProcess = AtomicBoolean(false)

    data class ActivityEntry(
        val activity: WeakReference<Activity>,
        val createdAt: Long = SystemClock.elapsedRealtime(),
        var resumedAt: Long = 0L,
        var stoppedAt: Long = 0L,
        var isDestroyed: Boolean = false
    )

    init {
        // Record as soon as this object is loaded
        appOnCreateTime = SystemClock.elapsedRealtime()
    }



    /**
     * Thread-safe cumulative manual/auto configuration entry point.
     */
    fun install(context: Context, initClasses: List<Class<Any>>) {
        if (hasTriggered.get()) {
            Log.w(TAG, "Already triggered first frame. Rejecting cumulative initializer registration.")
            return
        }

        val appContext = context.applicationContext as Application
        globalAppContext = appContext

        if (coldLaunchCounted.compareAndSet(false, true)) {
            libraryScope.launch(Dispatchers.IO) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val coldCount = prefs.getInt(KEY_COLD_LAUNCHES, 0) + 1
                val totalCount = prefs.getInt(KEY_TOTAL_LAUNCHES, 0) + 1
                prefs.edit()
                    .putInt(KEY_COLD_LAUNCHES, coldCount)
                    .putInt(KEY_TOTAL_LAUNCHES, totalCount)
                    .apply()
            }
        }

        synchronized(this) {
            if (initClasses.isNotEmpty()) {
                initializers.addAll(initClasses)
            }

            try {
                sortedInitializers = sort(initializers.toList())
                sortedInitializers?.forEach { clazz ->
                    getDeferred<Any>(clazz)
                }
            } catch (e: Exception) {
                if (!isInstalled.get()) {
                    isInstalled.set(false)
                }
                throw e
            }

            if (isInstalled.compareAndSet(false, true)) {
                // Register Activity Lifecycle callbacks to detect first frame & trampolines
                registerLifecycleCallbacks(appContext)
            } else {
                Log.i(TAG, "Incrementally registered additional initializers. Total is now: ${initializers.size}")
            }
        }
    }

    /**
     * Default install (reads from Manifest via ContentProvider automatically)
     */
    fun install(context: Context) {
        // In the zero-config scenario/auto-install, this is handled by FrameReadyProvider
        Log.i(TAG, "Auto-installed via zero-config provider.")
    }

    /**
     * Retrieves the typed CompletableDeferred for an initializer.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getDeferred(clazz: Class<Any>): CompletableDeferred<T> {
        return resultMap.getOrPut(clazz) { CompletableDeferred() } as CompletableDeferred<T>
    }

    @Suppress("UNCHECKED_CAST")
    private fun getInstance(clazz: Class<Any>): FrameReadyInitializer<Any?> {
        return initializerInstances.getOrPut(clazz) {
            clazz.getDeclaredConstructor().newInstance() as FrameReadyInitializer<Any?>
        }
    }

    /**
     * Non-blocking getter. Returns matching value if ready, or null if pending.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    fun <T, C : FrameReadyInitializer<T>> getOrNull(clazz: Class<C>): T? {
        val deferred = resultMap[clazz as Class<Any>] ?: return null
        return if (deferred.isCompleted) {
            runCatching { deferred.getCompleted() as T }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Thread-safe blocking getter. Throws an explicit IllegalStateException when called on
     * Android's main thread before completion to protect developers from black-screen deadlocks.
     */
    fun <T, C : FrameReadyInitializer<T>> get(clazz: Class<C>): T {
        @Suppress("UNCHECKED_CAST")
        val deferred = resultMap[clazz as Class<Any>] ?: throw IllegalStateException("Initializer ${clazz.simpleName} was never installed.")
        if (!deferred.isCompleted && Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException(
                "Rule 4 Violation & Deadlock Risk! Blocking 'get()' called on the Main thread before " +
                "${clazz.simpleName} completed initialization. Use suspending 'await()' in a coroutine, " +
                "or run this query on a background thread."
            )
        }
        return runBlocking { await(clazz) }
    }

    /**
     * Suspending awaiter. Implements the wait/suspend contract cleanly with adjustable timeout.
     */
    suspend fun <T, C : FrameReadyInitializer<T>> await(
        clazz: Class<C>,
        timeoutMs: Long = 5000L
    ): T {
        @Suppress("UNCHECKED_CAST")
        val deferred = getDeferred<T>(clazz as Class<Any>)
        try {
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            val exception = InitializerTimeoutException(
                "Initializer ${clazz.simpleName} timed out after $timeoutMs ms without producing execution outcomes."
            )
            handleStartupFailure(exception, clazz.name)
            throw exception
        }
    }

    /**
     * Resets the stability count inside SharedPreferences.
     */
    @VisibleForTesting
    internal fun resetStabilityCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_STABLE_COUNT, 0)
            .putInt(KEY_COLD_LAUNCHES, 1)
            .putInt(KEY_TOTAL_LAUNCHES, 1)
            .putString(KEY_TTFF_HISTORY, "")
            .apply()
    }

    /**
     * Generates topological order of initializers via Kahn's algorithm.
     */
    @VisibleForTesting
    internal fun sort(classes: List<Class<Any>>): List<Class<Any>> {
        val allNodes = mutableSetOf<Class<Any>>()
        val adjacencyList = mutableMapOf<Class<Any>, MutableList<Class<Any>>>()
        val inDegree = mutableMapOf<Class<Any>, Int>()

        // Recursively extract all elements to include transitively declared dependency classes
        fun scan(clazz: Class<Any>) {
            if (allNodes.add(clazz)) {
                try {
                    val instance = getInstance(clazz)
                    val deps = instance.dependencies()
                    for (dep in deps) {
                        @Suppress("UNCHECKED_CAST")
                        val depClass = Class.forName(dep) as Class<Any>
                        scan(depClass)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed scanning dependencies for: ${clazz.name}", e)
                    // If instantiation fails during scan, we still allow other nodes to be processed
                }
            }
        }

        classes.forEach { scan(it) }

        // Setup graphs
        allNodes.forEach { node ->
            adjacencyList[node] = mutableListOf()
            inDegree[node] = 0
        }

        // Build edges: dependency -> element (dependency runs first)
        allNodes.forEach { node ->
            try {
                val instance = getInstance(node)
                instance.dependencies().forEach { dep ->
                    @Suppress("UNCHECKED_CAST")
                    val depClass = Class.forName(dep) as Class<Any>
                    adjacencyList[depClass]?.add(node)
                    inDegree[node] = (inDegree[node] ?: 0) + 1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating dependency edges for: ${node.name}", e)
            }
        }

        // Kahn's Sort Algorithm
        val queue = ArrayDeque<Class<Any>>()
        allNodes.forEach { node ->
            if ((inDegree[node] ?: 0) == 0) {
                queue.addLast(node)
            }
        }

        val result = mutableListOf<Class<Any>>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            adjacencyList[node]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) {
                    queue.addLast(neighbor)
                }
            }
        }

        if (result.size != allNodes.size) {
            throw CircularDependencyException(
                "CircularDependencyException: A circular dependency path exists among registered post-frame initializers."
            )
        }

        return result
    }

    private fun registerLifecycleCallbacks(app: Application) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activityMap[activity] = ActivityEntry(WeakReference(activity))
            }

            override fun onActivityStarted(activity: Activity) {
                val count = activeActivitiesCount.incrementAndGet()
                if (count == 1) {
                    if (!firstActivityStartedInProcess.compareAndSet(false, true)) {
                        libraryScope.launch(Dispatchers.IO) {
                            val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            val totalCount = prefs.getInt(KEY_TOTAL_LAUNCHES, 0) + 1
                            prefs.edit().putInt(KEY_TOTAL_LAUNCHES, totalCount).apply()
                        }
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {
                val entry = activityMap[activity] ?: return
                entry.resumedAt = SystemClock.elapsedRealtime()

                val isNotificationStart = Build.VERSION.SDK_INT >= 31 && isNotificationOriginated(activity)
                val isExplicitTrampoline = trampolineActivities.contains(activity::class.java)

                if (isNotificationStart) {
                    // API 31+ Notification routing: skip delay overhead, schedule immediate DecorView draw
                    triggerFirstDraw(activity)
                } else if (isExplicitTrampoline) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Explicit trampoline registered: ${activity.localClassName}, skipping frame trigger.")
                    }
                    trampolineSkipCount.incrementAndGet()
                } else {
                    // Standard trampoline & splash flow
                    val weakActivity = WeakReference(activity)
                    handler.postDelayed({
                        val act = weakActivity.get() ?: return@postDelayed
                        val currentEntry = activityMap[act] ?: return@postDelayed
                        // If Activity continues to remain visible resumed (has not stopped, not finish) -> Real Activity
                        if (!act.isFinishing && !act.isDestroyed && currentEntry.stoppedAt == 0L) {
                            triggerFirstDraw(act)
                        } else {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Trampoline detected: ${act.localClassName}, skipping frame trigger.")
                            }
                            trampolineSkipCount.incrementAndGet()
                        }
                    }, trampolineThresholdMs)
                }
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val entry = activityMap[activity]
                if (entry != null) {
                    entry.stoppedAt = SystemClock.elapsedRealtime()
                }

                // Rule: If app goes to background entirely before first frame renders (active activities count = 0), still run!
                val count = activeActivitiesCount.decrementAndGet()
                if (count == 0 && !hasTriggered.get()) {
                    Log.i(TAG, "App went to background before first frame. Triggering initializers in background.")
                    triggerBackgroundExecution(activity.applicationContext)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                val entry = activityMap.remove(activity)
                if (entry != null) {
                    entry.isDestroyed = true
                }
            }
        }
        lifecycleCallbacks = callbacks
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    private fun isNotificationOriginated(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        return try {
            intent.hasExtra("notification_id") || 
            intent.hasExtra("from_notification") || 
            intent.action?.contains("NOTIFICATION") == true
        } catch (e: Exception) {
            // Protect against BadParcelableException from external intents
            false
        }
    }

    private fun triggerFirstDraw(activity: Activity) {
        if (hasTriggered.compareAndSet(false, true)) {
            // Clean up activity listener to avoid unnecessary overhead after trigger
            unregisterCallbacks(activity.applicationContext)

            if (isExecutingInUnitTest) {
                // Inline fallback for headless test runners
                handler.post {
                    val firstFrameTime = SystemClock.elapsedRealtime()
                    runAll(activity.applicationContext, firstFrameTime, activity.localClassName)
                }
            } else {
                val decorView = activity.window?.decorView
                if (decorView != null) {
                    if (decorView.isAttachedToWindow) {
                        registerOnNextDrawAndPost(activity, decorView)
                    } else {
                        val attachListener = object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                decorView.removeOnAttachStateChangeListener(this)
                                registerOnNextDrawAndPost(activity, decorView)
                            }
                            override fun onViewDetachedFromWindow(v: android.view.View) {}
                        }
                        decorView.addOnAttachStateChangeListener(attachListener)
                    }
                } else {
                    // Fallback to post if decorView is null
                    handler.post {
                        val firstFrameTime = SystemClock.elapsedRealtime()
                        runAll(activity.applicationContext, firstFrameTime, activity.localClassName)
                    }
                }
            }
        }
    }

    private fun registerOnNextDrawAndPost(activity: Activity, decorView: android.view.View) {
        val observer = decorView.viewTreeObserver
        val drawListener = object : android.view.ViewTreeObserver.OnDrawListener {
            private var handled = false
            override fun onDraw() {
                if (handled) return
                handled = true
                // Post removal of listener to avoid exceptions during drawing pass
                handler.post {
                    if (decorView.viewTreeObserver.isAlive) {
                        decorView.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= 29) {
                    decorView.viewTreeObserver.registerFrameCommitCallback {
                        val firstFrameTime = SystemClock.elapsedRealtime()
                        runAll(activity.applicationContext, firstFrameTime, activity.localClassName)
                    }
                } else {
                    decorView.postOnAnimation {
                        val firstFrameTime = SystemClock.elapsedRealtime()
                        runAll(activity.applicationContext, firstFrameTime, activity.localClassName)
                    }
                }
            }
        }
        observer.addOnDrawListener(drawListener)
    }

    private fun triggerBackgroundExecution(context: Context) {
        if (hasTriggered.compareAndSet(false, true)) {
            unregisterCallbacks(context)
            val shadowFrameTime = SystemClock.elapsedRealtime()
            runAll(context, shadowFrameTime, "Background")
        }
    }

    private fun unregisterCallbacks(context: Context) {
        val app = context.applicationContext as? Application
        lifecycleCallbacks?.let { app?.unregisterActivityLifecycleCallbacks(it) }
        lifecycleCallbacks = null
        activityMap.clear() // Clear references completely to prevent context/activity memory leaks
    }

    private fun runAll(context: Context, firstFrameTime: Long, activityName: String? = null) {
        val sorted = sortedInitializers ?: return
        val ttff = firstFrameTime - appOnCreateTime

        val displayed = firstFrameTime - contentProviderStartTime
        
        val metrics = StartupMetrics(
            ttffMs = ttff,
            initStartMs = 0L,
            initCompleteMs = 0L,
            trampolineSkipped = trampolineSkipCount.get() > 0,
            trampolineSkipCount = trampolineSkipCount.get(),
            initializerCount = sorted.size,
            stableLaunchCount = 0,
            ttffP50 = 0L,
            ttffP90 = 0L,
            ttffP99 = 0L,
            netImprovementRate = 0.0,
            coldStartRate = 100.0,
            displayedMs = displayed,
            activityName = activityName ?: ""
        )

        val trackerStart = SystemClock.elapsedRealtime()
        val firstStartTracked = AtomicBoolean(false)
        val initStartMsRef = java.util.concurrent.atomic.AtomicLong(0L)

        // Guarantee globalAppContext is non-null for metrics & SharedPreferences Access
        if (globalAppContext == null) {
            globalAppContext = context.applicationContext
        }

        libraryScope.launch {
            val jobs = sorted.map { clazz ->
                launch {
                    val deferred = getDeferred<Any?>(clazz)
                    try {
                        // Retrieve the cached instance
                        val instance = getInstance(clazz)
                        
                        // Suspend-wait on all declared dependencies. If a dependency completes exceptionally,
                        // depDeferred.await() will throw, which is caught locally inside this try-catch.
                        instance.dependencies().forEach { dep ->
                            @Suppress("UNCHECKED_CAST")
                            val depClass = Class.forName(dep) as Class<Any>
                            val depDeferred = getDeferred<Any?>(depClass)
                            depDeferred.await()
                        }

                        if (firstStartTracked.compareAndSet(false, true)) {
                            // first frame -> first initializer started
                            val offsetMs = SystemClock.elapsedRealtime() - firstFrameTime
                            initStartMsRef.set(offsetMs)
                        }

                        val dispatcher = when (instance.executionThread()) {
                            ExecutionThread.MAIN -> Dispatchers.Main
                            ExecutionThread.BACKGROUND -> Dispatchers.IO
                        }

                        val timeout = instance.timeoutMs()
                        val result = if (timeout > 0 && timeout < Long.MAX_VALUE) {
                            try {
                                kotlinx.coroutines.withTimeout(timeout) {
                                    if (instance.executionThread() == ExecutionThread.BACKGROUND) {
                                        kotlinx.coroutines.runInterruptible(dispatcher) {
                                            kotlinx.coroutines.runBlocking {
                                                instance.create(context)
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(dispatcher) {
                                            instance.create(context)
                                        }
                                    }
                                }
                            } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                                throw InitializerTimeoutException(
                                    "Initializer ${clazz.simpleName} timed out after ${timeout}ms. " +
                                    "Ensure internal heavy operations are optimized or configured with higher timeouts."
                                )
                            }
                        } else {
                            kotlinx.coroutines.withContext(dispatcher) {
                                instance.create(context)
                            }
                        }
                        deferred.complete(result)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed executing FrameReadyInitializer: ${clazz.name}", e)
                        deferred.completeExceptionally(e)
                        handleStartupFailure(e, clazz.name)
                        // Terminate downstream execution gracefully via local completion exception,
                        // without throwing uncaught exceptions to cascade-cancel the entire launch tree.
                    }
                }
            }

            jobs.joinAll()

            // All final executions resolved! Report metrics
            val endTracker = SystemClock.elapsedRealtime()
            val initCompleteMs = endTracker - firstFrameTime
            
            // Generate stable statistics across launches
            handleStartupSuccess(context, metrics.copy(
                initStartMs = initStartMsRef.get(),
                initCompleteMs = initCompleteMs
            ), activityName)
        }
    }

    private fun handleStartupSuccess(context: Context, partialMetrics: StartupMetrics, activityName: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentStableCount = prefs.getInt(KEY_STABLE_COUNT, 0) + 1
        
        prefs.edit().putInt(KEY_STABLE_COUNT, currentStableCount).apply()

        // Append to history list
        val historyStr = prefs.getString(KEY_TTFF_HISTORY, "") ?: ""
        val historyList = if (historyStr.isEmpty()) mutableListOf() else historyStr.split(",").mapNotNull { it.toLongOrNull() }.toMutableList()
        
        historyList.add(partialMetrics.ttffMs)
        if (historyList.size > 100) {
            historyList.removeAt(0)
        }
        prefs.edit().putString(KEY_TTFF_HISTORY, historyList.joinToString(",")).apply()

        // Calculate exact percentiles
        val sortedHistory = historyList.sorted()
        val size = sortedHistory.size
        val p50 = if (size > 0) sortedHistory[(size * 0.5).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs
        val p90 = if (size > 0) sortedHistory[(size * 0.9).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs
        val p99 = if (size > 0) sortedHistory[(size * 0.99).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs

        // Calculate improvement against baseline
        // Formula: (baselineTTFF - libraryTTFF) / baselineTTFF * 100
        val netImprovement = if (baselineTtffMs > 0) {
            ((baselineTtffMs - partialMetrics.ttffMs).toDouble() / baselineTtffMs.toDouble()) * 100.0
        } else {
            0.0
        }

        val coldCount = prefs.getInt(KEY_COLD_LAUNCHES, 1)
        val totalCount = prefs.getInt(KEY_TOTAL_LAUNCHES, 1).coerceAtLeast(coldCount)
        val computedColdStartRate = (coldCount.toDouble() / totalCount.toDouble()) * 100.0

        val completedMetrics = partialMetrics.copy(
            stableLaunchCount = currentStableCount,
            ttffP50 = p50,
            ttffP90 = p90,
            ttffP99 = p99,
            netImprovementRate = netImprovement,
            coldStartRate = computedColdStartRate
        )

        val displayedLogName = activityName ?: "App"
        Log.i(TAG, "ActivityTaskManager [FrameReady-Logged]: Displayed $displayedLogName: +${completedMetrics.displayedMs}ms (coldStartRate: ${completedMetrics.coldStartRate}%)")

        // Only emit to flow if N stable launches is satisfied
        if (currentStableCount >= stableThreshold) {
            _metricsFlow.tryEmit(completedMetrics)
        }
    }

    private fun handleStartupFailure(t: Throwable, componentName: String) {
        Log.e(TAG, "Initialization failure triggered stability counter reset. Failed element: $componentName", t)
        // Reset count to zero
        val ctx = globalAppContext ?: activityMap.keys.firstOrNull()
        if (ctx != null) {
            handler.post {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_STABLE_COUNT, 0).apply()
            }
        }
    }

    @VisibleForTesting
    fun resetAllForTesting() {
        resultMap.clear()
        initializers.clear()
        initializerInstances.clear()
        isInstalled.set(false)
        hasTriggered.set(false)
        activityMap.clear()
        activeActivitiesCount.set(0)
        trampolineSkipCount.set(0)
        lifecycleCallbacks = null
        globalAppContext = null
        coldLaunchCounted.set(false)
        firstActivityStartedInProcess.set(false)
    }
}
