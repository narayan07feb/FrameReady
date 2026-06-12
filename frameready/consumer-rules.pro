# FrameReady Consumer Proguard Rules

# Keep the FrameReadyProvider so it is not stripped by R8 during application compilation
-keep class com.frameready.FrameReadyProvider { *; }

# Keep all implementations of FrameReadyInitializer and their zero-argument constructors
# This is critical because the library discovers and instantiates them via reflection using manifest metadata
-keep class * implements com.frameready.FrameReadyInitializer {
    <init>();
}

# Keep the public API of the FrameReady class itself
-keep class com.frameready.FrameReady {
    public static *** getOrNull(...);
    public static *** await(...);
    public static void setMetricsListener(...);
    public static long getBaselineTtffMs();
    public static void setBaselineTtffMs(long);
    public static int getStableThreshold();
    public static void setStableThreshold(int);
}

# Keep the StartupMetrics data class for telemetry usage
-keep class com.frameready.StartupMetrics { *; }
