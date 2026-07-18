package com.frameready

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * FrameReady: parallel, dependency-ordered, post-first-frame initialization for Android and iOS.
 *
 * This is the ONE shared implementation used by every platform — there is no per-platform copy
 * of the coordination engine. The only things that differ per platform are primitives
 * ([currentTimeMs], [platformLog], [isMainThread], [instantiateNoArg], [executeOnDispatcher]) and
 * how the "first frame" trigger is wired up:
 *
 * - **Android (classic View-based)**: auto-wired via `ActivityLifecycleCallbacks` — see the
 *   `FrameReady.install(Context, List<Class<Any>>)` extension in `androidMain`, typically reached
 *   through [FrameReadyProvider]'s manifest auto-discovery.
 * - **Android (Compose) / iOS (Compose Multiplatform)**: call [install] once (e.g. in
 *   `Application.onCreate` / your iOS entry point), then call [signalCompositionReady] exactly
 *   once from a `LaunchedEffect(Unit)` in your app's ROOT composable:
 *   ```kotlin
 *   @Composable
 *   fun App() {
 *       LaunchedEffect(Unit) { FrameReady.signalCompositionReady(context) }
 *       // navigation between screens does NOT re-trigger initialization —
 *       // it runs exactly once per process, the same as Android's classic Activity path.
 *   }
 *   ```
 *
 * ## Consuming results (same API on every platform)
 * ```kotlin
 * val analytics = FrameReady.await<AnalyticsInitializer>()
 * val flow = FrameReady.asStateFlow<AnalyticsInitializer>()
 * ```
 *
 * ## iOS reflection limitation
 * Kotlin/Native cannot instantiate classes via reflection. Register a factory for every
 * initializer with [registerFactory] before calling [install]:
 * ```kotlin
 * FrameReady.registerFactory(AnalyticsInitializer::class) { AnalyticsInitializer() }
 * ```
 */
object FrameReady {
    internal const val TAG = "FrameReady"

    internal val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ─── Result store & registration state — identical on every platform ──────
    internal val resultMap = SafeMap<KClass<Any>, CompletableDeferred<Any?>>()
    internal val initializers = SafeSet<KClass<Any>>()
    private val initializerInstances = SafeMap<KClass<Any>, FrameReadyInitializer<Any?>>()
    private val initializerFactories = SafeMap<KClass<Any>, () -> FrameReadyInitializer<Any?>>()
    private val stateFlows = SafeMap<KClass<Any>, MutableStateFlow<Any?>>()
    private val disabledInitializers = SafeSet<KClass<Any>>()

    private val isInstalled = SafeFlag(false)
    internal val hasTriggered = SafeFlag(false)
    private val coldLaunchCounted = SafeFlag(false)

    @Volatile
    internal var sortedInitializers: List<KClass<Any>>? = null

    @Volatile
    private var globalContext: PlatformContext? = null

    /** Baseline used to compute [StartupMetrics.ttffMs] — set once when this object loads. */
    private val objectLoadTimeMs: Long = currentTimeMs()

    /**
     * Best-effort process start time, used to compute [StartupMetrics.displayedMs].
     * Android's `FrameReadyProvider` overrides this with a precise value derived from
     * `Process.getStartUptimeMillis()`. Platforms without a more precise signal keep the default.
     */
    @Volatile
    var processStartMs: Long = currentTimeMs()

    private val _metricsFlow = MutableSharedFlow<StartupMetrics>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** A reactive stream that emits final metrics once the library stabilizes after a launch. */
    val metricsFlow: SharedFlow<StartupMetrics> = _metricsFlow.asSharedFlow()

    var baselineTtffMs: Long = 0L

    /**
     * Optional storage interface enabling historical telemetry (cold-start rate, TTFF
     * percentiles, etc.). If left null, metrics are emitted with default/zeroed history fields.
     */
    var storage: FrameReadyStorage? = null

    /**
     * Timeout before running initializers anyway if neither the platform auto-trigger nor
     * [signalCompositionReady] ever fires (e.g. a background process/extension). Default 10s.
     */
    var headlessTimeoutMs: Long = 10_000L

    /**
     * Hook a platform bridge (e.g. Android's ActivityLifecycleCallbacks wiring) can register to
     * clear its own extra state when [resetAllForTesting] runs, keeping a single reset call
     * sufficient for tests regardless of which platform-specific bridge is in play.
     */
    internal var platformResetHook: (() -> Unit)? = null

