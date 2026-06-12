package com.example.samplehilt

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// 1. Core Hilt Application Class
@HiltAndroidApp
class HiltSampleApplication : Application()

// 2. An Async service we want to initialize after the first frame
class EncryptedSecretStorage(val masterKey: String) {
    fun retrieveSecureData(): String {
        return "SUCCESS-[DECRYPTED-WITH-KEY-$masterKey]"
    }
}


// 3. A Singleton state holder managed by Hilt to store the instantiated service
@Singleton
class AsyncStorageHolder @Inject constructor() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private val _storageFlow = MutableStateFlow<EncryptedSecretStorage?>(null)
    val storageFlow: StateFlow<EncryptedSecretStorage?> = _storageFlow.asStateFlow()

    // Invoked post-frame by our FrameReady Initializer
    fun initializeStorage(storage: EncryptedSecretStorage) {
        _storageFlow.value = storage
    }

    fun awaitStorageReadyDeferred(): kotlinx.coroutines.Deferred<EncryptedSecretStorage> {
        val deferred = kotlinx.coroutines.CompletableDeferred<EncryptedSecretStorage>()
        scope.launch {
            val res = _storageFlow.filterNotNull().first()
            deferred.complete(res)
        }
        return deferred
    }
}

// 4. An EntryPoint for Initializers because Initializers are created by Android's ContentProvider
// and can't use constructor injection directly.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface InitializerEntryPoint {
    fun storageHolder(): AsyncStorageHolder
}

// 5. FrameReadyInitializer that performs the asynchronous suspend loading
class HiltPostFrameModuleInitializer : FrameReadyInitializer<EncryptedSecretStorage> {
    
    override suspend fun create(context: Context): EncryptedSecretStorage {
        // A. Simulate reading encrypted keystore keys & doing heavy CPU key derivation
        delay(1500)
        
        val decryptedKey = "A9X9-D3E7-L300-K92B"
        val storage = EncryptedSecretStorage(decryptedKey)

        // B. Inject into the Hilt-managed singleton holder using our EntryPoint
        val appContext = context.applicationContext
        val entryPoint = EntryPoints.get(appContext, InitializerEntryPoint::class.java)
        entryPoint.storageHolder().initializeStorage(storage)

        return storage
    }

    override fun dependencies(): List<Class<out FrameReadyInitializer<*>>> = emptyList()
}

// 5.1 Custom provider interface to avoid issues with raw Kotlin function types with continuation in KSP / Hilt
interface EncryptedSecretStorageProvider {
    fun getDeferred(): kotlinx.coroutines.Deferred<EncryptedSecretStorage>
}

// 6. Provide lazy or suspended providers in custom modules
@Module
@InstallIn(SingletonComponent::class)
object HiltIntegrationModule {

    // Pattern A (Deferred provider interface): Allow injecting 'EncryptedSecretStorageProvider' safely!
    @Provides
    @Singleton
    fun provideEncryptedSecretStorageProvider(
        holder: AsyncStorageHolder
    ): EncryptedSecretStorageProvider {
        return object : EncryptedSecretStorageProvider {
            override fun getDeferred(): kotlinx.coroutines.Deferred<EncryptedSecretStorage> {
                return holder.awaitStorageReadyDeferred()
            }
        }
    }
}

// 7. Inject dependencies safely in ViewModels!
@HiltViewModel
class HiltSampleViewModel @Inject constructor(
    private val storageHolder: AsyncStorageHolder,
    private val storageProvider: EncryptedSecretStorageProvider // Injected using Pattern A
) : ViewModel() {

    private val _statusText = MutableStateFlow("ViewModel initialized. Waiting for post-frame initialization...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _decryptedData = MutableStateFlow("")
    val decryptedData: StateFlow<String> = _decryptedData.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    init {
        // Enforce asynchronous waiting that doesn't block UI thread
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            // Simulating incremental loading phases
            delay(300)
            _statusText.value = "Phase 1: Booting system context (Hilt ready)..."
            _progress.value = 0.2f
            
            delay(400)
            _statusText.value = "Phase 2: Awaiting FrameReady post-draw callback..."
            _progress.value = 0.4f
            
            // Wait for FrameReady.await() OR use the suspended provider injected by Hilt!
            _statusText.value = "Phase 3: Suspended waiting on Post-Frame initializers..."
            _progress.value = 0.6f
            
            val storage = storageProvider.getDeferred().await() // Awaits the post-frame initializer safely!
            val duration = System.currentTimeMillis() - startTime
            
            _progress.value = 1.0f
            _statusText.value = "System Fully Initialized (Complete inside: ${duration}ms)"
            _decryptedData.value = storage.retrieveSecureData()
            _isCompleted.value = true
        }
    }
}

// 8. Launcher Activity
@AndroidEntryPoint
class HiltMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HiltSampleScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiltSampleScreen(viewModel: HiltSampleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val statusText by viewModel.statusText.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val decryptedData by viewModel.decryptedData.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFD0BCFF),
            background = Color(0xFF141218),
            surface = Color(0xFF211F26)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FrameReady: Hilt Integration", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1D1B20),
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Informative header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF311111)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Hilt info",
                            tint = Color(0xFFFFB4AB),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Hilt & Suspend Co-existence",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD9D3),
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Because constructor injection is synchronous, Hilt cannot natively inject suspended initializers. Instead, we inject a suspended provider 'suspend () -> T' that halts dependent coroutines without locking.",
                                color = Color(0xFFFFD9D3).copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress state
                Text(
                    text = "ViewModel State Flow",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFD0BCFF),
                            trackColor = Color(0xFF49454F)
                        )
                    }
                }

                // Initialized Data Card
                if (isCompleted) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                tint = Color(0xFF81C784),
                                contentDescription = "Active",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Decrypted Credentials Loaded:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = decryptedData,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                // Core implementation overview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Design Patterns Showcase",
                            color = Color(0xFFD0BCFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "1. Hilt injects custom 'EncryptedSecretStorageProvider' safely\n2. FrameReadyInitializer handles post-draw loading\n3. EntryPoint bridges the ContentProvider layer\n4. AsyncStorageHolder stores state safely as a StateFlow\n5. UI and ViewModel remain ultra-responsive during loading",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
