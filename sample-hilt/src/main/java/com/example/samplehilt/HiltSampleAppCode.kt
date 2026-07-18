package com.example.samplehilt

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameready.FrameReady
import com.frameready.FrameReadyInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// Two-sided DI bridge for FrameReady
//
// Problem: FrameReady initializers are instantiated via reflection (no-arg
// constructor), so they can't receive injected constructor dependencies.
// And consuming the result in the DI graph requires custom holder classes.
//
// Solution — two complementary APIs:
//   registerFactory()  → inject DI dependencies INTO the initializer's create()
//   asDeferred()       → inject the result OUT into the DI graph as Deferred<T>
//
// Before (5 boilerplate classes):
//   AsyncStorageHolder + EncryptedSecretStorageProvider + HiltIntegrationModule
//   + InitializerEntryPoint + storageProvider.getDeferred().await()
//
// After (two one-liners):
//   FrameReady.registerFactory(...) { EncryptedStorageInitializer(keystoreManager) }
//   FrameReady.asDeferred(EncryptedStorageInitializer::class)
// ─────────────────────────────────────────────────────────────────────────────

// ─── 1. DI-managed dependency provided by Hilt ───────────────────────────────
class KeystoreManager @Inject constructor() {
    fun getMasterKey(): String = "A9X9-D3E7-L300-K92B"
}

// ─── 2. Result type returned by the initializer ──────────────────────────────
data class EncryptedStorage(val masterKey: String) {
    fun retrieveSecureData(): String = "SUCCESS — DECRYPTED WITH KEY [$masterKey]"
}

// ─── 3. Initializer ──────────────────────────────────────────────────────────
//        No-arg constructor: used by FrameReady's sort/scan phase (before Hilt).
//        Secondary constructor: receives DI deps provided by registerFactory().
class EncryptedStorageInitializer : FrameReadyInitializer<EncryptedStorage> {
    private var keystoreManager: KeystoreManager? = null

    constructor()                                                          // scan phase
    constructor(keystoreManager: KeystoreManager) { this.keystoreManager = keystoreManager }

    override fun dependencies() = emptyList<kotlin.reflect.KClass<out FrameReadyInitializer<*>>>()

    override suspend fun create(context: Context): EncryptedStorage {
        val km = checkNotNull(keystoreManager) {
            "Call FrameReady.registerFactory(EncryptedStorageInitializer::class) in Application.onCreate()"
        }
        delay(1500)
        return EncryptedStorage(km.getMasterKey())
    }
}

// ─── 4. Application: registerFactory() bridges Hilt → FrameReady ─────────────
//        super.onCreate() triggers Hilt injection of @Inject fields.
//        registerFactory() must be called before the first frame is drawn.
@HiltAndroidApp
class HiltSampleApplication : Application() {
    @Inject lateinit var keystoreManager: KeystoreManager

    override fun onCreate() {
        super.onCreate()
        // registerFactory: provide the DI-injected instance for create().
        // No EntryPoint class, no holder singleton, no custom module needed.
        FrameReady.registerFactory(EncryptedStorageInitializer::class) {
            EncryptedStorageInitializer(keystoreManager)
        }
    }
}

// ─── 5. Hilt module: asDeferred() bridges FrameReady → DI graph ──────────────
//        Deferred<EncryptedStorage> is a stable @Singleton: same instance every call.
//        Safe to call asDeferred() before install() — deferred completes post-frame.
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideStorageDeferred(): Deferred<EncryptedStorage> =
        FrameReady.asDeferred(EncryptedStorageInitializer::class)
}

