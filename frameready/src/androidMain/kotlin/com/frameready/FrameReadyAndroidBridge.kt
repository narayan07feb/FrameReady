package com.frameready

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Android-specific glue on top of the shared [FrameReady] engine.
 *
 * This file adds NO coordination logic of its own — every call here forwards straight into the
 * one shared implementation. It provides two things:
 *
 * 1. **A legacy `java.lang.Class`-based [install] overload**, source/binary compatible with
 *    pre-KMP releases, used by [FrameReadyProvider]'s manifest auto-discovery. This is the ONLY
 *    Class-based entry point kept — every other API (`await`, `getOrNull`, `disable`, etc.) is
 *    exclusively `KClass`-based (`SomeInitializer::class`, not `::class.java`) so the public
 *    surface stays a single, unambiguous set of members on `object FrameReady`.
 * 2. **Classic View-based auto-trigger**: `ActivityLifecycleCallbacks` + trampoline detection +
 *    `Choreographer` frame-commit timing, wired up by the `Class`-based [install] above.
 *
 * For Compose on Android — including the single-Activity, multiple-composable CMP pattern —
 * prefer `FrameReady.install(context, listOf(SomeInit::class))` (the KMP-style member, from
 * `commonMain`) together with `FrameReady.signalCompositionReady(context)` from your root
 * composable's `LaunchedEffect(Unit)` — the exact same pattern used on iOS.
 */

private val activityMap = ConcurrentHashMap<Activity, ActivityEntry>()
private val activeActivitiesCount = AtomicInteger(0)
private val trampolineSkipCount = AtomicInteger(0)
private val firstActivityStartedInProcess = AtomicBoolean(false)
private val lifecycleAttached = AtomicBoolean(false)
private var registeredLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

private val isExecutingInUnitTest by lazy {
    runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
}

private class ActivityEntry(
    val activity: WeakReference<Activity>,
    val createdAt: Long = SystemClock.elapsedRealtime(),
    var resumedAt: Long = 0L,
    var stoppedAt: Long = 0L,
    var isDestroyed: Boolean = false
)

private var _trampolineThresholdMs: Long = 500L

/** Delay before an Activity that stops without surviving is considered a trampoline. */
var FrameReady.trampolineThresholdMs: Long
    get() = _trampolineThresholdMs
    set(value) { _trampolineThresholdMs = value }

private val _trampolineActivities = mutableSetOf<Class<out Activity>>()

/** Activities that should always be treated as trampolines and skipped immediately. */
val FrameReady.trampolineActivities: MutableSet<Class<out Activity>> get() = _trampolineActivities

private var _notificationOriginChecker: ((Intent) -> Boolean)? = null

/**
 * Optional predicate for detecting notification-originated launches, replacing the built-in
 * heuristic (checks for `"notification_id"` / `"from_notification"` extras). Set in
 * `Application.onCreate` to match your app's actual notification intent contract.
 */
var FrameReady.notificationOriginChecker: ((Intent) -> Boolean)?
    get() = _notificationOriginChecker
    set(value) { _notificationOriginChecker = value }

/**
 * Legacy entry point: registers initializers AND auto-wires Activity-lifecycle-based first-frame
 * detection for classic View-based apps (including [FrameReadyProvider] manifest auto-discovery).
 *
 * For Compose apps, prefer `FrameReady.install(context, List<KClass<...>>)` together with
 * `FrameReady.signalCompositionReady(context)`.
 */
@Suppress("UNCHECKED_CAST")
fun FrameReady.install(context: Context, initClasses: List<Class<Any>>) {
    FrameReady.platformResetHook = { resetAndroidBridgeForTesting() }

    val kClasses = initClasses.map { it.kotlin as KClass<out FrameReadyInitializer<*>> }
    FrameReady.install(context, kClasses)

    val appContext = context.applicationContext as Application
    if (lifecycleAttached.compareAndSet(false, true)) {
        registerLifecycleCallbacks(appContext)
    }
}

// ─── Testing support ─────────────────────────────────────────────────────────

@VisibleForTesting
internal fun FrameReady.registeredLifecycleCallbacksForTesting(): Application.ActivityLifecycleCallbacks? =
    registeredLifecycleCallbacks

private fun resetAndroidBridgeForTesting() {
    activityMap.clear()
    activeActivitiesCount.set(0)
    trampolineSkipCount.set(0)
    firstActivityStartedInProcess.set(false)
    lifecycleAttached.set(false)
    registeredLifecycleCallbacks = null
}

// ─── Activity lifecycle wiring ─────────────────────────────────────────────────

