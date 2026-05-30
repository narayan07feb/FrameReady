package com.frameready

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FrameReadyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        FrameReady.resetAllForTesting()
        FrameReady.stableThreshold = 100
        FrameReady.baselineTtffMs = 350L
    }

    // --- TEST INITIALIZERS ---

    class TestInitA : FrameReadyInitializer<String> {
        override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()
        override suspend fun create(context: Context): String = "Result_A"
    }

    class TestInitB : FrameReadyInitializer<String> {
        override fun dependencies() = listOf(TestInitA::class.java)
        override suspend fun create(context: Context): String {
            val a = FrameReady.getOrNull(TestInitA::class.java) ?: "Null_A"
            return "Result_B_with_$a"
        }
    }

    class TestInitC : FrameReadyInitializer<String> {
        override fun dependencies() = listOf(TestInitB::class.java)
        override suspend fun create(context: Context): String {
            val b = FrameReady.getOrNull(TestInitB::class.java) ?: "Null_B"
            return "Result_C_with_$b"
        }
    }

    // --- CIRCULAR TESTS ---

    class CircularA : FrameReadyInitializer<String> {
        override fun dependencies() = listOf(CircularB::class.java)
        override suspend fun create(context: Context): String = "A"
    }

    class CircularB : FrameReadyInitializer<String> {
        override fun dependencies() = listOf(CircularA::class.java)
        override suspend fun create(context: Context): String = "B"
    }

    // --- FAILING INITIALIZER ---

    class FailingInit : FrameReadyInitializer<String> {
        override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()
        override suspend fun create(context: Context): String {
            throw RuntimeException("Simulated Failure")
        }
    }

    // --- SLOW INITIALIZER ---

    class SlowInit : FrameReadyInitializer<String> {
        override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()
        override suspend fun create(context: Context): String {
            kotlinx.coroutines.delay(1000)
            return "Done"
        }
    }

    class BlockingInterruptibleInit : FrameReadyInitializer<String> {
        companion object {
            val wasInterrupted = java.util.concurrent.atomic.AtomicBoolean(false)
        }

        override fun dependencies() = emptyList<Class<out FrameReadyInitializer<*>>>()
        override fun executionThread() = ExecutionThread.BACKGROUND
        override fun timeoutMs() = 400L // 400ms timeout

        override suspend fun create(context: Context): String {
            try {
                Thread.sleep(5000) // Sleep 5s (blocking)
            } catch (e: InterruptedException) {
                wasInterrupted.set(true)
                throw e
            }
            return "Success"
        }
    }

    // ==========================================
    // 1. DEPENDENCY ORDERING (KAHN SORT)
    // ==========================================
    @Test
    fun testDependencyOrdering_TopologicalSort() {
        // We supply nodes in reverse or scrambled order.
        val input = listOf(TestInitC::class.java, TestInitB::class.java, TestInitA::class.java)
        val sortedOutput = FrameReady.sort(input)

        // Index checks: A must be before B, B must be before C
        val idxA = sortedOutput.indexOf(TestInitA::class.java)
        val idxB = sortedOutput.indexOf(TestInitB::class.java)
        val idxC = sortedOutput.indexOf(TestInitC::class.java)

        assertTrue("A must run before B", idxA < idxB)
        assertTrue("B must run before C", idxB < idxC)
        assertEquals(3, sortedOutput.size)
    }

    // ==========================================
    // 2. CIRCULAR DEPENDENCY DETECTION
    // ==========================================
    @Test
    fun testCircularDependency_FailsFast() {
        try {
            FrameReady.install(context, listOf(CircularA::class.java))
            fail("Expected CircularDependencyException to be thrown!")
        } catch (e: CircularDependencyException) {
            assertTrue(e.message!!.contains("circular dependency"))
        }
    }

    // ==========================================
    // 3. SUSPEND / AWAIT CONTRACT (PRE & POST FRAME)
    // ==========================================
    @Test
    fun testAwait_BeforeCompletion_SuspendsAndResumes() = runTest {
        // Install initializers manual registration
        FrameReady.install(context, listOf(TestInitA::class.java))

        val deferredResult = async {
            FrameReady.await(TestInitA::class.java)
        }

        // Initially no result is ready
        assertNull(FrameReady.getOrNull(TestInitA::class.java))

        // Trigger manual run simulating frame trigger completing
        @Suppress("UNCHECKED_CAST")
        val internalMap = FrameReady::class.java.getDeclaredField("resultMap").apply {
            isAccessible = true
        }.get(null) as java.util.concurrent.ConcurrentHashMap<Class<*>, CompletableDeferred<Any>>

        // Force complete Result
        internalMap[TestInitA::class.java]?.complete("Manual_Result_A")

        // Await should resume and yield correct result
        val resumedResult = deferredResult.await()
        assertEquals("Manual_Result_A", resumedResult)
    }

    @Test
    fun testAwait_AfterCompletion_ReturnsImmediately() = runTest {
        FrameReady.install(context, listOf(TestInitA::class.java))

        // Pre-complete it
        FrameReady.getDeferred<String>(TestInitA::class.java).complete("Speedy_A")

        val immediateResult = FrameReady.await(TestInitA::class.java)
        assertEquals("Speedy_A", immediateResult)
    }

    // ==========================================
    // 4. TIMEOUT SUPPORT - THROWS TIMEOUT EXCEPTION
    // ==========================================
    @Test
    fun testAwaitTimeout_ThrowsException() = runTest {
        FrameReady.install(context, listOf(TestInitA::class.java))

        try {
            // Wait for only 50ms (will timeout as we never complete it)
            FrameReady.await(TestInitA::class.java, timeoutMs = 50L)
            fail("Expected InitializerTimeoutException")
        } catch (e: InitializerTimeoutException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    // ==========================================
    // 5. EXCEPTION PROPAGATION FROM INITIALIZER TO AWAIT
    // ==========================================
    @Test
    fun testExceptionPropagation_BubblesUp() = runTest {
        FrameReady.install(context, listOf(FailingInit::class.java))

        // Force complete exceptionally to simulate runner failure
        FrameReady.getDeferred<String>(FailingInit::class.java).completeExceptionally(
            RuntimeException("Underlying Failure")
        )

        try {
            FrameReady.await(FailingInit::class.java)
            fail("Expected exception to bubble up!")
        } catch (e: Throwable) {
            assertEquals("Underlying Failure", e.message)
        }
    }

    // ==========================================
    // 6. TRAMPOLINE ACTIVITY SCREEN SKIPPED
    // ==========================================
    @Test
    fun testTrampolineActivity_IsSkipped() {
        val app = context.applicationContext as Application
        FrameReady.install(app, listOf(TestInitA::class.java))

        // Senders mock trampoline activity
        val mockActivity = Robolectric.buildActivity(Activity::class.java).get()
        val destActivity = Robolectric.buildActivity(Activity::class.java).get()
        
        // Manual lifecycle progression
        val callbacks = getRegisteredCallbacks(app)
        assertNotNull("Callbacks must be registered after install", callbacks)

        // Simulating immediate trampoline create -> resume
        callbacks.onActivityCreated(mockActivity, null)
        callbacks.onActivityStarted(mockActivity)
        callbacks.onActivityResumed(mockActivity)

        // Simulating high-fidelity transition: Destination activity starts
        callbacks.onActivityCreated(destActivity, null)
        callbacks.onActivityStarted(destActivity)
        callbacks.onActivityResumed(destActivity)

        // Trampoline scenario: Trampoline Activity stopped within < 500ms
        shadowOf(Looper.getMainLooper()).idleFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        callbacks.onActivityStopped(mockActivity)

        // Run full looper past remaining threshold
        shadowOf(Looper.getMainLooper()).idleFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Check that initializers have NOT run because trampoline skip was bypassed
        assertNull(FrameReady.getOrNull(TestInitA::class.java))
    }

    // ==========================================
    // 7. SPLASHSCREEN / PRIMARY SCREEN SUCCESS TRIGGER
    // ==========================================
    @Test
    fun testPrimaryActivity_SurvivesThreshold_TriggersStartup() {
        val app = context.applicationContext as Application
        FrameReady.install(app, listOf(TestInitA::class.java))

        val mockActivity = Robolectric.buildActivity(Activity::class.java).get()
        val callbacks = getRegisteredCallbacks(app)
        assertNotNull(callbacks)

        callbacks.onActivityCreated(mockActivity, null)
        callbacks.onActivityStarted(mockActivity)
        callbacks.onActivityResumed(mockActivity)

        // Survives past the 500ms threshold without any stop callback
        shadowOf(Looper.getMainLooper()).idleFor(600, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Choreographer frame triggers background runner
        shadowOf(Looper.getMainLooper()).idle()

        // Wait up to 5 seconds for JVM background threads to complete execution
        var result: String? = null
        var limit = 0
        while (limit < 100) {
            result = FrameReady.getOrNull(TestInitA::class.java)
            if (result != null) break
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            limit++
        }

        assertNotNull("Primary Activity must complete initialization successfully", result)
    }

    // ==========================================
    // 8. STABILITY COUNTER RESET ON EXCEPTION
    // ==========================================
    @Test
    fun testStabilityCounter_ResetsOnFailure() {
        FrameReady.install(context, emptyList())
        val sharedPrefs = context.getSharedPreferences("frame_ready_preferences", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("consecutive_stable_launches", 15).apply()

        // Call failure tracker manually
        val exception = RuntimeException("Boom")
        val method = FrameReady::class.java.getDeclaredMethod("handleStartupFailure", Throwable::class.java, String::class.java).apply {
            isAccessible = true
        }
        
        method.invoke(FrameReady, exception, "FailingInit")
        
        // Wait for handler UI post to clean up shared preference
        shadowOf(Looper.getMainLooper()).idle()

        // Must reset back to 0
        val count = sharedPrefs.getInt("consecutive_stable_launches", -1)
        assertEquals(0, count)
    }

    // ==========================================
    // 9. DEADLOCK PREVENTION IN GET()
    // ==========================================
    @Test
    fun testGet_OnMainThreadBeforeCompletion_ThrowsException() {
        FrameReady.install(context, listOf(TestInitA::class.java))
        
        try {
            FrameReady.get(TestInitA::class.java)
            fail("Expected IllegalStateException due to Main Thread deadlock checking!")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Rule 4 Violation & Deadlock Risk"))
        }
    }

    // ==========================================
    // 10. EXPLICIT TRAMPOLINE REGISTRATION
    // ==========================================
    @Test
    fun testExplicitTrampoline_SkipsImmediate() {
        class CustomTrampolineActivity : Activity()
        
        val app = context.applicationContext as Application
        FrameReady.install(app, listOf(TestInitA::class.java))
        FrameReady.trampolineActivities.add(CustomTrampolineActivity::class.java)

        val activity = Robolectric.buildActivity(CustomTrampolineActivity::class.java).get()
        val callbacks = getRegisteredCallbacks(app)
        
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        // Run full looper past any thresholds
        shadowOf(Looper.getMainLooper()).idleFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Should skip triggering due to explicit trampoline registration
        assertNull(FrameReady.getOrNull(TestInitA::class.java))
    }

    // ==========================================
    // 11. CUMULATIVE INITIALIZER REGISTRATION
    // ==========================================
    @Test
    fun testCumulativeInstallation_SavesNodes() {
        FrameReady.install(context, listOf(TestInitA::class.java))
        // Incrementally install B
        FrameReady.install(context, listOf(TestInitB::class.java))

        val sorted = FrameReady::class.java.getDeclaredField("sortedInitializers").apply {
            isAccessible = true
        }.get(null) as List<*>

        assertTrue(sorted.contains(TestInitA::class.java))
        assertTrue(sorted.contains(TestInitB::class.java))
    }

    // ==========================================
    // 12. RUNINTERRUPTIBLE & TIMEOUT SAFETY FOR SLEEPING THREADS
    // ==========================================
    @Test
    fun testBlockingInitializer_WhenTimeoutExceeded_SafelyInterruptsThreadSleep() = kotlinx.coroutines.runBlocking {
        BlockingInterruptibleInit.wasInterrupted.set(false)
        FrameReady.install(context, listOf(BlockingInterruptibleInit::class.java))

        val app = context.applicationContext as Application
        val callbacks = getRegisteredCallbacks(app)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        // Run scheduler loop to let Choreographer fire & dispatch libraries
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        try {
            FrameReady.await(BlockingInterruptibleInit::class.java)
            fail("Expected InitializerTimeoutException")
        } catch (e: Exception) {
            val unwrapped = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
            assertTrue(
                "Expected InitializerTimeoutException but got: ${unwrapped::class.java.simpleName}",
                unwrapped is InitializerTimeoutException
            )
        }

        assertTrue(
            "Expected the blocked Thread.sleep inside the initializer to be interrupted, but it wasn't!",
            BlockingInterruptibleInit.wasInterrupted.get()
        )
    }

    private fun getRegisteredCallbacks(app: Application): Application.ActivityLifecycleCallbacks {
        val field = FrameReady::class.java.getDeclaredField("lifecycleCallbacks").apply {
            isAccessible = true
        }
        return field.get(null) as Application.ActivityLifecycleCallbacks
    }
}
