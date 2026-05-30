package com.example.demo

import android.content.Context
import android.util.Log
import com.frameready.ExecutionThread
import com.frameready.FrameReadyInitializer
import com.frameready.FrameReady
import kotlinx.coroutines.delay

class AInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()

    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: Context): String {
        Log.i("SampleInitializer", "AInitializer starting on ${Thread.currentThread().name}")
        // Simulate background work elegantly with non-blocking delay
        delay(800)
        Log.i("SampleInitializer", "AInitializer completed!")
        return "Core Services Active"
    }
}

class BInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = listOf(AInitializer::class.java)

    override fun executionThread() = ExecutionThread.BACKGROUND

    override suspend fun create(context: Context): String {
        Log.i("SampleInitializer", "BInitializer starting on ${Thread.currentThread().name}")
        // Under Rule 1 of FrameReady, A is guaranteed to be done before B starts.
        val aResult = FrameReady.getOrNull(AInitializer::class.java)
            ?: throw IllegalStateException("A must have completed first!")
        
        // Simulate background DB work elegantly with non-blocking delay
        delay(600)
        Log.i("SampleInitializer", "BInitializer completed (A was: $aResult)!")
        return "Local SQL DB Connected [a: $aResult]"
    }
}

class CInitializer : FrameReadyInitializer<String> {
    override fun dependencies() = listOf(BInitializer::class.java)

    override fun executionThread() = ExecutionThread.MAIN

    override suspend fun create(context: Context): String {
        Log.i("SampleInitializer", "CInitializer starting on ${Thread.currentThread().name}")
        // Under Rule 1 of FrameReady, B is guaranteed to be done before C starts.
        val bResult = FrameReady.getOrNull(BInitializer::class.java)
            ?: throw IllegalStateException("B must have completed first!")
        
        // C runs on MAIN thread, so do not block here
        Log.i("SampleInitializer", "CInitializer completed! (B was: $bResult)!")
        return "Cloud Sync Engine Configured [b: $bResult]"
    }
}
