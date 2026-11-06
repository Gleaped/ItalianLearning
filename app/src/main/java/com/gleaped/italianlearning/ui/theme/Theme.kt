package com.gleaped.italianlearning.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gleaped.italianlearning.data.settings.ThemeMode

// Palette: basil green, terracotta and cream, quietly Italian.
private val Basil = Color(0xFF2E6B4F)
private val BasilLight = Color(0xFF8FD5B0)
private val Terracotta = Color(0xFFB4532A)
private val TerracottaLight = Color(0xFFFFB59A)
private val Cream = Color(0xFFFFFBF5)
private val Ink = Color(0xFF1E1B18)

private val LightColors = lightColorScheme(
    primary = Basil,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EDD5),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF380D00),
    tertiary = Color(0xFF5F5C2E),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE4DA),
    onSurfaceVariant = Color(0xFF4E453D),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = BasilLight,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF115238),
    onPrimaryContainer = Color(0xFFC4EDD5),
    secondary = TerracottaLight,
    onSecondary = Color(0xFF5B1A00),
    secondaryContainer = Color(0xFF8A3A16),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFC9C58F),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEBE1D8),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEBE1D8),
    surfaceVariant = Color(0xFF4E453D),
    onSurfaceVariant = Color(0xFFD2C4B8),
    error = Color(0xFFFFB4AB),
)

/** Colours for exercise feedback, readable in both themes. */
object FeedbackColors {
    val correctLight = Color(0xFFD7F2E1)
    val correctDark = Color(0xFF1F4D36)
    val almostLight = Color(0xFFFFF0C2)
    val almostDark = Color(0xFF574A12)
    val wrongLight = Color(0xFFFFDAD4)
    val wrongDark = Color(0xFF6B2219)
}

@Composable
fun ItalianoTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
