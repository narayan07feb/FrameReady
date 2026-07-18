package com.example.ui.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun isReducedMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
