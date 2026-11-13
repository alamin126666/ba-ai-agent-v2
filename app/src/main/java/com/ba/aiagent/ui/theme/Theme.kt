package com.ba.aiagent.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    secondary = BaCyan,
    tertiary = BaViolet,
    background = BgDark,
    surface = CardDark,
    surfaceVariant = SurfaceDark,
    onBackground = TextDark,
    onSurface = TextDark,
    error = ErrorRed
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    secondary = BaCyan,
    tertiary = BaViolet,
    background = BgLight,
    surface = CardLight,
    onBackground = TextLight,
    onSurface = TextLight,
    error = ErrorRed
)

@Composable
fun BaAiAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: String = "orange",
    content: @Composable () -> Unit
) {
    val accentColor = if (accent == "violet") BaViolet else ClaudeOrange
    val scheme = if (darkTheme) darkScheme(accentColor) else lightScheme(accentColor)
    MaterialTheme(
        colorScheme = scheme,
        typography = BaTypography,
        content = content
    )
}

@Composable
fun pressScale(pressed: Boolean): Float {
    return animateFloatAsState(if (pressed) 0.96f else 1f, label = "press").value
}
