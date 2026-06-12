package com.example.demo

import android.content.Context
import androidx.startup.Initializer
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import com.frameready.ExecutionThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// =========================================================================
// ARCHITECTURAL SAMPLE 1: KOIN INTEGRATION WITH FRAMEREADY
// =========================================================================
/**
 * Under Koin, we can either start Koin directly inside Application.onCreate() (with empty or placeholder modules),
 * or start Koin dynamically post-first-frame inside a FrameReadyInitializer!
 * 
 * If Koin is started post-first-frame, we must make sure all dependent ViewModels or classes
 * await the completion of the Koin initializer before retrieving injections.
 */

// A sample service that takes time to prepare
interface AnalyticsService {
    fun trackEvent(name: String)
}

class AnalyticsServiceImpl : AnalyticsService {
    override fun trackEvent(name: String) {
        android.util.Log.i("KoinIntegration", "Tracking event: $name")
    }
}

/**
 * Koin modules definition representing your dependency graph.
 */
object SampleKoinModules {
    // Simulated Koin module definition
    // val appModule = module {
    //     single<AnalyticsService> { AnalyticsServiceImpl() }
    // }
}

/**
 * A dedicated FrameReady Initializer to bootstrap Koin post-first-frame.
 * This guarantees the application paints instant UI, then starts the Koin dependency graph
 * asynchronously on a background dispatcher.
 */
class KoinFrameReadyInitializer : FrameReadyInitializer<Boolean> {
    
    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> {
        return emptyList()
    }

    override fun executionThread(): ExecutionThread {
        return ExecutionThread.BACKGROUND
    }

    override suspend fun create(context: Context): Boolean {
        android.util.Log.i("DI_Integration", "Starting Koin dependency injection graph post-first-frame...")
        
        // Simulating the boostrap of Koin Container
        // startKoin {
        //     androidContext(context)
        //     modules(SampleKoinModules.appModule)
        // }
        
        // Simulate heavy DI resolution work
        kotlinx.coroutines.delay(500)
        
        android.util.Log.i("DI_Integration", "Koin started successfully and modules registered!")
        return true
    }
}

// =========================================================================
// ARCHITECTURAL SAMPLE 2: HILT INTEGRATION & ASYNC INITIALIZATION
// =========================================================================
/**
 * DOES HILT SUPPORT Kotlin 'suspend' FUNCTIONS DIRECTLY?
 * 
 * Short answer: No, Hilt do not support injecting suspended constructors or 
 * suspended values synchronously, because Android object instantiation (e.g. ViewModels,
 * Activities) is strictly synchronous. 
 *
 * Long answer: YES, Hilt can support asynchronous initialization by injecting
 * "Asynchronous Gateways". There are three main patterns to achieve this:
 *
 * ─────────────────────────────────────────────────────────────────────────
 * PATTERN A: The Suspended Lambda Provider (Recommended)
 * ─────────────────────────────────────────────────────────────────────────
 * Tell Hilt to inject a `suspend () -> T` function type. Since Kotlin compiles
 * this functional type to a standard JVM class (Function1), Hilt can inject it 
 * synchronously. The consumer can then suspend safely within its execution context.
 *
 * [Hilt Module Example]:
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object DatabaseModule {
 *     @Provides
 *     @Singleton
 *     fun provideAsyncDatabase(): suspend () -> SQLiteDatabase {
 *         return {
 *             // Await the asynchronous FrameReady initialization sequence
 *             FrameReady.await(DatabaseInitializer::class.java)
 *         }
 *     }
 * }
 * ```
 *
 * [Consumer Injection Example]:
 * ```kotlin
 * @HiltViewModel
 * class MyViewModel @Inject constructor(
 *     private val dbProvider: suspend () -> SQLiteDatabase
 * ) : ViewModel() {
 *     init {
 *         viewModelScope.launch {
 *             val db = dbProvider() // Suspends execution until the DB is ready!
 *             db.query(...)
 *         }
 *     }
 * }
 * ```
 *
 * ─────────────────────────────────────────────────────────────────────────
 * PATTERN B: The Deferred / Completable Approach
 * ─────────────────────────────────────────────────────────────────────────
 * Inject a `Deferred<T>` or a singleton Connection Holder. Hilt injects the
 * holder synchronously, and when any operation is performed, the consumer suspends
 * on the initialization.
 * 
 * ─────────────────────────────────────────────────────────────────────────
 * PATTERN C: The EntryPoint Approach
 * ─────────────────────────────────────────────────────────────────────────
 * Since FrameReadyInitializers are instantiated with empty constructors, you
 * cannot use constructor injection. Instead, retrieve dependencies from Hilt
 * using `EntryPointAccessors`.
 */

