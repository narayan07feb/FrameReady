package com.example.sampleios

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.example.demo.AInitializer
import com.example.demo.BInitializer
import com.example.demo.CInitializer
import com.example.demo.MainScreen
import com.example.demo.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.frameready.FrameReady
import com.frameready.PlatformContext
import platform.UIKit.UIViewController

/**
 * The SAME `MainScreen` composable Android's `MainActivity` hosts (`:shared-ui` module),
 * rendered natively on iOS via Compose Multiplatform. Called from Swift as
 * `MainViewControllerKt.MainViewController()`.
 *
 * Unlike Android — where `FrameReadyProvider`'s manifest auto-discovery registers initializers —
 * iOS has no such mechanism and cannot reflectively instantiate classes, so this entry point
 * registers a factory per initializer and calls [FrameReady.install]. First-frame is signaled
 * from shared `MainScreen` via `LaunchedEffect(Unit) { FrameReady.signalCompositionReady() }`.
 * Do not signal here: that starts `create()` before the first composition.
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    FrameReady.baselineTtffMs = 450L
    FrameReady.registerFactory(AInitializer::class) { AInitializer() }
    FrameReady.registerFactory(BInitializer::class) { BInitializer() }
    FrameReady.registerFactory(CInitializer::class) { CInitializer() }
    FrameReady.install(PlatformContext.Default, listOf(AInitializer::class, BInitializer::class, CInitializer::class))

    return ComposeUIViewController {
        val viewModel = remember { MainViewModel() }
        MyApplicationTheme {
            Scaffold { innerPadding ->
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
