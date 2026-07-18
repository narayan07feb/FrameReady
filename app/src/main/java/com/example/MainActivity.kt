package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.demo.MainScreen
import com.example.demo.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.frameready.FrameReady

/**
 * Thin Android host for the shared `MainScreen` composable (`:shared-ui` module) — the same
 * screen renders on iOS via `sample-ios`'s `ComposeUIViewController` entry point. Keep this file
 * free of UI code; add new screens/sections to `:shared-ui`'s commonMain instead.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FrameReady.baselineTtffMs = 450L

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
