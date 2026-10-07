package com.claudeusage.widget.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ClaudePurple,
    onPrimary = TextPrimary,
    primaryContainer = ClaudePurpleDark,
    secondary = ClaudePurpleLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = StatusCritical
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudePurple,
    onPrimary = Color.White,
    primaryContainer = ClaudePurpleLight,
    secondary = ClaudePurpleDark,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error = StatusCritical
)

data class ExtendedColors(
    val cardBackground: Color,
    val textMuted: Color,
    val textSecondary: Color,
    val progressTrack: Color,
    val dividerColor: Color,
    val providerClayText: Color,
    val providerNeutralBorder: Color,
    val providerNeutralText: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        cardBackground = DarkCard,
        textMuted = TextMuted,
        textSecondary = TextSecondary,
        progressTrack = ProgressTrack,
        dividerColor = DarkBackground,
        providerClayText = ProviderClayTextDark,
        providerNeutralBorder = ProviderNeutralBorderDark,
        providerNeutralText = ProviderNeutralTextDark
    )
}

private val DarkExtendedColors = ExtendedColors(
    cardBackground = DarkCard,
    textMuted = TextMuted,
    textSecondary = TextSecondary,
    progressTrack = ProgressTrack,
    dividerColor = DarkBackground,
    providerClayText = ProviderClayTextDark,
    providerNeutralBorder = ProviderNeutralBorderDark,
    providerNeutralText = ProviderNeutralTextDark
)

private val LightExtendedColors = ExtendedColors(
    cardBackground = LightCard,
    textMuted = LightTextMuted,
    textSecondary = LightTextSecondary,
    progressTrack = LightProgressTrack,
    dividerColor = LightProgressTrack,
    providerClayText = ProviderClayTextLight,
    providerNeutralBorder = ProviderNeutralBorderLight,
    providerNeutralText = ProviderNeutralTextLight
)

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

@Composable
fun ClaudeUsageTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