    // ─── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers initializers and prepares the execution engine. Safe to call multiple times
     * before the first frame — registrations accumulate. Call once per process, typically at
     * app startup (`Application.onCreate`, iOS entry point).
     */
    fun install(context: PlatformContext, classes: List<KClass<out FrameReadyInitializer<*>>>) {
        if (hasTriggered.get()) {
            platformLog(TAG, "Already triggered first frame. Rejecting late initializer registration.", 'W')
            return
        }
        globalContext = context

        if (coldLaunchCounted.compareAndSet(false, true)) {
            storage?.let { store ->
                libraryScope.launch {
                    store.setColdLaunchCount(store.getColdLaunchCount() + 1)
                    store.setTotalLaunchCount(store.getTotalLaunchCount() + 1)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        if (classes.isNotEmpty()) {
            initializers.addAll(classes as List<KClass<Any>>)
        }

        sortedInitializers = sort(initializers.toList())
        sortedInitializers?.forEach { clazz -> getDeferred<Any>(clazz) }

        if (isInstalled.compareAndSet(false, true)) {
            libraryScope.launch {
                delay(headlessTimeoutMs)
                if (!hasTriggered.get()) {
                    platformLog(TAG, "No first-frame trigger within ${headlessTimeoutMs}ms — running initializers headlessly.", 'I')
                    markFirstFrame(context, currentTimeMs())
                }
            }
        } else {
            platformLog(TAG, "Incrementally registered additional initializers. Total: ${initializers.size}", 'I')
        }
    }

    /**
     * Signals that the first meaningful composition has been drawn. This is the primary trigger
     * for Compose / Compose Multiplatform apps on every platform, and the ONLY trigger on iOS
     * (which has no Activity-lifecycle equivalent).
     *
     * Call exactly once from a `LaunchedEffect(Unit)` in your app's ROOT composable. Subsequent
     * calls (e.g. from screen navigation) are no-ops — initializers run exactly once per process.
     */
    fun signalCompositionReady(context: PlatformContext) {
        if (!isInstalled.get()) {
            platformLog(TAG, "signalCompositionReady() called before install(). Call install() first.", 'W')
            return
        }
        markFirstFrame(context, currentTimeMs())
    }

    /**
     * Internal first-frame trigger. Platform-specific auto-trigger mechanisms (Android's
     * ActivityLifecycleCallbacks + Choreographer frame-commit) call this directly with a
     * precisely-measured [firstFrameMs] instead of going through [signalCompositionReady].
     */
    internal fun markFirstFrame(
        context: PlatformContext,
        firstFrameMs: Long,
        activityName: String? = null,
        trampolineSkipCount: Int = 0
    ) {
        if (hasTriggered.compareAndSet(false, true)) {
            runAll(context, firstFrameMs, activityName, trampolineSkipCount)
        }
    }

    // ─── Disable / retry ────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun disable(clazz: KClass<out FrameReadyInitializer<*>>) {
        if (hasTriggered.get()) {
            platformLog(TAG, "disable() called after first frame — ${clazz.simpleName} already ran or is running.", 'W')
            return
        }
        disabledInitializers.add(clazz as KClass<Any>)
        platformLog(TAG, "Disabled initializer: ${clazz.simpleName}", 'I')
    }

    @Suppress("UNCHECKED_CAST")
    fun isDisabled(clazz: KClass<out FrameReadyInitializer<*>>): Boolean =
        disabledInitializers.contains(clazz as KClass<Any>)

    /**
     * Re-runs a failed or completed initializer, replacing its previous result. Dependents are
     * NOT automatically re-run. A no-op if the initializer is still running.
     */
    @Suppress("UNCHECKED_CAST")
    fun retry(clazz: KClass<out FrameReadyInitializer<*>>) {
        val classKey = clazz as KClass<Any>
        if (!initializers.contains(classKey)) {
            throw IllegalStateException(
                "${clazz.simpleName} was never registered with FrameReady. Call install() first."
            )
        }
        val existing = resultMap[classKey]
        if (existing != null && !existing.isCompleted) {
            platformLog(TAG, "retry() ignored — ${clazz.simpleName} is still running.", 'W')
            return
        }
        val freshDeferred = CompletableDeferred<Any?>()
        resultMap[classKey] = freshDeferred
        stateFlows[classKey]?.value = null

        val ctx = globalContext ?: run {
            platformLog(TAG, "retry() called before any context was available — ignoring.", 'E')
            return
        }
        libraryScope.launch {
            try {
                val instance = getInstance(classKey)
                val timeout = instance.timeoutMs()
                val result = if (timeout in 1 until Long.MAX_VALUE) {
                    try {
                        withTimeout(timeout) {
                            executeOnDispatcher(instance.executionThread()) { instance.create(ctx) }
                        }
                    } catch (te: TimeoutCancellationException) {
                        throw InitializerTimeoutException("${clazz.simpleName} timed out after ${timeout}ms on retry.")
                    }
                } else {
                    executeOnDispatcher(instance.executionThread()) { instance.create(ctx) }
                }
                freshDeferred.complete(result)
                stateFlows[classKey]?.value = result
                platformLog(TAG, "Retry succeeded: ${clazz.simpleName}", 'I')
            } catch (e: Throwable) {
                platformLog(TAG, "Retry failed: ${clazz.simpleName}", 'E', e)
                freshDeferred.completeExceptionally(e)
            }
        }
    }

    /**
     * Registers a factory used to instantiate [clazz] instead of reflection.
     * **Required on iOS** — Kotlin/Native has no reflective no-arg instantiation. Also useful on
     * Android for DI integration (Hilt, Koin, Dagger).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> registerFactory(
        clazz: KClass<out FrameReadyInitializer<T>>,
        factory: () -> FrameReadyInitializer<T>
    ) {
        initializerFactories[clazz as KClass<Any>] = factory as () -> FrameReadyInitializer<Any?>
        initializerInstances.remove(clazz)
    }

    // ─── Result access ──────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    internal fun <T> getDeferred(clazz: KClass<Any>): CompletableDeferred<T> =
        resultMap.getOrPut(clazz) { CompletableDeferred() } as CompletableDeferred<T>

    @Suppress("UNCHECKED_CAST")
    fun <T, C : FrameReadyInitializer<T>> asDeferred(clazz: KClass<C>): Deferred<T> =
        getDeferred<T>(clazz as KClass<Any>)

    /**
     * Returns a [StateFlow] that emits `null` while [clazz] is pending, then its result once
     * done. The same instance is returned on every call for a given [clazz] — safe to bind as a
     * `@Singleton` in DI or call repeatedly from `remember` in Compose.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    fun <T, C : FrameReadyInitializer<T>> asStateFlow(clazz: KClass<C>): StateFlow<T?> {
        val typedClazz: KClass<C> = clazz
        return stateFlows.getOrPut(clazz as KClass<Any>) {
            val state = MutableStateFlow<Any?>(getOrNull<T, C>(typedClazz))
            if (state.value == null) {
                libraryScope.launch {
                    val result = runCatching { await<T, C>(typedClazz) }.getOrNull()
                    if (result != null) state.value = result
                }
            }
            state
        } as StateFlow<T?>
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    fun <T, C : FrameReadyInitializer<T>> getOrNull(clazz: KClass<C>): T? {
        val deferred = resultMap[clazz as KClass<Any>] ?: return null
        return if (deferred.isCompleted) runCatching { deferred.getCompleted() as T }.getOrNull() else null
    }

    /**
     * Blocking accessor. Throws if called on the main/UI thread before [clazz] has completed —
     * that combination is a deadlock risk (the initializer may itself need the main thread).
     * Prefer suspending [await] in a coroutine.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T, C : FrameReadyInitializer<T>> get(clazz: KClass<C>): T {
        val typedClazz: KClass<C> = clazz
        val deferred = resultMap[clazz as KClass<Any>]
            ?: throw IllegalStateException("Initializer ${clazz.simpleName} was never installed.")
        if (!deferred.isCompleted && isMainThread()) {
            throw IllegalStateException(
                "Rule 4 Violation & Deadlock Risk! Blocking 'get()' called on the Main thread before " +
                "${clazz.simpleName} completed initialization. Use suspending 'await()' in a coroutine, " +
                "or run this query on a background thread."
            )
        }
        return runBlocking { await<T, C>(typedClazz) }
    }

    /** Suspends until [clazz]'s result is available, then returns it. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T, C : FrameReadyInitializer<T>> await(
        clazz: KClass<C>,
        timeoutMs: Long = 5000L
    ): T {
        if (initializers.size > 0 && !initializers.contains(clazz as KClass<Any>)) {
            throw IllegalStateException(
                "${clazz.simpleName} was never registered with FrameReady. Call install() first."
            )
        }
        val deferred = getDeferred<T>(clazz as KClass<Any>)
        try {
            return withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            val exception = InitializerTimeoutException(
                "Initializer ${clazz.simpleName} timed out after $timeoutMs ms without producing execution outcomes."
            )
            handleStartupFailure(exception, clazz as KClass<Any>)
            throw exception
        }
    }

    fun resetStability() {
        storage?.setStableLaunchCount(0)
    }

    // ─── Internal: topological sort ─────────────────────────────────────────────

    internal fun sort(classes: List<KClass<Any>>): List<KClass<Any>> {
        val allNodes = mutableSetOf<KClass<Any>>()
        val adjacencyList = mutableMapOf<KClass<Any>, MutableList<KClass<Any>>>()
        val inDegree = mutableMapOf<KClass<Any>, Int>()

        fun scan(clazz: KClass<Any>) {
            if (allNodes.add(clazz)) {
                try {
                    val instance = getInstance(clazz)
                    for (dep in instance.dependencies()) {
                        @Suppress("UNCHECKED_CAST")
                        scan(dep as KClass<Any>)
                    }
                } catch (e: Exception) {
                    platformLog(TAG, "Failed scanning dependencies for: ${clazz.simpleName}", 'E', e)
                }
            }
        }

        classes.forEach { scan(it) }

        allNodes.forEach { node ->
            adjacencyList[node] = mutableListOf()
            inDegree[node] = 0
        }

        allNodes.forEach { node ->
            try {
                getInstance(node).dependencies().forEach { dep ->
                    @Suppress("UNCHECKED_CAST")
                    val depClass = dep as KClass<Any>
                    adjacencyList[depClass]?.add(node)
                    inDegree[node] = (inDegree[node] ?: 0) + 1
                }
            } catch (e: Exception) {
                platformLog(TAG, "Failed creating dependency edges for: ${node.simpleName}", 'E', e)
            }
        }

        val queue = ArrayDeque<KClass<Any>>()
        allNodes.forEach { if ((inDegree[it] ?: 0) == 0) queue.addLast(it) }

        val result = mutableListOf<KClass<Any>>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            adjacencyList[node]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) queue.addLast(neighbor)
            }
        }

        if (result.size != allNodes.size) {
            throw CircularDependencyException(
                "CircularDependencyException: A circular dependency path exists among registered post-frame initializers."
            )
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    internal fun getInstance(clazz: KClass<Any>): FrameReadyInitializer<Any?> {
        return initializerInstances.getOrPut(clazz) {
            val factory = initializerFactories[clazz]
            if (factory != null) {
                factory()
            } else {
                instantiateNoArg(clazz) as FrameReadyInitializer<Any?>
            }
        }
    }

    // ─── Core parallel runner — identical on every platform ─────────────────────

    internal fun runAll(
        context: PlatformContext,
        firstFrameTime: Long,
        activityName: String?,
        trampolineSkipCount: Int
    ) {
        val sorted = sortedInitializers ?: return

        disabledInitializers.forEachSnapshot { clazz ->
            if (sorted.contains(clazz)) {
                getDeferred<Any?>(clazz).completeExceptionally(
                    DisabledInitializerException("${clazz.simpleName} was disabled via FrameReady.disable() and will not run.")
                )
                platformLog(TAG, "Skipping disabled initializer: ${clazz.simpleName}", 'I')
            }
        }

        val active = if (disabledInitializers.isEmpty) sorted else sorted.filter { !disabledInitializers.contains(it) }

        val ttff = firstFrameTime - objectLoadTimeMs
        val displayed = firstFrameTime - processStartMs
        if (globalContext == null) globalContext = context

        val firstStartTracked = SafeFlag(false)
        val initStartMsRef = SafeLongRef(0L)

        libraryScope.launch {
            val jobs = active.map { clazz ->
                launch {
                    val deferred = getDeferred<Any?>(clazz)
                    try {
                        val instance = getInstance(clazz)

                        instance.dependencies().forEach { dep ->
                            @Suppress("UNCHECKED_CAST")
                            getDeferred<Any?>(dep as KClass<Any>).await()
                        }

                        if (firstStartTracked.compareAndSet(false, true)) {
                            initStartMsRef.set(currentTimeMs() - firstFrameTime)
                        }

                        val timeout = instance.timeoutMs()
                        val result = if (timeout in 1 until Long.MAX_VALUE) {
                            try {
                                withTimeout(timeout) {
                                    executeOnDispatcher(instance.executionThread()) { instance.create(context) }
                                }
                            } catch (te: TimeoutCancellationException) {
                                throw InitializerTimeoutException(
                                    "Initializer ${clazz.simpleName} timed out after ${timeout}ms."
                                )
                            }
                        } else {
                            executeOnDispatcher(instance.executionThread()) { instance.create(context) }
                        }
                        deferred.complete(result)
                    } catch (e: Throwable) {
                        platformLog(TAG, "Failed executing FrameReadyInitializer: ${clazz.simpleName}", 'E', e)
                        deferred.completeExceptionally(e)
                        handleStartupFailure(e, clazz)
                    }
                }
            }

            jobs.joinAll()

            val initCompleteMs = currentTimeMs() - firstFrameTime
            val partialMetrics = StartupMetrics(
                ttffMs = ttff,
                initStartMs = initStartMsRef.get(),
                initCompleteMs = initCompleteMs,
                trampolineSkipped = trampolineSkipCount > 0,
                trampolineSkipCount = trampolineSkipCount,
                initializerCount = active.size,
                stableLaunchCount = 0,
                ttffP50 = 0L, ttffP90 = 0L, ttffP99 = 0L,
                netImprovementRate = 0.0,
                coldStartRate = 100.0,
                displayedMs = displayed,
                activityName = activityName ?: ""
            )
            handleStartupSuccess(partialMetrics, activityName)
        }
    }

    private fun handleStartupSuccess(partialMetrics: StartupMetrics, activityName: String?) {
        val displayedLogName = activityName ?: "App"
        val netImprovement = if (baselineTtffMs > 0) {
            ((baselineTtffMs - partialMetrics.ttffMs).toDouble() / baselineTtffMs.toDouble()) * 100.0
        } else 0.0

        val store = storage
        if (store == null) {
            val defaultMetrics = partialMetrics.copy(
                stableLaunchCount = 1,
                netImprovementRate = netImprovement,
                coldStartRate = 100.0
            )
            platformLog(TAG, "Displayed $displayedLogName: +${defaultMetrics.displayedMs}ms", 'I')
            _metricsFlow.tryEmit(defaultMetrics)
            return
        }

        val currentStableCount = store.getStableLaunchCount() + 1
        store.setStableLaunchCount(currentStableCount)

        val historyList = store.getTtffHistory().toMutableList()
        historyList.add(partialMetrics.ttffMs)
        if (historyList.size > 100) historyList.removeAt(0)
        store.setTtffHistory(historyList)

        val sortedHistory = historyList.sorted()
        val size = sortedHistory.size
        val p50 = if (size > 0) sortedHistory[(size * 0.5).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs
        val p90 = if (size > 0) sortedHistory[(size * 0.9).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs
        val p99 = if (size > 0) sortedHistory[(size * 0.99).toInt().coerceAtMost(size - 1)] else partialMetrics.ttffMs

        val coldCount = store.getColdLaunchCount().coerceAtLeast(1)
        val totalCount = store.getTotalLaunchCount().coerceAtLeast(coldCount)
        val computedColdStartRate = (coldCount.toDouble() / totalCount.toDouble()) * 100.0

        val completedMetrics = partialMetrics.copy(
            stableLaunchCount = currentStableCount,
            ttffP50 = p50, ttffP90 = p90, ttffP99 = p99,
            netImprovementRate = netImprovement,
            coldStartRate = computedColdStartRate
        )
        platformLog(TAG, "Displayed $displayedLogName: +${completedMetrics.displayedMs}ms", 'I')
        _metricsFlow.tryEmit(completedMetrics)
    }

    internal fun handleStartupFailure(t: Throwable, clazz: KClass<Any>) {
        platformLog(TAG, "Initialization failure. Failed element: ${clazz.simpleName}", 'E', t)
        storage?.let { store ->
            libraryScope.launch { store.setStableLaunchCount(0) }
        }
    }

    /** Resets all engine state, including any platform bridge's extra state (e.g. Android lifecycle wiring). */
    fun resetAllForTesting() {
        resultMap.clear()
        initializers.clear()
        initializerInstances.clear()
        initializerFactories.clear()
        stateFlows.clear()
        disabledInitializers.clear()
        isInstalled.set(false)
        hasTriggered.set(false)
        coldLaunchCounted.set(false)
        sortedInitializers = null
        globalContext = null
        platformResetHook?.invoke()
    }
}