// ─── 6. ViewModel: inject Deferred<EncryptedStorage>, zero FrameReady imports ─
//        Unit tests: substitute with CompletableDeferred<EncryptedStorage>()
//                    .apply { complete(fakeStorage) } — no FrameReady needed.
@HiltViewModel
class HiltSampleViewModel @Inject constructor(
    private val storageDeferred: Deferred<EncryptedStorage>
) : ViewModel() {

    private val _phase   = MutableStateFlow("Waiting for post-frame init…")
    private val _result  = MutableStateFlow<String?>(null)
    private val _done    = MutableStateFlow(false)
    val phase:  StateFlow<String>  = _phase.asStateFlow()
    val result: StateFlow<String?> = _result.asStateFlow()
    val done:   StateFlow<Boolean> = _done.asStateFlow()

    init {
        viewModelScope.launch {
            _phase.value = "Suspended — awaiting Deferred<EncryptedStorage>…"
            // Plain .await() on the injected Deferred.
            // No FrameReady.await() call — no FrameReady import in this file.
            val storage = storageDeferred.await()
            _result.value = storage.retrieveSecureData()
            _phase.value  = "Post-frame init complete"
            _done.value   = true
        }
    }
}

// ─── 7. Activity + UI ─────────────────────────────────────────────────────────
@AndroidEntryPoint
class HiltMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HiltSampleScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiltSampleScreen(viewModel: HiltSampleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val phase  by viewModel.phase.collectAsState()
    val result by viewModel.result.collectAsState()
    val done   by viewModel.done.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary    = Color(0xFFD0BCFF),
            background = Color(0xFF141218),
            surface    = Color(0xFF211F26)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FrameReady × Hilt", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = Color(0xFF1D1B20),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF141218)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── registerFactory() card ─────────────────────────────────
                PatternCard(
                    title = "1 · registerFactory() — inject INTO initializer",
                    body  = "Provides DI-managed constructor args to create(). " +
                            "Called in Application.onCreate() after Hilt injects @Inject fields.",
                    code  = "// Application.onCreate()\n" +
                            "FrameReady.registerFactory(\n" +
                            "    EncryptedStorageInitializer::class.java\n" +
                            ") { EncryptedStorageInitializer(keystoreManager) }",
                    color = Color(0xFF311111)
                )

                // ── asDeferred() card ──────────────────────────────────────
                PatternCard(
                    title = "2 · asDeferred() — inject result OUT into DI graph",
                    body  = "Returns a stable Deferred<T> that can be @Provides'd as a " +
                            "@Singleton. Consumers inject Deferred<T> — no FrameReady imports.",
                    code  = "// Hilt module\n" +
                            "@Provides @Singleton\n" +
                            "fun provideStorage(): Deferred<EncryptedStorage> =\n" +
                            "    FrameReady.asDeferred(EncryptedStorageInitializer::class)\n\n" +
                            "// ViewModel — zero FrameReady imports\n" +
                            "class HomeVM @Inject constructor(\n" +
                            "    private val storage: Deferred<EncryptedStorage>\n" +
                            ") : ViewModel() {\n" +
                            "    init { viewModelScope.launch { storage.await().use() } }\n" +
                            "}",
                    color = Color(0xFF11211E)
                )

                // ── Testability card ───────────────────────────────────────
                PatternCard(
                    title = "3 · Unit tests — no FrameReady wiring needed",
                    body  = "Substitute the injected Deferred<T> with a pre-completed " +
                            "CompletableDeferred. Tests are fully isolated from FrameReady.",
                    code  = "val fakeStorage = EncryptedStorage(\"test-key\")\n" +
                            "val fakeDeferred = CompletableDeferred(fakeStorage)\n" +
                            "val vm = HiltSampleViewModel(fakeDeferred)\n" +
                            "// vm.result is immediately available — no waiting",
                    color = Color(0xFF1A1A26)
                )

                // ── Live phase ─────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Live ViewModel state", color = Color.White.copy(0.4f), fontSize = 11.sp)
                        Text(phase, color = Color.White, fontWeight = FontWeight.Medium)
                        if (!done) LinearProgressIndicator(
                            modifier   = Modifier.fillMaxWidth(),
                            color      = Color(0xFFD0BCFF),
                            trackColor = Color(0xFF49454F)
                        )
                    }
                }

                if (done && result != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1B3A1B)),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(result!!, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternCard(title: String, body: String, code: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = color),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(body, color = Color.White.copy(0.75f), fontSize = 12.sp)
            Surface(
                color    = Color.Black.copy(0.3f),
                shape    = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    code,
                    modifier   = Modifier.padding(10.dp),
                    color      = Color(0xFFD0BCFF).copy(0.9f),
                    fontSize   = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
