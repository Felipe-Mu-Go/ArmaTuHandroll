package com.armatuhandroll.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider
import com.armatuhandroll.R

private val googleFontProvider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val poppins = GoogleFont("Poppins")

val AppFontFamily = FontFamily(
    Font(googleFont = poppins, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = poppins, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = poppins, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = poppins, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        displayMedium = displayMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = displaySmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal),
        bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal),
        bodySmall = bodySmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal),
        labelLarge = labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium)
    )
}
