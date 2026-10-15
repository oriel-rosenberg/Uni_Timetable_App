package com.orirose.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE5FF),
    onPrimaryContainer = Color(0xFF10206B),
    secondary = Color(0xFF5B6070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E6EE),
    onSecondaryContainer = Color(0xFF1B1C1F),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFAFAFB),
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFEFF0F3),
    onSurfaceVariant = Color(0xFF6B6F78),
    outline = Color(0xFFB4B8C2),
    outlineVariant = Color(0xFFE6E7EB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFF0F1F4),
    surfaceContainerHigh = Color(0xFFEBECEF),
    surfaceContainerHighest = Color(0xFFE5E6EA),
    inverseSurface = Color(0xFF2B2D31),
    inverseOnSurface = Color(0xFFF1F2F4),
    inversePrimary = Color(0xFFA9B8FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB0FF),
    onPrimary = Color(0xFF0F1E5C),
    primaryContainer = Color(0xFF2A3A86),
    onPrimaryContainer = Color(0xFFDFE5FF),
    secondary = Color(0xFFC3C6D1),
    onSecondary = Color(0xFF1B1C1F),
    secondaryContainer = Color(0xFF2E3037),
    onSecondaryContainer = Color(0xFFE4E6EE),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE6E7EA),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFE6E7EA),
    surfaceVariant = Color(0xFF1D1F23),
    onSurfaceVariant = Color(0xFF9A9EA7),
    outline = Color(0xFF5A5E67),
    outlineVariant = Color(0xFF2A2C31),
    surfaceContainerLowest = Color(0xFF0C0D0E),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF24262A),
    surfaceContainerHighest = Color(0xFF2E3035),
    inverseSurface = Color(0xFFE6E7EA),
    inverseOnSurface = Color(0xFF1B1C1F),
    inversePrimary = Color(0xFF3B5BDB),
)

/**
 * @param fontFamily the typeface for all text; the default is the phone's own, which covers Hebrew.
 */
@Composable
fun TimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography().withFont(fontFamily),
    ) {
        // The app is Hebrew-only, so it reads right-to-left whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

private fun Typography.withFont(family: FontFamily): Typography {
    if (family == FontFamily.Default) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}
