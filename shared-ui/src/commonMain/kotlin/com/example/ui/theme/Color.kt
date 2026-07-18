package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────
// FrameReady demo palette — the app's dark "cosmic slate" brand identity,
// expressed as named tokens instead of scattered hex literals.
// Consumed by AppDarkColorScheme (M3 roles) and ExtraColors (semantic
// accents M3 has no built-in role for: warning, success, chart series).
// ─────────────────────────────────────────────────────────────────────────

// Surface tonal steps (darkest -> lightest), same hue family as background.
val DemoSurfaceLowest = Color(0xFF08080E)
val DemoSurface = Color(0xFF0C0C14)
val DemoSurfaceContainerLow = Color(0xFF111122)
val DemoSurfaceContainer = Color(0xFF151525)
val DemoSurfaceContainerHigh = Color(0xFF1A1D2B)
val DemoSurfaceContainerHighest = Color(0xFF23233B)
val DemoOutline = Color(0xFF2E2E4A)
val DemoOutlineVariant = Color(0xFF23233B)

val DemoOnSurface = Color(0xFFE5E7EB)
val DemoOnSurfaceVariant = Color(0xFF9CA3AF)
val DemoOnSurfaceMuted = Color(0xFF6B7281)

// Primary: light-indigo accent (KPI values, links, icons) + medium-indigo
// container (solid CTA button surfaces).
val DemoPrimary = Color(0xFF818CF8)
val DemoPrimaryContainer = Color(0xFF4F46E5)
val DemoOnPrimaryContainer = Color(0xFFFFFFFF)
val DemoPrimaryContainerDeep = Color(0xFF312E81) // benchmark trigger / progress track

// Secondary: purple accent (DI section) + its container button surface.
val DemoSecondary = Color(0xFFC084FC)
val DemoSecondaryContainer = Color(0xFF701F6E)
val DemoOnSecondaryContainer = Color(0xFFFFFFFF)

// Tertiary: teal/cyan accent for informational callouts and code highlights.
val DemoTertiary = Color(0xFF2DD4BF)
val DemoTertiaryContainer = Color(0xFF0F766E)
val DemoInfo = Color(0xFF38BDF8)
val DemoInfoSoft = Color(0xFF81D4FA)

// Error.
val DemoError = Color(0xFFF87171)
val DemoErrorStrong = Color(0xFFEF4444)
val DemoErrorContainer = Color(0xFF701A1A)
val DemoOnErrorContainer = Color(0xFFFCA5A5)

// Warning (no M3 role) — amber family.
val DemoWarning = Color(0xFFFBBF24)
val DemoWarningStrong = Color(0xFFF59E0B)
val DemoOnWarningContainer = Color(0xFFFCD34D)
val DemoWarningContainer = Color(0xFF3B1E13)
val DemoWarningOutline = Color(0xFF78350F)

// Success (no M3 role) — green family.
val DemoSuccess = Color(0xFF34D399)
val DemoSuccessContainer = Color(0xFF065F46)
val DemoSuccessStrong = Color(0xFF10B981)

// Chart series accent used only by the three-way benchmark comparison bars.
val DemoChartOrange = Color(0xFFF97316)
