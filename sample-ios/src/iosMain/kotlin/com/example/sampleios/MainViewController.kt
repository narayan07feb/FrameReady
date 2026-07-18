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
 * Unlike Android — where `FrameReadyProvider`'s manifest auto-discovery registers and triggers
 * initializers automatically — iOS has no such mechanism and cannot reflectively instantiate
 * classes, so this entry point explicitly registers a factory per initializer, installs, and
 * signals composition-ready itself (the documented iOS integration contract for FrameReady).
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    FrameReady.baselineTtffMs = 450L
    FrameReady.registerFactory(AInitializer::class) { AInitializer() }
    FrameReady.registerFactory(BInitializer::class) { BInitializer() }
    FrameReady.registerFactory(CInitializer::class) { CInitializer() }
    FrameReady.install(PlatformContext.Default, listOf(AInitializer::class, BInitializer::class, CInitializer::class))
    FrameReady.signalCompositionReady(PlatformContext.Default)

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
