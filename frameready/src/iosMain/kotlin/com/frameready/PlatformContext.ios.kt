package com.frameready

/**
 * iOS has no system context type equivalent to Android's Context.
 * PlatformContext is a lightweight marker singleton — initializers receive it but rarely need it.
 * Use `PlatformContext.Default` when calling [FrameReady.install] or [FrameReady.signalCompositionReady].
 */
actual abstract class PlatformContext {
    companion object {
        val Default: PlatformContext = object : PlatformContext() {}
    }
}
