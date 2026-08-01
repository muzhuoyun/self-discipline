package com.selfdiscipline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 沉稳的绿色系配色，契合修身主题 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B3A1C),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFFF9A825),
    onTertiary = Color(0xFF422B00),
    tertiaryContainer = Color(0xFFFFE082),
    onTertiaryContainer = Color(0xFF5C4300),
    background = Color(0xFFFAF7F1),
    onBackground = Color(0xFF1C1B16),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1C1B16),
    surfaceVariant = Color(0xFFEFEAE0),
    onSurfaceVariant = Color(0xFF4E4B44),
    outline = Color(0xFF7C786E),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF08310B),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00352F),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFE082),
    onTertiary = Color(0xFF422B00),
    background = Color(0xFF14140F),
    onBackground = Color(0xFFE7E2D6),
    surface = Color(0xFF14140F),
    onSurface = Color(0xFFE7E2D6),
    surfaceVariant = Color(0xFF2B2823),
    onSurfaceVariant = Color(0xFFB9B4A9),
    error = Color(0xFFEF9A9A),
)

@Composable
fun SelfDisciplineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
