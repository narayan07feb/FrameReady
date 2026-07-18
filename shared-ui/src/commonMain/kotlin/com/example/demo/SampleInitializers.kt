package com.example.demo

import com.frameready.ExecutionThread
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import com.frameready.PlatformContext
import kotlinx.coroutines.delay

class AInitializer : FrameReadyInitializer<String> {
    override fun dependencies(): List<kotlin.reflect.KClass<out FrameReadyInitializer<*>>> = emptyList()

    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: PlatformContext): String {
        // Simulate background work elegantly with non-blocking delay
        delay(800)
        return "Core Services Active"
    }
}

class BInitializer : FrameReadyInitializer<String> {
    override fun dependencies(): List<kotlin.reflect.KClass<out FrameReadyInitializer<*>>> = listOf(AInitializer::class)

    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: PlatformContext): String {
        // Under Rule 1 of FrameReady, A is guaranteed to be done before B starts.
        val aResult = FrameReady.getOrNull(AInitializer::class)
            ?: throw IllegalStateException("A must have completed first!")

        // Simulate background DB work elegantly with non-blocking delay
        delay(600)
        return "Local SQL DB Connected [a: $aResult]"
    }
}

class CInitializer : FrameReadyInitializer<String> {
    override fun dependencies(): List<kotlin.reflect.KClass<out FrameReadyInitializer<*>>> = listOf(BInitializer::class)

    override fun executionThread() = ExecutionThread.MAIN

    override suspend fun create(context: PlatformContext): String {
        // Under Rule 1 of FrameReady, B is guaranteed to be done before C starts.
        val bResult = FrameReady.getOrNull(BInitializer::class)
            ?: throw IllegalStateException("B must have completed first!")

        // C runs on MAIN thread, so do not block here
        return "Cloud Sync Engine Configured [b: $bResult]"
    }
}