private fun registerLifecycleCallbacks(app: Application) {
    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activityMap[activity] = ActivityEntry(WeakReference(activity))
        }

        override fun onActivityStarted(activity: Activity) {
            val count = activeActivitiesCount.incrementAndGet()
            if (count == 1 && firstActivityStartedInProcess.compareAndSet(false, true)) {
                FrameReady.storage?.let { store ->
                    FrameReady.libraryScope.launch {
                        store.setTotalLaunchCount(store.getTotalLaunchCount() + 1)
                    }
                }
            }
        }

        override fun onActivityResumed(activity: Activity) {
            val entry = activityMap[activity] ?: return
            entry.resumedAt = SystemClock.elapsedRealtime()

            val isNotificationStart = Build.VERSION.SDK_INT >= 31 && isNotificationOriginated(activity)
            val isExplicitTrampoline = FrameReady.trampolineActivities.contains(activity::class.java)

            when {
                isNotificationStart -> triggerFirstDraw(activity)
                isExplicitTrampoline -> {
                    if (Log.isLoggable(FrameReady.TAG, Log.DEBUG)) {
                        Log.d(FrameReady.TAG, "Explicit trampoline: ${activity.localClassName}, skipping.")
                    }
                    trampolineSkipCount.incrementAndGet()
                }
                else -> {
                    val weakActivity = WeakReference(activity)
                    Handler(Looper.getMainLooper()).postDelayed({
                        val act = weakActivity.get() ?: return@postDelayed
                        val currentEntry = activityMap[act] ?: return@postDelayed
                        if (!act.isFinishing && !act.isDestroyed && currentEntry.stoppedAt == 0L) {
                            triggerFirstDraw(act)
                        } else {
                            if (Log.isLoggable(FrameReady.TAG, Log.DEBUG)) {
                                Log.d(FrameReady.TAG, "Trampoline detected: ${act.localClassName}, skipping.")
                            }
                            trampolineSkipCount.incrementAndGet()
                        }
                    }, FrameReady.trampolineThresholdMs)
                }
            }
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            activityMap[activity]?.stoppedAt = SystemClock.elapsedRealtime()
            val count = activeActivitiesCount.decrementAndGet()
            if (count == 0 && !FrameReady.hasTriggered.get()) {
                Log.i(FrameReady.TAG, "App went to background before first frame. Triggering initializers.")
                triggerBackgroundExecution(activity.applicationContext)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            activityMap.remove(activity)?.isDestroyed = true
        }
    }
    registeredLifecycleCallbacks = callbacks
    app.registerActivityLifecycleCallbacks(callbacks)
}

private fun isNotificationOriginated(activity: Activity): Boolean {
    val intent = activity.intent ?: return false
    return try {
        FrameReady.notificationOriginChecker?.invoke(intent)
            ?: (intent.hasExtra("notification_id") ||
               intent.hasExtra("from_notification") ||
               intent.action?.contains("NOTIFICATION") == true)
    } catch (e: Exception) {
        false
    }
}

private fun triggerFirstDraw(activity: Activity) {
    if (FrameReady.hasTriggered.get()) return
    val appContext = activity.applicationContext
    val activityName = activity.localClassName
    val decorView = activity.window?.decorView

    unregisterCallbacks(appContext)

    if (isExecutingInUnitTest) {
        Handler(Looper.getMainLooper()).post {
            FrameReady.markFirstFrame(appContext, SystemClock.elapsedRealtime(), activityName, trampolineSkipCount.get())
        }
    } else {
        scheduleRunAllOnNextFrame(appContext, activityName, decorView)
    }
}

private fun scheduleRunAllOnNextFrame(appContext: Context, activityName: String, decorView: View?) {
    Choreographer.getInstance().postFrameCallback { _ ->
        if (Build.VERSION.SDK_INT >= 29 && decorView != null) {
            val vto = decorView.viewTreeObserver
            if (vto.isAlive) {
                decorView.invalidate()
                vto.registerFrameCommitCallback {
                    FrameReady.markFirstFrame(appContext, SystemClock.elapsedRealtime(), activityName, trampolineSkipCount.get())
                }
                return@postFrameCallback
            }
        }
        FrameReady.markFirstFrame(appContext, SystemClock.elapsedRealtime(), activityName, trampolineSkipCount.get())
    }
}

private fun triggerBackgroundExecution(context: Context) {
    unregisterCallbacks(context)
    FrameReady.markFirstFrame(context, SystemClock.elapsedRealtime(), "Background", trampolineSkipCount.get())
}

private fun unregisterCallbacks(context: Context) {
    val app = context.applicationContext as? Application
    registeredLifecycleCallbacks?.let { app?.unregisterActivityLifecycleCallbacks(it) }
    registeredLifecycleCallbacks = null
    activityMap.clear()
}
