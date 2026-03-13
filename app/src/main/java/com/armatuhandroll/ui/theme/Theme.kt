package com.armatuhandroll.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = SoftBeige,
    onPrimary = WarmBlack,
    secondary = CreamText,
    onSecondary = WarmBlack,
    background = WarmBlack,
    onBackground = CreamText,
    surface = DeepBrown,
    onSurface = CreamText,
    tertiary = AccentBrown
)

@Composable
fun ArmaTuHandrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