// The holder wrapper injected by Hilt synchronously (PATTERN B)
class AsyncDatabaseHolder {
    private var actualDatabaseConnection: Any? = null
    
    fun setConnection(connection: Any) {
        this.actualDatabaseConnection = connection
    }
    
    fun getDb(): Any {
        return actualDatabaseConnection ?: throw IllegalStateException("Database is not initialized yet!")
    }
}

/**
 * Hilt module registering our holder as a Singleton.
 */
// @Module
// @InstallIn(SingletonComponent::class)
object HiltDatabaseModule {
    // @Provides
    // @Singleton
    fun provideDatabaseHolder(): AsyncDatabaseHolder {
        return AsyncDatabaseHolder()
    }

    // Demonstrates Pattern A (Suspended lambda provider):
    // @Provides
    // @Singleton
    // fun provideAsyncDatabaseStub(): suspend () -> Any {
    //     return {
    //         FrameReady.await(DatabaseFrameReadyInitializer::class.java)
    //     }
    // }
}

/**
 * The FrameReady initializer that loads the database connection in the background.
 * Hilt manages the life of the holder, but FrameReady manages the scheduling of the heavy initialization.
 */
class DatabaseFrameReadyInitializer : FrameReadyInitializer<Any> {
    
    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> {
        return emptyList()
    }

    override fun executionThread(): ExecutionThread = ExecutionThread.BACKGROUND

    override suspend fun create(context: Context): Any {
        android.util.Log.i("DI_Integration", "DbFrameReady: Preparing synchronous database connection...")
        
        // Simulating heavy SQLite index operations or migrations
        HeavyInitializer.initialize(context, blockDurationMs = 1500)
        
        val databaseStubConnection = Any()
        
        // Retreive the holder from Hilt or service locator and supply the initialized connection
        // (In realistic app, you can use EntryPoints or construct directly)
        
        android.util.Log.i("DI_Integration", "DbFrameReady: Database initialized asynchronously!")
        return databaseStubConnection
    }
}


// =========================================================================
// HOW TO MAKE SURE WE WAIT UNTIL INITIALIZERS ARE COMPLETED
// =========================================================================
/**
 * A sample ViewModel or component that depends on these asynchronously initialized SDKs.
 * 
 * Using FrameReady, we suspend any coroutine scope until 'FrameReady.await()' returns.
 * This guarantees type-safe execution and zero Main Thread blocking.
 */
class SafeConsumerViewModel {
    
    // Injected via Koin/Hilt
    private val analyticsService: AnalyticsService = AnalyticsServiceImpl()
    private val dbHolder: AsyncDatabaseHolder = AsyncDatabaseHolder()

    init {
        // Enforce safe wait sequence
        CoroutineScope(Dispatchers.Main).launch {
            try {
                android.util.Log.i("SafeConsumer", "SafeConsumer initialized. Checking dependencies...")
                
                // 1. Wait until Koin graph has fully started
                FrameReady.await(KoinFrameReadyInitializer::class.java)
                
                // 2. Wait until Hilt Database Async loader is complete 
                FrameReady.await(DatabaseFrameReadyInitializer::class.java)
                
                android.util.Log.i("SafeConsumer", "All dependencies are ready and loaded! Executing business code...")
                
                // 3. Now we can safely invoke dependent methods
                analyticsService.trackEvent("AppLaunchSuccess")
                val db = dbHolder.getDb()
                
            } catch (e: Exception) {
                android.util.Log.e("SafeConsumer", "Failed to retrieve initialized dependencies", e)
            }
        }
    }
}
