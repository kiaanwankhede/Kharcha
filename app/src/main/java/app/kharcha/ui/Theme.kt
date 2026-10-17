package app.kharcha.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF13203A)
val Paper = Color(0xFFF7F5F0)
val Sand = Color(0xFFECE8DF)
val CardColor = Color(0xFFFDFCF9)
val Rust = Color(0xFFB84D1C)
val Blue = Color(0xFF2F5DA8)
val Muted = Color(0xFF4A5568)
val Faint = Color(0xFF6A7179)
val Line = Color(0xFFDDD7CB)

private val colors = lightColorScheme(
    primary = Navy,
    onPrimary = Paper,
    secondary = Rust,
    onSecondary = CardColor,
    tertiary = Blue,
    background = Paper,
    onBackground = Navy,
    surface = CardColor,
    onSurface = Navy,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    surfaceContainer = Sand,
    outline = Line,
)

@Composable
fun KharchaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
