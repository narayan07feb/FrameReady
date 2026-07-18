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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameReadyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        FrameReady.resetAllForTesting()
        FrameReady.baselineTtffMs = 350L
    }

    // --- TEST INITIALIZERS ---

    class TestInitA : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
        override suspend fun create(context: Context): String = "Result_A"
    }

    class TestInitB : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = listOf(TestInitA::class)
        override suspend fun create(context: Context): String {
            val a = FrameReady.getOrNull(TestInitA::class) ?: "Null_A"
            return "Result_B_with_$a"
        }
    }

    class TestInitC : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = listOf(TestInitB::class)
        override suspend fun create(context: Context): String {
            val b = FrameReady.getOrNull(TestInitB::class) ?: "Null_B"
            return "Result_C_with_$b"
        }
    }

    // --- CIRCULAR TESTS ---

    class CircularA : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = listOf(CircularB::class)
        override suspend fun create(context: Context): String = "A"
    }

    class CircularB : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = listOf(CircularA::class)
        override suspend fun create(context: Context): String = "B"
    }

    // --- FAILING INITIALIZER ---

    class FailingInit : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
        override suspend fun create(context: Context): String {
            throw RuntimeException("Simulated Failure")
        }
    }

    // --- SLOW INITIALIZER ---

    class SlowInit : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
        override suspend fun create(context: Context): String {
            kotlinx.coroutines.delay(1000)
            return "Done"
        }
    }

    class BlockingInterruptibleInit : FrameReadyInitializer<String> {
        companion object {
            val wasInterrupted = java.util.concurrent.atomic.AtomicBoolean(false)
        }

        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
        override fun executionThread() = ExecutionThread.BACKGROUND
        override fun timeoutMs() = 400L

        override suspend fun create(context: Context): String {
            try {
                Thread.sleep(5000)
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
        val input = listOf(TestInitC::class as KClass<Any>, TestInitB::class as KClass<Any>, TestInitA::class as KClass<Any>)
        val sortedOutput = FrameReady.sort(input)

        val idxA = sortedOutput.indexOf(TestInitA::class as KClass<Any>)
        val idxB = sortedOutput.indexOf(TestInitB::class as KClass<Any>)
        val idxC = sortedOutput.indexOf(TestInitC::class as KClass<Any>)

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
            FrameReady.install(context, listOf(CircularA::class.java as Class<Any>))
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
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))

        val deferredResult = async { FrameReady.await(TestInitA::class) }

        assertNull(FrameReady.getOrNull(TestInitA::class))

        FrameReady.getDeferred<String>(TestInitA::class as KClass<Any>).complete("Manual_Result_A")

        val resumedResult = deferredResult.await()
        assertEquals("Manual_Result_A", resumedResult)
    }

    @Test
    fun testAwait_AfterCompletion_ReturnsImmediately() = runTest {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        FrameReady.getDeferred<String>(TestInitA::class as KClass<Any>).complete("Speedy_A")
        val immediateResult = FrameReady.await(TestInitA::class)
        assertEquals("Speedy_A", immediateResult)
    }

    // ==========================================
    // 4. TIMEOUT SUPPORT
    // ==========================================
    @Test
    fun testAwaitTimeout_ThrowsException() = runTest {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        try {
            FrameReady.await(TestInitA::class, timeoutMs = 50L)
            fail("Expected InitializerTimeoutException")
        } catch (e: InitializerTimeoutException) {
            assertTrue(e.message!!.contains("timed out"))
        }
    }

    // ==========================================
    // 5. EXCEPTION PROPAGATION
    // ==========================================
    @Test
    fun testExceptionPropagation_BubblesUp() = runTest {
        FrameReady.install(context, listOf(FailingInit::class.java as Class<Any>))
        FrameReady.getDeferred<String>(FailingInit::class as KClass<Any>).completeExceptionally(
            RuntimeException("Underlying Failure")
        )
        try {
            FrameReady.await(FailingInit::class)
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
        FrameReady.install(app, listOf(TestInitA::class.java as Class<Any>))

        val mockActivity = Robolectric.buildActivity(Activity::class.java).get()
        val destActivity = Robolectric.buildActivity(Activity::class.java).get()

        val callbacks = getRegisteredCallbacks(app)
        assertNotNull("Callbacks must be registered after install", callbacks)

        callbacks.onActivityCreated(mockActivity, null)
        callbacks.onActivityStarted(mockActivity)
        callbacks.onActivityResumed(mockActivity)

        callbacks.onActivityCreated(destActivity, null)
        callbacks.onActivityStarted(destActivity)
        callbacks.onActivityResumed(destActivity)

        shadowOf(Looper.getMainLooper()).idleFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        callbacks.onActivityStopped(mockActivity)

        shadowOf(Looper.getMainLooper()).idleFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertNull(FrameReady.getOrNull(TestInitA::class))
    }

    // ==========================================
    // 7. PRIMARY SCREEN SUCCESS TRIGGER
    // ==========================================
    @Test
    fun testPrimaryActivity_SurvivesThreshold_TriggersStartup() {
        val app = context.applicationContext as Application
        FrameReady.install(app, listOf(TestInitA::class.java as Class<Any>))

        val mockActivity = Robolectric.buildActivity(Activity::class.java).get()
        val callbacks = getRegisteredCallbacks(app)
        assertNotNull(callbacks)

        callbacks.onActivityCreated(mockActivity, null)
        callbacks.onActivityStarted(mockActivity)
        callbacks.onActivityResumed(mockActivity)

        shadowOf(Looper.getMainLooper()).idleFor(600, java.util.concurrent.TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        var result: String? = null
        var limit = 0
        while (limit < 100) {
            result = FrameReady.getOrNull(TestInitA::class)
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
    fun testStabilityCounter_ResetsOnFailure() = kotlinx.coroutines.runBlocking {
        var stableCount = 15
        val testStorage = object : FrameReadyStorage {
            override fun getStableLaunchCount() = stableCount
            override fun setStableLaunchCount(count: Int) { stableCount = count }
            override fun getTotalLaunchCount() = 0
            override fun setTotalLaunchCount(count: Int) {}
            override fun getColdLaunchCount() = 0
            override fun setColdLaunchCount(count: Int) {}
            override fun getTtffHistory() = emptyList<Long>()
            override fun setTtffHistory(history: List<Long>) {}
        }
        FrameReady.storage = testStorage
        @Suppress("UNCHECKED_CAST")
        FrameReady.install(context, emptyList<Class<Any>>())

        val exception = RuntimeException("Boom")
        // Internal functions get KMP module-name-mangled on JVM; find by prefix.
        val method = FrameReady::class.java.declaredMethods
            .first { it.name.startsWith("handleStartupFailure") }
            .apply { isAccessible = true }
        method.invoke(FrameReady, exception, TestInitA::class)

        // handleStartupFailure() resets stability via a background coroutine (libraryScope,
        // Dispatchers.Default) — poll briefly instead of asserting immediately.
        kotlinx.coroutines.withTimeout(2000L) {
            while (testStorage.getStableLaunchCount() != 0) {
                kotlinx.coroutines.delay(20L)
            }
        }
        assertEquals("Failure should reset stability count to 0", 0, testStorage.getStableLaunchCount())
    }

    // ==========================================
    // 9. DEADLOCK PREVENTION IN GET()
    // ==========================================
    @Test
    fun testGet_OnMainThreadBeforeCompletion_ThrowsException() {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        try {
            FrameReady.get(TestInitA::class)
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
        FrameReady.install(app, listOf(TestInitA::class.java as Class<Any>))
        FrameReady.trampolineActivities.add(CustomTrampolineActivity::class.java)

        val activity = Robolectric.buildActivity(CustomTrampolineActivity::class.java).get()
        val callbacks = getRegisteredCallbacks(app)

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        shadowOf(Looper.getMainLooper()).idleFor(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertNull(FrameReady.getOrNull(TestInitA::class))
    }

    // ==========================================
    // 11. CUMULATIVE INITIALIZER REGISTRATION
    // ==========================================
    @Test
    fun testCumulativeInstallation_SavesNodes() {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        FrameReady.install(context, listOf(TestInitB::class.java as Class<Any>))

        val sorted = FrameReady::class.java.getDeclaredField("sortedInitializers").apply {
            isAccessible = true
        }.get(FrameReady) as List<Any?>

        assertTrue(sorted.contains(TestInitA::class))
        assertTrue(sorted.contains(TestInitB::class))
    }

    // ==========================================
    // 12. RUNINTERRUPTIBLE & TIMEOUT SAFETY
    // ==========================================
    @Test
    fun testBlockingInitializer_WhenTimeoutExceeded_SafelyInterruptsThreadSleep() = kotlinx.coroutines.runBlocking {
        BlockingInterruptibleInit.wasInterrupted.set(false)
        FrameReady.install(context, listOf(BlockingInterruptibleInit::class.java as Class<Any>))

        val app = context.applicationContext as Application
        val callbacks = getRegisteredCallbacks(app)
        val activity = Robolectric.buildActivity(Activity::class.java).get()

        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)

        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        try {
            FrameReady.await(BlockingInterruptibleInit::class)
            fail("Expected InitializerTimeoutException")
        } catch (e: Exception) {
            val unwrapped = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
            assertTrue(
                "Expected InitializerTimeoutException but got: ${unwrapped::class.java.simpleName}",
                unwrapped is InitializerTimeoutException
            )
        }

        assertTrue(
            "Expected Thread.sleep to be interrupted, but it wasn't!",
            BlockingInterruptibleInit.wasInterrupted.get()
        )
    }

    // ==========================================
    // 13. DISABLE — per-initializer opt-out
    // ==========================================

    @Test
    fun testDisabledInitializer_ThrowsDisabledInitializerException() = kotlinx.coroutines.runBlocking {
        FrameReady.disable(TestInitA::class)
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))

        val app = context.applicationContext as Application
        val callbacks = getRegisteredCallbacks(app)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        try {
            FrameReady.await(TestInitA::class)
            fail("Expected DisabledInitializerException")
        } catch (e: Exception) {
            assertTrue(
                "Expected DisabledInitializerException but got ${e::class.java.simpleName}",
                e is DisabledInitializerException
            )
        }
    }

    @Test
    fun testDisabledInitializer_DependentCascadeFails() = kotlinx.coroutines.runBlocking {
        FrameReady.disable(TestInitA::class)
        FrameReady.install(context, listOf(
            TestInitA::class.java as Class<Any>,
            TestInitB::class.java as Class<Any>
        ))

        val app = context.applicationContext as Application
        val callbacks = getRegisteredCallbacks(app)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        try {
            FrameReady.await(TestInitB::class)
            fail("Expected cascade failure")
        } catch (e: Exception) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun testDisabledInitializer_IsDisabled_ReturnsTrue() {
        FrameReady.disable(TestInitA::class)
        assertTrue(FrameReady.isDisabled(TestInitA::class))
    }

    // ==========================================
    // 14. HEADLESS PROCESS — no-Activity fallback
    // ==========================================

    @Test
    fun testHeadlessProcess_RunsInitializersAfterTimeout() = kotlinx.coroutines.runBlocking {
        FrameReady.headlessTimeoutMs = 200L
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))

        shadowOf(Looper.getMainLooper()).idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)

        val result = FrameReady.await(TestInitA::class)
        assertEquals("Result_A", result)
    }

    // ==========================================
    // 15. registerFactory() — DI bridge
    // ==========================================

    class FactoryTestInit : FrameReadyInitializer<String> {
        override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
        override suspend fun create(context: Context): String = "ReflectionResult"
    }

    @Test
    fun testRegisterFactory_UsesFactoryInstanceForCreate() = kotlinx.coroutines.runBlocking {
        val factoryInstance = object : FrameReadyInitializer<String> {
            override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
            override suspend fun create(context: Context): String = "FromFactory"
        }
        FrameReady.registerFactory(FactoryTestInit::class) { factoryInstance }
        FrameReady.install(context, listOf(FactoryTestInit::class.java as Class<Any>))

        val callbacks = getRegisteredCallbacks(context as Application)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        val result = FrameReady.await(FactoryTestInit::class)
        assertEquals("FromFactory", result)
    }

    @Test
    fun testRegisterFactory_EvictsReflectionInstanceOnRegistration() {
        FrameReady.install(context, listOf(FactoryTestInit::class.java as Class<Any>))

        // Trigger reflection-based instantiation once, before registerFactory() is called.
        @Suppress("UNCHECKED_CAST")
        val reflectionInstance = FrameReady.getInstance(FactoryTestInit::class as KClass<Any>)

        val factoryInstance = object : FrameReadyInitializer<String> {
            override fun dependencies(): List<KClass<out FrameReadyInitializer<*>>> = emptyList()
            override suspend fun create(context: Context): String = "FromFactory"
        }
        FrameReady.registerFactory(FactoryTestInit::class) { factoryInstance }

        @Suppress("UNCHECKED_CAST")
        val afterRegister = FrameReady.getInstance(FactoryTestInit::class as KClass<Any>)
        assertNotSame("registerFactory() must evict the cached reflection instance", reflectionInstance, afterRegister)
        assertSame(factoryInstance, afterRegister)
    }

    // ==========================================
    // 16. asDeferred() — DI consumption bridge
    // ==========================================

    @Test
    fun testAsDeferred_ReturnsSameInstanceAsAwait() = kotlinx.coroutines.runBlocking {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        val deferred = FrameReady.asDeferred(TestInitA::class)

        val callbacks = getRegisteredCallbacks(context as Application)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Result_A", deferred.await())
    }

    @Test
    fun testAsDeferred_IsStableSingleton() {
        val d1 = FrameReady.asDeferred(TestInitA::class)
        val d2 = FrameReady.asDeferred(TestInitA::class)
        assertSame(d1, d2)
    }

    @Test
    fun testAsDeferred_CanBeCalledBeforeInstall() = kotlinx.coroutines.runBlocking {
        val deferred = FrameReady.asDeferred(TestInitA::class)
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))

        val callbacks = getRegisteredCallbacks(context as Application)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Result_A", deferred.await())
    }

    // ─── await() fail-fast ────────────────────────────────────────────────────────

    @Test
    fun testAwait_ThrowsIllegalStateException_WhenClassNotInstalled() = runTest {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        try {
            FrameReady.await(TestInitB::class)
            fail("Expected IllegalStateException for unregistered class")
        } catch (e: IllegalStateException) {
            assertTrue("Message should name the class", e.message?.contains("TestInitB") == true)
        }
    }

    // ─── asStateFlow ──────────────────────────────────────────────────────────────

    @Test
    fun testAsStateFlow_EmitsNullThenResult() = kotlinx.coroutines.runBlocking {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        val flow = FrameReady.asStateFlow(TestInitA::class)

        assertNull(flow.value)

        val callbacks = getRegisteredCallbacks(context as Application)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        val result = kotlinx.coroutines.withTimeout(2000L) {
            flow.filterNotNull().first()
        }
        assertEquals("Result_A", result)
    }

    @Test
    fun testAsStateFlow_IsStableSingleton() {
        val f1 = FrameReady.asStateFlow(TestInitA::class)
        val f2 = FrameReady.asStateFlow(TestInitA::class)
        assertSame(f1, f2)
    }

    // ─── retry ────────────────────────────────────────────────────────────────────

    @Test
    fun testRetry_ThrowsWhenClassNotInstalled() {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        try {
            FrameReady.retry(TestInitB::class)
            fail("Expected IllegalStateException for unregistered class")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("TestInitB") == true)
        }
    }

    @Test
    fun testRetry_ResetsCompletedDeferred() = kotlinx.coroutines.runBlocking {
        FrameReady.install(context, listOf(TestInitA::class.java as Class<Any>))
        val callbacks = getRegisteredCallbacks(context as Application)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        callbacks.onActivityCreated(activity, null)
        callbacks.onActivityStarted(activity)
        callbacks.onActivityResumed(activity)
        shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        val first = FrameReady.await(TestInitA::class, timeoutMs = 2000L)
        assertEquals("Result_A", first)

        FrameReady.retry(TestInitA::class)
        assertNull(FrameReady.getOrNull(TestInitA::class))
    }

    private fun getRegisteredCallbacks(app: Application): Application.ActivityLifecycleCallbacks {
        return FrameReady.registeredLifecycleCallbacksForTesting()!!
    }
}
