# ────────────────────────────────────────────────────────────────────────────────
# FrameReady consumer ProGuard / R8 rules
#
# These rules are automatically included in any app that depends on FrameReady.
# Do NOT add app-specific rules here; put those in your app's proguard-rules.pro.
# ────────────────────────────────────────────────────────────────────────────────

# ContentProvider — must survive so the OS can instantiate it at app startup
-keep class com.frameready.FrameReadyProvider { *; }

# All FrameReadyInitializer implementations require a zero-arg constructor because
# FrameReady discovers them via AndroidManifest metadata and instantiates via reflection.
# Keep the no-arg <init> but allow R8 to strip unused members otherwise.
-keepclassmembers class * implements com.frameready.FrameReadyInitializer {
    <init>();
}

# The FrameReadyInitializer interface itself (needed for instanceof checks and cast)
-keep interface com.frameready.FrameReadyInitializer { *; }

# FrameReady singleton — keep every public and @JvmStatic member that the app
# can call by name (reflection from Kotlin object companion descriptors).
-keep class com.frameready.FrameReady {
    # Properties
    public kotlinx.coroutines.flow.SharedFlow getMetricsFlow();
    public long getContentProviderStartTime();
    public long getBaselineTtffMs();
    public void setBaselineTtffMs(long);
    public com.frameready.FrameReadyStorage getStorage();
    public void setStorage(com.frameready.FrameReadyStorage);
    public long getTrampolineThresholdMs();
    public void setTrampolineThresholdMs(long);
    public java.util.List getTrampolineActivities();
    public void setTrampolineActivities(java.util.List);
    public long getHeadlessTimeoutMs();
    public void setHeadlessTimeoutMs(long);
    public kotlin.jvm.functions.Function1 getNotificationOriginChecker();
    public void setNotificationOriginChecker(kotlin.jvm.functions.Function1);

    # Core API
    public void install(android.content.Context, java.util.List);
    public void install(android.content.Context);
    public void disable(java.lang.Class);
    public boolean isDisabled(java.lang.Class);
    public *** getOrNull(java.lang.Class);
    public *** get(java.lang.Class);
    public *** await(java.lang.Class, long, kotlin.coroutines.Continuation);

    # DI integration
    public kotlinx.coroutines.Deferred asDeferred(java.lang.Class);
    public void registerFactory(java.lang.Class, kotlin.jvm.functions.Function0);

    # Compose / StateFlow
    public kotlinx.coroutines.flow.StateFlow asStateFlow(java.lang.Class);

    # Retry
    public void retry(java.lang.Class);

    # Metrics lifecycle
    public void resetStability();

    # Testing
    public void resetAllForTesting();
}

# Kotlin extension functions in FrameReadyExt (reified inline functions are inlined
# at call sites, but the backing non-reified overloads on FrameReady are kept above)
-keep class com.frameready.FrameReadyExtKt { *; }

# StartupMetrics data class — keep for telemetry and metricsFlow subscribers
-keep class com.frameready.StartupMetrics { *; }

# FrameReadyStorage interface — apps implement this; R8 must not remove the methods
-keep interface com.frameready.FrameReadyStorage { *; }

# Built-in SharedPreferences-backed storage (convenience class in the library)
-keep class com.frameready.SharedPreferencesFrameReadyStorage { *; }

# Exception types — preserve class names for clear error messages in logs / crash reporters
-keep class com.frameready.CircularDependencyException { *; }
-keep class com.frameready.InitializerTimeoutException { *; }
-keep class com.frameready.DisabledInitializerException { *; }
