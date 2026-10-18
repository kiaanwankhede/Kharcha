package app.kharcha.ui

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, by role. Screens use the names below (Navy, Paper, …), which resolve to the
 * light or dark value for the current theme.
 */
data class KharchaPalette(
    val ink: Color,
    val paper: Color,
    val card: Color,
    val sand: Color,
    val rust: Color,
    val blue: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val warmTint: Color,
    val coolTint: Color,
    val chartStrong: Color,
    val chartSoft: Color,
)

private val LightPalette = KharchaPalette(
    ink = Color(0xFF13203A),
    paper = Color(0xFFF7F5F0),
    card = Color(0xFFFDFCF9),
    sand = Color(0xFFECE8DF),
    rust = Color(0xFFB84D1C),
    blue = Color(0xFF2F5DA8),
    muted = Color(0xFF4A5568),
    faint = Color(0xFF6A7179),
    line = Color(0xFFDDD7CB),
    warmTint = Color(0xFFFBE3D5),
    coolTint = Color(0xFFE1E8F5),
    chartStrong = Color(0xFF2A78D6),
    chartSoft = Color(0xFF86B6EF),
)

private val DarkPalette = KharchaPalette(
    ink = Color(0xFFEEF1F6),
    paper = Color(0xFF0E1525),
    card = Color(0xFF172036),
    sand = Color(0xFF212B43),
    rust = Color(0xFFF08A5D),
    blue = Color(0xFF7DB0F0),
    muted = Color(0xFFB4BCCB),
    faint = Color(0xFF8E98AB),
    line = Color(0xFF2E3953),
    warmTint = Color(0xFF3A2A25),
    coolTint = Color(0xFF1F2E4A),
    chartStrong = Color(0xFF3987E5),
    chartSoft = Color(0xFF1C5CAB),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

val Navy: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val Paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper
val CardColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val Sand: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sand
val Rust: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rust
val Blue: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.blue
val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
val Faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val WarmTint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warmTint
val CoolTint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.coolTint
val ChartStrong: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chartStrong
val ChartSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chartSoft

/** [theme] is "system", "light" or "dark". */
@Composable
fun KharchaTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.blue,
            onPrimary = palette.paper,
            secondary = palette.rust,
            onSecondary = palette.paper,
            tertiary = palette.blue,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.sand,
            onSurfaceVariant = palette.muted,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.sand,
            outline = palette.line,
        )
    } else {
        lightColorScheme(
            primary = palette.ink,
            onPrimary = palette.paper,
            secondary = palette.rust,
            onSecondary = palette.card,
            tertiary = palette.blue,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.card,
            onSurface = palette.ink,
            surfaceVariant = palette.sand,
            onSurfaceVariant = palette.muted,
            surfaceContainer = palette.sand,
            outline = palette.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme) {
            // Every screen (including ones opened outside the tabs) gets the palette's background and
            // text colour, so nothing is left light-on-light or dark-on-dark.
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = palette.paper,
                contentColor = palette.ink,
                content = content,
            )
        }
    }
}
