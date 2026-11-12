package com.gleaped.italianlearning.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gleaped.italianlearning.R
import com.gleaped.italianlearning.data.settings.TextSize
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

/** Atkinson Hyperlegible (SIL OFL 1.1), bundled so it works offline: designed for clear letter shapes. */
val Hyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
    // No medium/semibold cuts: map them to bold so emphasis stays visible.
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Medium),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.SemiBold),
)

/** Accent used for ==highlighted== endings and trigger words in rule cards. */
object HighlightColors {
    val light = Color(0xFFB4532A)
    val dark = Color(0xFFFFB59A)
}

/**
 * Reading-first type scale: body text at 17sp with 1.5 line height (Material's default is 16/24),
 * Italian sentences in exercises use titleLarge (21sp). [scale] comes from the text-size setting.
 */
fun readableTypography(scale: Float): Typography {
    fun style(size: Float, line: Float, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = 0.sp) = TextStyle(
        fontFamily = Hyperlegible,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        letterSpacing = spacing,
    )
    return Typography(
        displayLarge = style(52f, 60f),
        displayMedium = style(42f, 50f),
        displaySmall = style(34f, 42f),
        headlineLarge = style(30f, 38f, FontWeight.Bold),
        headlineMedium = style(27f, 35f, FontWeight.Bold),
        headlineSmall = style(23f, 31f, FontWeight.Bold),
        titleLarge = style(21f, 30f),
        titleMedium = style(18f, 26f, FontWeight.Bold),
        titleSmall = style(16f, 23f, FontWeight.Bold),
        bodyLarge = style(17f, 26f),
        bodyMedium = style(16f, 24f),
        bodySmall = style(14f, 20f),
        labelLarge = style(16f, 22f, FontWeight.Bold, 0.01.em),
        labelMedium = style(14f, 19f, FontWeight.Bold, 0.02.em),
        labelSmall = style(13f, 18f, FontWeight.Bold, 0.02.em),
    )
}

@Composable
fun ItalianoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textSize: TextSize = TextSize.MEDIUM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = readableTypography(textSize.scale),
        content = content,
    )
}
