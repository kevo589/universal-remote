package dev.kmedrano.remote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1B2A4A)
private val BlueLight = Color(0xFF3D5A99)
private val Accent = Color(0xFF00B8A9)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    secondary = Accent,
)

private val LightColors = lightColorScheme(
    primary = Blue,
    secondary = Accent,
)

@Composable
fun UniversalRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
