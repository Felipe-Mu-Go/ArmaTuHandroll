package com.armatuhandroll.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

private val appFontFamily = FontFamily.SansSerif

val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = appFontFamily),
        displayMedium = displayMedium.copy(fontFamily = appFontFamily),
        displaySmall = displaySmall.copy(fontFamily = appFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = appFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = appFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = appFontFamily),
        titleLarge = titleLarge.copy(fontFamily = appFontFamily),
        titleMedium = titleMedium.copy(fontFamily = appFontFamily),
        titleSmall = titleSmall.copy(fontFamily = appFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = appFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = appFontFamily),
        bodySmall = bodySmall.copy(fontFamily = appFontFamily),
        labelLarge = labelLarge.copy(fontFamily = appFontFamily),
        labelMedium = labelMedium.copy(fontFamily = appFontFamily),
        labelSmall = labelSmall.copy(fontFamily = appFontFamily)
    )
}
