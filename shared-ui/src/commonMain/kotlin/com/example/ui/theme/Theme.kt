package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The FrameReady demo app's dark "cosmic slate" color scheme. Every screen renders in dark mode
 * by design (a startup-performance demo reads best on a near-black canvas with indigo/violet
 * accents), so this is the scheme used regardless of system theme. Shared verbatim across
 * Android and iOS via Compose Multiplatform — no per-platform color divergence.
 */
private val DemoColorScheme = darkColorScheme(
  primary = DemoPrimary,
  onPrimary = Color.White,
  primaryContainer = DemoPrimaryContainer,
  onPrimaryContainer = DemoOnPrimaryContainer,

  secondary = DemoSecondary,
  onSecondary = Color.White,
  secondaryContainer = DemoSecondaryContainer,
  onSecondaryContainer = DemoOnSecondaryContainer,

  tertiary = DemoTertiary,
  onTertiary = Color.White,
  tertiaryContainer = DemoTertiaryContainer,
  onTertiaryContainer = Color.White,

  error = DemoError,
  onError = Color.White,
  errorContainer = DemoErrorContainer,
  onErrorContainer = DemoOnErrorContainer,

  background = DemoSurface,
  onBackground = DemoOnSurface,

  surface = DemoSurface,
  onSurface = DemoOnSurface,
  onSurfaceVariant = DemoOnSurfaceVariant,

  surfaceContainerLowest = DemoSurfaceLowest,
  surfaceContainerLow = DemoSurfaceContainerLow,
  surfaceContainer = DemoSurfaceContainer,
  surfaceContainerHigh = DemoSurfaceContainerHigh,
  surfaceContainerHighest = DemoSurfaceContainerHighest,

  outline = DemoOutline,
  outlineVariant = DemoOutlineVariant,
)

/** Shape scale used across the demo screens — replaces ad-hoc `RoundedCornerShape(Ndp)` calls. */
val DemoShapes = Shapes(
  extraSmall = RoundedCornerShape(4.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Semantic colors M3's [androidx.compose.material3.ColorScheme] has no built-in role for
 * (warning, success, chart series). Extend the theme via a [staticCompositionLocalOf], not module
 * globals, so these still participate in composition/preview scoping like the rest of the theme.
 */
@Immutable
data class ExtraColors(
  val warning: Color,
  val warningStrong: Color,
  val onWarningContainer: Color,
  val warningContainer: Color,
  val warningOutline: Color,
  val success: Color,
  val successStrong: Color,
  val successContainer: Color,
  val info: Color,
  val infoSoft: Color,
  val chartOrange: Color,
  /** Deeper indigo than [androidx.compose.material3.ColorScheme.primaryContainer] — used for the
   * benchmark trigger button and active-tab affordances. */
  val primaryContainerDeep: Color,
)

private val DemoExtraColors = ExtraColors(
  warning = DemoWarning,
  warningStrong = DemoWarningStrong,
  onWarningContainer = DemoOnWarningContainer,
  warningContainer = DemoWarningContainer,
  warningOutline = DemoWarningOutline,
  success = DemoSuccess,
  successStrong = DemoSuccessStrong,
  successContainer = DemoSuccessContainer,
  info = DemoInfo,
  infoSoft = DemoInfoSoft,
  chartOrange = DemoChartOrange,
  primaryContainerDeep = DemoPrimaryContainerDeep,
)

private val LocalExtraColors = staticCompositionLocalOf { DemoExtraColors }

/** Access via `MaterialTheme.extraColors.warning`, mirroring `MaterialTheme.colorScheme`. */
val MaterialTheme.extraColors: ExtraColors
  @Composable get() = LocalExtraColors.current

/**
 * Whether the user has disabled system animations. Compose has no built-in
 * `LocalReducedMotion` — each platform answers this its own way (Android's
 * `Settings.Global.ANIMATOR_DURATION_SCALE`, iOS's `UIAccessibilityIsReduceMotionEnabled`), read
 * once per app launch via [isReducedMotionEnabled] and respected in any `AnimatedVisibility`/
 * `animate*AsState` call that isn't purely decorative.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Platform-specific reduced-motion query — see [LocalReducedMotion]. */
@Composable
expect fun isReducedMotionEnabled(): Boolean

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  // Always dark, on every platform, regardless of system light/dark setting — this demo's
  // "cosmic slate" identity and its extraColors/extraTypography tokens were only ever designed
  // against DemoColorScheme. A generic LightColorScheme has no matching tuned tokens and produces
  // broken-contrast text (confirmed: iOS simulator defaults to light mode and rendered
  // near-invisible text before this was pinned).
  val colorScheme = DemoColorScheme
  val reduceMotion = isReducedMotionEnabled()

  CompositionLocalProvider(
    LocalExtraColors provides DemoExtraColors,
    LocalExtraTypography provides DemoExtraTypography,
    LocalReducedMotion provides reduceMotion,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = DemoShapes,
      content = content,
    )
  }
}
