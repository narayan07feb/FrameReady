package com.frameready

/**
 * Platform-supplied context handed to [FrameReadyInitializer.create].
 *
 * - **Android**: `actual typealias PlatformContext = android.content.Context` — existing
 *   initializers compile unchanged.
 * - **iOS**: a lightweight marker singleton (`PlatformContext.Default`) since iOS has no
 *   equivalent of Android's `Context`.
 */
expect abstract class PlatformContext
