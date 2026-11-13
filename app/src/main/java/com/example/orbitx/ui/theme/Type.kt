package com.example.orbitx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.orbitx.R

@OptIn(ExperimentalTextApi::class)
private val AstaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun asta(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = AstaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

val Typography = Typography(
    displaySmall = asta(40, 48, FontWeight.Bold),
    headlineMedium = asta(32, 40, FontWeight.Bold),
    headlineSmall = asta(26, 34, FontWeight.Bold),
    titleLarge = asta(24, 32, FontWeight.SemiBold),
    titleMedium = asta(21, 28, FontWeight.SemiBold),
    titleSmall = asta(18, 26, FontWeight.SemiBold),
    bodyLarge = asta(18, 26),
    // In-car reading distance: secondary text and eyebrows sized up from phone defaults.
    bodyMedium = asta(17, 25),
    bodySmall = asta(16, 23),
    labelLarge = asta(18, 24, FontWeight.SemiBold),
    labelMedium = asta(17, 23, FontWeight.SemiBold),
    labelSmall = asta(16, 22, FontWeight.SemiBold),
)
